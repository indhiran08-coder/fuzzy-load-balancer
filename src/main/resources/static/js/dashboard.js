/**
 * dashboard.js — Fuzzy Load Balancer Dashboard Logic
 *
 * Handles:
 *   - JWT token management (localStorage)
 *   - API calls to the Spring Boot backend
 *   - Real-time server metric updates (polling every 5s)
 *   - Fuzzy score visualization
 *   - Simulation controls
 *   - Toast notifications
 */

// =============================================================================
// CONFIG
// =============================================================================

const API_BASE = '';          // Same origin — Spring Boot serves both
const POLL_INTERVAL_MS = 5000; // Refresh server cards every 5 seconds
let pollingTimer = null;
let jwtToken = localStorage.getItem('jwt_token') || null;

// =============================================================================
// STARTUP
// =============================================================================

document.addEventListener('DOMContentLoaded', () => {
    loadDashboard();
    startPolling();
    updateSimStatus();
});

/**
 * loadDashboard() — Loads all dashboard panels on page load.
 */
async function loadDashboard() {
    await Promise.all([
        loadKpis(),
        loadServers(),
        loadDistribution()
    ]);
}

/**
 * startPolling() — Refreshes server cards and KPIs automatically.
 * Uses setInterval so the data stays fresh even if simulation is running.
 */
function startPolling() {
    if (pollingTimer) clearInterval(pollingTimer);
    pollingTimer = setInterval(async () => {
        await Promise.all([loadKpis(), loadServers(), loadDistribution()]);
        updateSimStatus();
    }, POLL_INTERVAL_MS);
}

// =============================================================================
// KPI CARDS
// =============================================================================

async function loadKpis() {
    const data = await apiFetch('/api/dashboard/summary');
    if (!data?.data) return;

    const d = data.data;
    animateValue('kpi-total',         d.totalRequests || 0);
    animateValue('kpi-score',         (d.averageFuzzyScore || 0).toFixed(1));
    document.getElementById('kpi-rt').textContent =
        d.averageResponseTimeMs ? `${d.averageResponseTimeMs.toFixed(0)}ms` : '—';
    document.getElementById('kpi-healthy-count').textContent =
        `${d.healthyServers || 0}/${d.totalServers || 0}`;
}

/**
 * animateValue() — Smooth counter animation for KPI numbers.
 */
function animateValue(elementId, target) {
    const el = document.getElementById(elementId);
    if (!el) return;
    const start = parseInt(el.textContent) || 0;
    const targetNum = parseFloat(target);
    if (isNaN(targetNum)) { el.textContent = target; return; }

    const duration = 600;
    const startTime = performance.now();
    const isDecimal = String(target).includes('.');

    function update(now) {
        const elapsed = now - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const eased = 1 - Math.pow(1 - progress, 3); // ease-out-cubic
        const current = start + (targetNum - start) * eased;
        el.textContent = isDecimal ? current.toFixed(1) : Math.round(current);
        if (progress < 1) requestAnimationFrame(update);
    }
    requestAnimationFrame(update);
}

// =============================================================================
// SERVER CARDS
// =============================================================================

async function loadServers() {
    const data = await apiFetch('/api/servers?size=20');
    const grid = document.getElementById('server-grid');
    if (!data?.data?.content) {
        grid.innerHTML = '<p class="empty-state">No servers registered yet.</p>';
        return;
    }

    const servers = data.data.content;
    grid.innerHTML = servers.map(renderServerCard).join('');
}

function renderServerCard(server) {
    const healthClass = server.healthStatus?.toLowerCase() || 'offline';
    const cpuClass = server.cpuUsage > 80 ? 'high' : '';
    const ramClass = server.ramUsage > 80 ? 'high' : '';

    return `
    <div class="server-card ${healthClass}">
        <div class="server-header">
            <div>
                <div class="server-name">${escHtml(server.name)}</div>
                <div class="server-address">${escHtml(server.address)}:${server.port}</div>
            </div>
            <span class="health-badge ${server.healthStatus}">${server.healthStatus}</span>
        </div>
        <div class="metrics">
            <div class="metric-row">
                <div class="metric-header">
                    <span>CPU Usage</span>
                    <span>${server.cpuUsage?.toFixed(1) || 0}%</span>
                </div>
                <div class="metric-bar">
                    <div class="metric-fill cpu ${cpuClass}" style="width:${Math.min(server.cpuUsage || 0, 100)}%"></div>
                </div>
            </div>
            <div class="metric-row">
                <div class="metric-header">
                    <span>RAM Usage</span>
                    <span>${server.ramUsage?.toFixed(1) || 0}%</span>
                </div>
                <div class="metric-bar">
                    <div class="metric-fill ram ${ramClass}" style="width:${Math.min(server.ramUsage || 0, 100)}%"></div>
                </div>
            </div>
        </div>
        <div class="server-stats">
            <div class="stat">
                <span class="stat-label">Requests</span>
                <span class="stat-value">${server.activeRequests || 0}</span>
            </div>
            <div class="stat">
                <span class="stat-label">Resp Time</span>
                <span class="stat-value">${server.responseTime?.toFixed(0) || 0}ms</span>
            </div>
            <div class="stat">
                <span class="stat-label">Served</span>
                <span class="stat-value">${server.totalRequestsServed || 0}</span>
            </div>
        </div>
    </div>`;
}

async function refreshServers() { await loadServers(); showToast('Servers refreshed', 'info'); }

// =============================================================================
// LOAD DISTRIBUTION
// =============================================================================

async function loadDistribution() {
    const data = await apiFetch('/api/dashboard/distribution');
    const panel = document.getElementById('distribution-panel');
    if (!data?.data || Object.keys(data.data).length === 0) {
        panel.innerHTML = '<p class="empty-state">No routing decisions yet. Route a request first.</p>';
        return;
    }

    const dist = data.data;
    const colors = ['#6c63ff', '#38f9d7', '#f5576c', '#43e97b', '#fa8231'];
    let i = 0;
    panel.innerHTML = Object.entries(dist).map(([name, info]) => `
        <div class="dist-row">
            <div class="dist-header">
                <span>${escHtml(name)}</span>
                <span>${info.percentage || 0}% (${info.count || 0} reqs)</span>
            </div>
            <div class="dist-bar">
                <div class="dist-fill" style="width:${info.percentage || 0}%; background:${colors[i++ % colors.length]}"></div>
            </div>
        </div>
    `).join('');
}

// =============================================================================
// FUZZY EVALUATION
// =============================================================================

async function evaluateServers() {
    const panel = document.getElementById('fuzzy-scores');
    panel.innerHTML = '<div class="loading-spinner">Running fuzzy engine...</div>';

    const data = await apiFetch('/api/loadbalancer/evaluate');
    if (!data?.data || data.data.length === 0) {
        panel.innerHTML = '<p class="empty-state">No available servers to evaluate.</p>';
        return;
    }

    panel.innerHTML = data.data.map((s, idx) => `
        <div class="fuzzy-row ${s.selected ? 'winner' : ''}">
            <span class="fuzzy-server-name">${escHtml(s.serverName)}</span>
            <div class="fuzzy-bar-wrap">
                <div class="fuzzy-bar">
                    <div class="fuzzy-fill" style="width:${s.fuzzyScore || 0}%"></div>
                </div>
            </div>
            <span class="fuzzy-score">${(s.fuzzyScore || 0).toFixed(1)}</span>
            <span class="fuzzy-label label-${s.priorityLabel}">${s.priorityLabel}</span>
        </div>
    `).join('');

    showToast('Fuzzy evaluation complete', 'success');
}

// =============================================================================
// ROUTE A REQUEST
// =============================================================================

async function routeRequest() {
    const btn = document.getElementById('route-btn');
    btn.textContent = '⏳ Routing...';
    btn.disabled = true;

    const data = await apiFetch('/api/loadbalancer/route?path=/dashboard/test-request', { method: 'POST' });

    btn.textContent = '⚡ Route Request';
    btn.disabled = false;

    if (!data?.data) { showToast('Routing failed', 'error'); return; }

    const r = data.data;
    const section = document.getElementById('last-decision-section');
    const panel = document.getElementById('last-decision');
    section.style.display = 'block';

    panel.innerHTML = `
        <div class="decision-winner">
            <span class="winner-icon">🏆</span>
            <div>
                <div class="winner-name">${escHtml(r.selectedServer?.name || 'Unknown')}</div>
                <div class="winner-score">Fuzzy Score: ${r.winningScore?.toFixed(2)} — ${r.priorityLabel}</div>
            </div>
        </div>
        <div class="decision-meta">
            <div class="meta-item">
                <span class="meta-label">Algorithm</span>
                <span class="meta-value">${r.algorithm}</span>
            </div>
            <div class="meta-item">
                <span class="meta-label">Servers Evaluated</span>
                <span class="meta-value">${r.serversEvaluated}</span>
            </div>
            <div class="meta-item">
                <span class="meta-label">Decision Time</span>
                <span class="meta-value">${r.decisionTimeMs}ms</span>
            </div>
        </div>
        <div style="margin-top:16px;font-size:12px;color:var(--text-muted)">
            All scores: ${(r.allEvaluations || []).map(e =>
                `${e.serverName}: ${e.fuzzyScore?.toFixed(2)}`).join(' | ')}
        </div>
    `;

    showToast(`Routed to ${r.selectedServer?.name} (score: ${r.winningScore?.toFixed(1)})`, 'success');
    await loadKpis();
    await loadDistribution();
}

// =============================================================================
// SIMULATION CONTROLS
// =============================================================================

async function startSim() {
    const data = await apiFetch('/api/simulation/start', { method: 'POST' });
    showMessage(data?.data || 'Simulation started');
    updateSimStatus();
    showToast('Simulation started', 'success');
}

async function stopSim() {
    const data = await apiFetch('/api/simulation/stop', { method: 'POST' });
    showMessage(data?.data || 'Simulation stopped');
    updateSimStatus();
    showToast('Simulation stopped', 'info');
}

async function triggerTick() {
    const data = await apiFetch('/api/simulation/trigger', { method: 'POST' });
    showMessage(data?.data || 'Manual tick triggered');
    showToast('Manual tick executed', 'info');
    await loadServers();
}

async function triggerStress() {
    const data = await apiFetch('/api/simulation/stress', { method: 'POST' });
    showMessage(data?.data || 'Stress test applied');
    showToast('Stress test applied! Watch fuzzy scores change.', 'info');
    await loadServers();
    await evaluateServers();
}

async function resetMetrics() {
    const data = await apiFetch('/api/simulation/reset', { method: 'POST' });
    showMessage(data?.data || 'Metrics reset');
    showToast('Metrics reset to baseline', 'success');
    await loadServers();
}

async function updateSimStatus() {
    const data = await apiFetch('/api/simulation/status');
    const dot  = document.getElementById('sim-dot');
    const text = document.getElementById('sim-status-text');
    if (!data?.data) return;

    const running = data.data.running;
    dot.className = running ? 'status-dot' : 'status-dot inactive';
    text.textContent = running ? 'Simulation Running' : 'Simulation Stopped';
}

function showMessage(msg) {
    const el = document.getElementById('sim-message');
    el.textContent = msg;
    el.style.display = 'block';
    setTimeout(() => { el.style.display = 'none'; }, 5000);
}

// =============================================================================
// API UTILITY
// =============================================================================

/**
 * apiFetch() — Wrapper around fetch() that:
 *   1. Adds the JWT Authorization header if a token exists
 *   2. Returns parsed JSON or null on error
 *   3. Handles 401 by clearing token and prompting re-login
 */
async function apiFetch(url, options = {}) {
    try {
        const headers = { 'Content-Type': 'application/json', ...options.headers };
        if (jwtToken) headers['Authorization'] = `Bearer ${jwtToken}`;

        const response = await fetch(API_BASE + url, { ...options, headers });

        if (response.status === 401 || response.status === 403) {
            // Token expired — for demo purposes, get a fresh token
            await autoLogin();
            return apiFetch(url, options); // Retry once
        }

        if (!response.ok) {
            console.warn(`API error ${response.status} for ${url}`);
            return null;
        }

        return await response.json();
    } catch (err) {
        console.error('API fetch error:', err);
        return null;
    }
}

/**
 * autoLogin() — Silently logs in with default credentials for dashboard access.
 * In production, redirect to a proper login page instead.
 */
async function autoLogin() {
    try {
        const resp = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ usernameOrEmail: 'admin', password: 'admin123' })
        });
        if (resp.ok) {
            const data = await resp.json();
            jwtToken = data.data?.accessToken;
            if (jwtToken) localStorage.setItem('jwt_token', jwtToken);
        }
    } catch (e) {
        console.error('Auto-login failed:', e);
    }
}

// =============================================================================
// TOAST NOTIFICATIONS
// =============================================================================

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(20px)';
        toast.style.transition = 'all 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

// =============================================================================
// UTILITIES
// =============================================================================

/** escHtml() — Prevents XSS by escaping HTML special characters. */
function escHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

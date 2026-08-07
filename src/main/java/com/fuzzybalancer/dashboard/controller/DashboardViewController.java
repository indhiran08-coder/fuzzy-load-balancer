package com.fuzzybalancer.dashboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * DashboardViewController — Serves Thymeleaf HTML pages.
 *
 * Note: This is a @Controller (not @RestController).
 * It returns VIEW NAMES (strings that resolve to template files)
 * rather than JSON responses.
 *
 * View resolution:
 *   "index"      → src/main/resources/templates/index.html
 *   "servers"    → src/main/resources/templates/servers.html
 *   "monitoring" → src/main/resources/templates/monitoring.html
 *   "simulation" → src/main/resources/templates/simulation.html
 *
 * The JS in index.html calls the REST API controllers (/api/...)
 * to fetch data dynamically. Thymeleaf is used only for page rendering,
 * not server-side data binding (keeping it simple for the dashboard).
 */
@Controller
public class DashboardViewController {

    /** Main dashboard page — all data loaded via JS/AJAX */
    @GetMapping("/")
    public String dashboard() {
        return "index";
    }

    /** Server management page */
    @GetMapping("/servers")
    public String servers() {
        return "index"; // Reuse index for SPA-like behavior
    }

    /** Monitoring/logs page */
    @GetMapping("/monitoring")
    public String monitoring() {
        return "index";
    }

    /** Simulation control page */
    @GetMapping("/simulation")
    public String simulation() {
        return "index";
    }
}

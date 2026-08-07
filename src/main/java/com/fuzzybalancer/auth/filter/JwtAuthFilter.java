package com.fuzzybalancer.auth.filter;

import com.fuzzybalancer.auth.service.JwtService;
import com.fuzzybalancer.auth.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthFilter — Spring Security filter that intercepts every HTTP request
 * to validate JWT tokens.
 *
 * Extends OncePerRequestFilter — guarantees this filter runs exactly once
 * per request, even in forward/include scenarios.
 *
 * Filter Chain Position:
 *   Request → [JwtAuthFilter] → [UsernamePasswordAuthenticationFilter] → Controller
 *
 * JWT Validation Flow (per request):
 *   1. Extract Authorization header
 *   2. Parse "Bearer <token>" — if missing or invalid format → skip (pass to next filter)
 *   3. Extract username from JWT
 *   4. If username valid and SecurityContext is empty → load UserDetails from DB
 *   5. Validate token (signature + expiry + username match)
 *   6. Set Authentication in SecurityContextHolder
 *   7. Pass to next filter → Controller receives authenticated request
 *
 * Why SecurityContextHolder?
 *   It stores the Authentication object for the current thread.
 *   Spring Security reads it in @PreAuthorize checks and in controllers
 *   via @AuthenticationPrincipal.
 *
 * Why check SecurityContextHolder == null before loading user?
 *   Prevents re-authentication on every filter pass if another filter
 *   already set the context (e.g., if multiple JWT filters were chained).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * doFilterInternal() — Core filter logic executed for every HTTP request.
     *
     * @param request     The incoming HTTP request
     * @param response    The HTTP response (modified only on auth failure)
     * @param filterChain The remaining filter chain
     */
    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Step 1: Extract the Authorization header
        final String authHeader = request.getHeader("Authorization");

        // Step 2: Check if header exists and starts with "Bearer "
        // If not, pass to next filter without setting authentication.
        // Spring Security will then enforce its own rules (deny if secured).
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 3: Extract token (remove "Bearer " prefix — 7 characters)
        final String jwt = authHeader.substring(7);
        String username = null;

        try {
            // Step 4: Extract username from token (also validates signature)
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            // Token is malformed, expired, or tampered — log and continue
            // without setting authentication. Spring Security will deny access.
            log.warn("JWT token parsing failed: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // Step 5: If we have a username and no authentication is set yet
        // (SecurityContextHolder is null = this request has not been authenticated yet)
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Load the full UserDetails (with roles) from the database
            // This is necessary to rebuild the SecurityContext with authorities
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Step 6: Validate the token against the loaded UserDetails
            // Checks: username match + not expired
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // Step 7: Create an authentication token
                // null credentials — we use JWT, not username/password here
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()  // Roles become GrantedAuthorities
                    );

                // Attach request details (IP address, session ID) to the auth token
                authToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Set the authentication in the SecurityContext for this request's thread
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("JWT authenticated user: {} for path: {}",
                    username, request.getRequestURI());
            }
        }

        // Continue the filter chain — pass to the next filter/controller
        filterChain.doFilter(request, response);
    }
}

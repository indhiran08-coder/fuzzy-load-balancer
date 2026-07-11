package com.fuzzybalancer.config;

import com.fuzzybalancer.auth.filter.JwtAuthFilter;
import com.fuzzybalancer.auth.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig — Master Spring Security configuration class.
 *
 * This class defines:
 *   1. SecurityFilterChain — which URLs are public vs. secured
 *   2. SessionCreationPolicy.STATELESS — no HttpSession (pure JWT)
 *   3. AuthenticationProvider — wires UserDetailsService + PasswordEncoder
 *   4. PasswordEncoder — BCrypt with strength 12
 *   5. AuthenticationManager — exposed as a Bean for AuthService
 *   6. CORS configuration — allows React frontend to call APIs
 *
 * @Configuration — Marks as a Spring configuration class (bean source)
 * @EnableWebSecurity — Activates Spring Security's web security support
 * @EnableMethodSecurity — Enables @PreAuthorize and @PostAuthorize on methods
 *   Example: @PreAuthorize("hasRole('ADMIN')") on controller methods
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * securityFilterChain() — Defines the HTTP security rules.
     *
     * Key decisions:
     *
     * 1. CSRF disabled — CSRF protection is for cookie-based sessions.
     *    Since we use stateless JWT (no cookies, no sessions), CSRF is
     *    irrelevant and would break Postman/API clients.
     *
     * 2. SessionCreationPolicy.STATELESS — Spring Security will not create
     *    or use HTTP sessions. Every request must carry a valid JWT.
     *    This makes the app horizontally scalable (no session affinity needed).
     *
     * 3. Public endpoints:
     *    - /api/auth/** — Registration and login (no token needed)
     *    - /swagger-ui/** — API docs (secured in production)
     *    - /api-docs/**  — OpenAPI JSON spec
     *    - /actuator/health — Health check (for Docker/k8s probes)
     *    - / , /login, /register — Thymeleaf dashboard pages
     *    - /css/**, /js/** — Static assets
     *
     * 4. JwtAuthFilter is inserted BEFORE UsernamePasswordAuthenticationFilter.
     *    This means JWT validation happens before Spring's default form-based auth.
     *
     * @param http HttpSecurity builder
     * @return Configured SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (stateless API with JWT)
            .csrf(AbstractHttpConfigurer::disable)

            // Configure CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no JWT required
                .requestMatchers(
                    "/api/auth/**",          // Login and register
                    "/swagger-ui/**",        // Swagger UI
                    "/swagger-ui.html",
                    "/api-docs/**",          // OpenAPI spec
                    "/actuator/health",      // Health check
                    "/actuator/info",
                    "/",                     // Dashboard home
                    "/login",               // Thymeleaf login page
                    "/register",            // Thymeleaf register page
                    "/css/**",              // Static CSS
                    "/js/**",               // Static JS
                    "/images/**",           // Static images
                    "/webjars/**"           // WebJars
                ).permitAll()

                // Admin-only endpoints
                .requestMatchers(
                    "/api/servers/*/delete",
                    "/api/admin/**"
                ).hasRole("ADMIN")

                // All other endpoints require authentication (any role)
                .anyRequest().authenticated()
            )

            // Stateless session — no HttpSession created or used
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Wire our custom AuthenticationProvider
            .authenticationProvider(authenticationProvider())

            // Insert our JWT filter before Spring's default auth filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * authenticationProvider() — Wires UserDetailsService + PasswordEncoder.
     *
     * DaoAuthenticationProvider is Spring Security's standard implementation
     * that loads users from a data source (DAO = Data Access Object).
     *
     * How it works during login:
     *   1. AuthenticationManager receives UsernamePasswordAuthenticationToken
     *   2. DaoAuthenticationProvider calls userDetailsService.loadUserByUsername()
     *   3. Calls passwordEncoder.matches(rawPassword, storedHash) → BCrypt compare
     *   4. Returns authenticated token on success
     *
     * @return Configured DaoAuthenticationProvider
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * passwordEncoder() — BCrypt password hashing with strength 12.
     *
     * BCrypt automatically generates a random salt for each password.
     * Strength 12 means 2^12 = 4096 rounds of hashing.
     * Higher strength = slower (harder to brute-force), but more CPU.
     * Strength 12 is a good balance for production in 2024.
     *
     * PasswordEncoder.encode() — one-way hash, never reversible
     * PasswordEncoder.matches() — compare raw + hash
     *
     * @return BCryptPasswordEncoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * authenticationManager() — Exposed as a Bean so AuthService can inject it.
     *
     * AuthenticationConfiguration auto-configures the manager from
     * our authenticationProvider() bean.
     *
     * @param config Spring's AuthenticationConfiguration
     * @return AuthenticationManager bean
     */
    @Bean
    public AuthenticationManager authenticationManager(
        AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * corsConfigurationSource() — Configures Cross-Origin Resource Sharing.
     *
     * Allows the React dashboard (running on http://localhost:3000) to call
     * our API (running on http://localhost:8080) without CORS errors.
     *
     * In production, replace "*" with your actual frontend domain.
     *
     * @return CORS configuration for all API paths
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow all origins in development (restrict in production)
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Allow Authorization header (needed for JWT)
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

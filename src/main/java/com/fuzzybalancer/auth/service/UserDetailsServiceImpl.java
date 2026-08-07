package com.fuzzybalancer.auth.service;

import com.fuzzybalancer.auth.entity.User;
import com.fuzzybalancer.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserDetailsServiceImpl — Spring Security integration point.
 *
 * Spring Security's authentication flow:
 *   1. User sends username + password to /api/auth/login
 *   2. AuthenticationManager calls UserDetailsService.loadUserByUsername()
 *   3. This method fetches the User from DB
 *   4. Spring Security compares the provided password (via BCrypt) with
 *      the stored hash
 *   5. On match → generates JWT; on failure → throws AuthenticationException
 *
 * Also used during JWT filter validation:
 *   - JwtAuthFilter extracts username from token
 *   - Calls loadUserByUsername() to rebuild SecurityContext
 *   - Verifies token is still valid for this user
 *
 * @RequiredArgsConstructor — Lombok: generates constructor for all final fields.
 *   This is the recommended way to inject dependencies in Spring (constructor
 *   injection over field injection — better for testing and immutability).
 *
 * @Transactional(readOnly = true) — Marks the method as read-only transaction.
 *   - Prevents accidental writes
 *   - Allows connection pool optimizations (read replicas, etc.)
 *   - IMPORTANT: needed because roles are LAZY by default on User entity
 *     (we need an open session to load them)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * loadUserByUsername() — The single method of UserDetailsService.
     *
     * Despite the name, we also support lookup by email:
     *   - Tries username first
     *   - Falls back to email lookup if no username match found
     *
     * @param usernameOrEmail Username or email from the login request
     * @return UserDetails (our User entity implements this)
     * @throws UsernameNotFoundException If no matching user found
     *
     * Note: UsernameNotFoundException extends AuthenticationException.
     * Spring Security catches it and returns 401 Unauthorized.
     * We intentionally use a GENERIC message to prevent user enumeration
     * attacks ("User not found" tells attackers which users exist).
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail)
        throws UsernameNotFoundException {

        log.debug("Loading user by username or email: {}", usernameOrEmail);

        // Try username first, then email
        User user = userRepository.findByUsernameWithRoles(usernameOrEmail)
            .or(() -> userRepository.findByEmail(usernameOrEmail))
            .orElseThrow(() -> {
                log.warn("Authentication attempt for non-existent user: {}", usernameOrEmail);
                // Generic message to prevent user enumeration
                return new UsernameNotFoundException("Invalid credentials");
            });

        log.debug("User found: {} with roles: {}", user.getUsername(), user.getRoles());
        return user;
    }
}

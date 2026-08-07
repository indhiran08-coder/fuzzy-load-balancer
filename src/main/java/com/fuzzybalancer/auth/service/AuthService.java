package com.fuzzybalancer.auth.service;

import com.fuzzybalancer.auth.dto.AuthResponse;
import com.fuzzybalancer.auth.dto.LoginRequest;
import com.fuzzybalancer.auth.dto.RegisterRequest;
import com.fuzzybalancer.auth.entity.Role;
import com.fuzzybalancer.auth.entity.User;
import com.fuzzybalancer.auth.repository.RoleRepository;
import com.fuzzybalancer.auth.repository.UserRepository;
import com.fuzzybalancer.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AuthService — Business logic for user registration and authentication.
 *
 * Handles:
 *   1. User registration with duplicate detection and password hashing
 *   2. User login via Spring Security's AuthenticationManager
 *   3. JWT generation via JwtService
 *   4. AuthResponse DTO assembly
 *
 * Transaction management:
 *   @Transactional ensures all DB operations within a method succeed or
 *   fail together (atomicity). If an exception is thrown after saving
 *   the user but before assigning roles, the entire operation rolls back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    /**
     * register() — Creates a new user account.
     *
     * Flow:
     *   1. Validate that username and email are not already taken
     *   2. Validate password confirmation match
     *   3. Hash the password with BCrypt
     *   4. Assign ROLE_USER by default
     *   5. Save user to database
     *   6. Generate JWT for immediate login after registration
     *   7. Return AuthResponse
     *
     * @param request Registration data from the API
     * @return AuthResponse with JWT token
     * @throws ApiException If username/email already exists or passwords don't match
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for username: {}", request.getUsername());

        // Step 1: Check for duplicate username
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ApiException(
                "Username '" + request.getUsername() + "' is already taken",
                HttpStatus.CONFLICT
            );
        }

        // Step 2: Check for duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(
                "Email '" + request.getEmail() + "' is already registered",
                HttpStatus.CONFLICT
            );
        }

        // Step 3: Validate password confirmation
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new ApiException("Passwords do not match", HttpStatus.BAD_REQUEST);
        }

        // Step 4: Fetch the default ROLE_USER
        Role userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
            .orElseThrow(() -> new ApiException(
                "System role not found. Please contact administrator.",
                HttpStatus.INTERNAL_SERVER_ERROR
            ));

        // Step 5: Build and save the User entity
        // passwordEncoder.encode() uses BCrypt with a random salt.
        // The hash is one-way — we can never recover the original password.
        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .roles(new HashSet<>(Set.of(userRole)))
            .enabled(true)
            .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getUsername());

        // Step 6: Generate JWT for the newly registered user
        String token = jwtService.generateToken(savedUser);

        // Step 7: Build and return the response
        return buildAuthResponse(savedUser, token, "Registration successful! Welcome aboard.");
    }

    // =========================================================================
    // LOGIN
    // =========================================================================

    /**
     * login() — Authenticates an existing user.
     *
     * Flow:
     *   1. Call AuthenticationManager.authenticate()
     *      → Spring Security internally calls UserDetailsService.loadUserByUsername()
     *      → Verifies password using PasswordEncoder.matches()
     *      → Returns Authentication object on success
     *      → Throws AuthenticationException on failure
     *   2. Extract the authenticated User from the Authentication object
     *   3. Generate JWT
     *   4. Return AuthResponse
     *
     * Why use AuthenticationManager here instead of doing it manually?
     *   AuthenticationManager is Spring Security's standardized authentication
     *   pipeline. It handles account locking, disabling, and expiry checks
     *   automatically based on UserDetails flags.
     *
     * @param request Login credentials from the API
     * @return AuthResponse with JWT token
     * @throws org.springframework.security.core.AuthenticationException If invalid credentials
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for: {}", request.getUsernameOrEmail());

        // authenticate() throws AuthenticationException if credentials are wrong.
        // Spring Security will translate this into a 401 response via our
        // global exception handler.
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getUsernameOrEmail(),
                request.getPassword()
            )
        );

        // getPrincipal() returns the UserDetails object returned by
        // UserDetailsServiceImpl.loadUserByUsername()
        User user = (User) authentication.getPrincipal();
        log.info("User logged in successfully: {}", user.getUsername());

        String token = jwtService.generateToken(user);
        return buildAuthResponse(user, token, "Login successful! Welcome back.");
    }

    // =========================================================================
    // HELPER
    // =========================================================================

    /**
     * buildAuthResponse() — Assembles the AuthResponse DTO from user + token.
     *
     * Extracts role names from the user's authorities and formats them as
     * a Set<String> for easy client-side consumption.
     *
     * @param user    The authenticated/registered User entity
     * @param token   The generated JWT token string
     * @param message Human-readable message for the response
     * @return Complete AuthResponse DTO
     */
    private AuthResponse buildAuthResponse(User user, String token, String message) {
        Set<String> roles = user.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        return AuthResponse.builder()
            .accessToken(token)
            .tokenType("Bearer")
            .expiresIn(jwtService.getExpirationMs())
            .userId(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .roles(roles)
            .authenticatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .message(message)
            .build();
    }
}

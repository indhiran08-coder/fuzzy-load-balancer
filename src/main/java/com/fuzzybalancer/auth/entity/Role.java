package com.fuzzybalancer.auth.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Role — JPA Entity mapped to the 'roles' table.
 *
 * Roles are pre-seeded in the database (via DataInitializer).
 * Two roles exist: ROLE_ADMIN and ROLE_USER.
 *
 * Why prefix with "ROLE_"?
 *   Spring Security's hasRole("ADMIN") automatically prepends "ROLE_",
 *   so hasRole("ADMIN") checks for authority "ROLE_ADMIN". If you use
 *   hasAuthority("ROLE_ADMIN"), no prefix is added. We use the ROLE_ prefix
 *   to maintain Spring Security conventions.
 *
 * @Enumerated(EnumType.STRING) — Stores the enum name as a VARCHAR in the DB
 *   (e.g., "ROLE_ADMIN"), not its ordinal (0, 1) which is fragile if the
 *   enum order changes.
 */
@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * RoleName enum — defines the possible role values.
     *
     * Using an enum (instead of a plain String) prevents typos and
     * allows switch expressions and exhaustive pattern matching.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private RoleName name;

    /** Optional human-readable description of the role's purpose. */
    @Column(length = 255)
    private String description;

    /**
     * RoleName — defines all valid roles in the system.
     *
     * ROLE_ADMIN: Full system access — can add/delete servers, manage users
     * ROLE_USER:  Read + trigger access — can view servers, route requests
     */
    public enum RoleName {
        ROLE_ADMIN,
        ROLE_USER
    }
}

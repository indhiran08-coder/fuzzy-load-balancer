package com.fuzzybalancer.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User — JPA Entity mapped to the 'users' table.
 *
 * Implements Spring Security's UserDetails interface so that Spring Security
 * can directly use this entity as the authentication principal.
 * This eliminates the need for a separate UserDetails adapter class.
 *
 * Annotations:
 * @Entity        — Marks this class as a JPA entity (mapped to a DB table)
 * @Table         — Specifies the table name and adds unique constraint on email
 * @Data          — Lombok: generates getters, setters, equals, hashCode, toString
 * @NoArgsConstructor — Lombok: generates default constructor (required by JPA)
 * @AllArgsConstructor — Lombok: generates all-args constructor
 * @Builder       — Lombok: generates builder pattern for clean object creation
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "email")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    /**
     * @Id       — Marks this as the primary key field
     * @GeneratedValue — AUTO_INCREMENT in MySQL; Hibernate chooses the
     *             strategy. IDENTITY delegates to the DB's auto-increment.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @Column(nullable = false, unique = true) — Enforces NOT NULL and
     * UNIQUE constraints at the database level.
     * Length 50 — usernames can be at most 50 characters.
     */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /**
     * Email — used for communications and as an alternate identifier.
     * Unique at DB level to prevent duplicate registrations.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * Password — stored as a BCrypt hash (never plaintext).
     * Length 255 to accommodate BCrypt's 60-char hash output with headroom.
     */
    @Column(nullable = false, length = 255)
    private String password;

    /**
     * @ManyToMany — A user can have multiple roles, a role can belong to
     *   multiple users. This creates a 'user_roles' join table automatically.
     *
     * FetchType.EAGER — Roles are loaded immediately with the user.
     *   Necessary because Spring Security needs the authorities synchronously
     *   during authentication (outside an open Session).
     *
     * CascadeType.MERGE — When a User is merged, merge its roles too.
     *   We don't cascade PERSIST (roles are pre-seeded, not created via user).
     */
    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;

    /** Whether this account is enabled. Disabled users cannot log in. */
    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /** Whether the account is not expired. Used for subscription-based access. */
    @Builder.Default
    @Column(nullable = false)
    private boolean accountNonExpired = true;

    /** Whether the account is not locked. Lock on too many failed attempts. */
    @Builder.Default
    @Column(nullable = false)
    private boolean accountNonLocked = true;

    /** Whether credentials (password) are not expired. */
    @Builder.Default
    @Column(nullable = false)
    private boolean credentialsNonExpired = true;

    /**
     * @CreationTimestamp — automatically set by Hibernate on INSERT.
     * updatable=false ensures it is never changed after creation.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * @PrePersist — JPA lifecycle hook: executed just before the entity
     * is first persisted to the database. Sets createdAt automatically.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * @PreUpdate — JPA lifecycle hook: executed just before an UPDATE query.
     * Keeps updatedAt in sync.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // -------------------------------------------------------------------------
    // UserDetails interface implementation
    // Spring Security calls these methods during authentication
    // -------------------------------------------------------------------------

    /**
     * getAuthorities() — Converts our Role entities into Spring Security's
     * GrantedAuthority objects. The role name (e.g., "ROLE_ADMIN") becomes
     * a SimpleGrantedAuthority, which is used in @PreAuthorize expressions.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority(role.getName().name()))
            .collect(Collectors.toSet());
    }

    @Override
    public boolean isAccountNonExpired() { return accountNonExpired; }

    @Override
    public boolean isAccountNonLocked() { return accountNonLocked; }

    @Override
    public boolean isCredentialsNonExpired() { return credentialsNonExpired; }

    @Override
    public boolean isEnabled() { return enabled; }
}

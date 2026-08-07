package com.fuzzybalancer.auth.repository;

import com.fuzzybalancer.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository — Spring Data JPA Repository for User entities.
 *
 * Why JpaRepository?
 *   Extending JpaRepository<User, Long> gives us 18+ pre-built methods:
 *   findById(), save(), delete(), findAll(), count(), existsById(), etc.
 *   No SQL needed for basic CRUD — Hibernate generates the queries.
 *
 * @Repository — Optional annotation (Spring Data detects it automatically),
 *   but included for clarity. Makes the interface a Spring bean and enables
 *   Spring's exception translation (SQLException → DataAccessException).
 *
 * Custom queries — we use JPQL (Java Persistence Query Language) which is
 *   database-agnostic. @Query uses JPQL by default. For native SQL, set
 *   nativeQuery = true.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * findByUsername() — Used by UserDetailsService during authentication.
     * Spring Security calls loadUserByUsername(username), which calls this.
     *
     * Spring Data derives the SQL automatically:
     *   SELECT * FROM users WHERE username = ?
     *
     * Returns Optional to force null-check at the call site,
     * preventing NullPointerExceptions.
     */
    Optional<User> findByUsername(String username);

    /**
     * findByEmail() — Alternative lookup for the registration check.
     * Used to verify no duplicate email exists before creating a user.
     */
    Optional<User> findByEmail(String email);

    /**
     * existsByUsername() — Efficient existence check using COUNT query.
     * Returns boolean — faster than findByUsername() when you don't
     * need the user object.
     *
     * Derived query: SELECT COUNT(*) > 0 FROM users WHERE username = ?
     */
    boolean existsByUsername(String username);

    /**
     * existsByEmail() — Same pattern as above, for email uniqueness.
     */
    boolean existsByEmail(String email);

    /**
     * findByUsernameWithRoles() — Fetches user and eagerly loads roles.
     *
     * JOIN FETCH prevents the N+1 query problem:
     *   Without JOIN FETCH: Hibernate runs 1 query for user + 1 query per role set
     *   With JOIN FETCH: Hibernate runs 1 query joining users + user_roles + roles
     *
     * Used during JWT validation to rebuild the SecurityContext.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.roles WHERE u.username = :username")
    Optional<User> findByUsernameWithRoles(String username);
}

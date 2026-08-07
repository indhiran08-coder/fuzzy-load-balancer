package com.fuzzybalancer.auth.repository;

import com.fuzzybalancer.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * RoleRepository — Spring Data JPA Repository for Role entities.
 *
 * Roles are pre-seeded (ROLE_ADMIN, ROLE_USER) via DataInitializer.
 * This repository is used to fetch existing roles by name during:
 *   1. User registration — assign ROLE_USER by default
 *   2. Admin creation — assign ROLE_ADMIN
 *   3. Role management endpoints
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * findByName() — Fetch a role by its enum name.
     *
     * Usage:
     *   roleRepository.findByName(Role.RoleName.ROLE_USER)
     *       .orElseThrow(() -> new RuntimeException("Role not found"));
     *
     * The Optional return forces proper handling of the case where
     * the role hasn't been seeded yet.
     */
    Optional<Role> findByName(Role.RoleName name);
}

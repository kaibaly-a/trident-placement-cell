package com.trident.placement.repository;

import com.trident.placement.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {

    /**
     * Look up an admin/TPO by their Azure AD email (preferred_username claim).
     * Email is stored lowercase in the admins table.
     */
    Optional<Admin> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
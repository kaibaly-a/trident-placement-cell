package com.trident.placement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents an Admin or TPO user in the placement portal.
 *
 * This table is separate from the STUDENT table.
 * Admins and TPOs log in via the same Azure AD tenant as students
 * but are identified by their email in THIS table, not in STUDENT.
 *
 * SQL to create:
 * CREATE TABLE admins (
 *     id          BIGINT AUTO_INCREMENT PRIMARY KEY,
 *     name        VARCHAR(255) NOT NULL,
 *     email       VARCHAR(255) NOT NULL UNIQUE,
 *     role        ENUM('ADMIN','TPO') NOT NULL,
 *     is_active   BOOLEAN NOT NULL DEFAULT TRUE,
 *     created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *     updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
 * );
 *
 * Seed data example:
 * INSERT INTO admins (name, email, role) VALUES
 *     ('Admin User',   'admin@trident.ac.in',  'ADMIN'),
 *     ('TPO Officer',  'tpo@trident.ac.in',    'TPO');
 */
@Entity
@Table(name = "admins", indexes = {
    @Index(name = "idx_admins_email", columnList = "email", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admin {

    @Id
    @GeneratedValue(
        strategy = GenerationType.SEQUENCE,
        generator = "admin_seq"
    )
    @SequenceGenerator(
        name = "admin_seq",
        sequenceName = "SEQ_ADMIN",
        allocationSize = 1
    )
    @Column(name = "id")
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    /**
     * Must match the Azure AD preferred_username / UPN exactly (lowercase).
     * e.g., "admin@trident.ac.in"
     */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * ADMIN or TPO — uses existing Role enum from com.trident.placement.enums
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private com.trident.placement.enums.Role role;

    /**
     * Soft-disable an admin account without deleting it.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
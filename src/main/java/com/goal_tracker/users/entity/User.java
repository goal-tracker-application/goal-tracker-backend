package com.goal_tracker.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Entity representing an application user (either registered or guest).
 */
@Getter
@Setter
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {

    /**
     * The unique identifier for the user.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The email address of the user.
     */
    @Column(unique = true, length = 320)
    private String email;

    /**
     * The hashed password of the user.
     */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /**
     * Indicates whether the user is a guest.
     */
    @Column(name = "is_guest", nullable = false)
    private Boolean isGuest = false;

    /**
     * The expiration date and time for guest users.
     */
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    /**
     * The date and time when the user was created.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * The date and time when the user was last updated.
     */
    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

        /**
     * Default no-arg constructor required by JPA.
     * Explicitly defined to prevent bytecode corruption issues with annotation processors.
     */
    public User() {
        super();
    }

    public User(String email, String passwordHash, Boolean isGuest, OffsetDateTime expiresAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.isGuest = isGuest;
        this.expiresAt = expiresAt;
    }
}
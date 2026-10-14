package com.goal_tracker.users.repository;

import com.goal_tracker.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for User entity persistence and database operations.
 * 
 * @see Usuario
 * @see JpaRepository
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their email address.
     *
     * @param email The email address to search for.
     * @return An Optional containing the User if found, or empty otherwise.
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if a user exists with the given email address.
     *
     * @param email The email address to check.
     * @return true if the email is already registered, false otherwise.
     */
    boolean existsByEmail(String email);
}
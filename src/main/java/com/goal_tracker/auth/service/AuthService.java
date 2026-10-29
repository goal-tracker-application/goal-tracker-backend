package com.goal_tracker.auth.service;

import com.goal_tracker.auth.dto.AuthResponse;
import com.goal_tracker.auth.dto.LoginRequest;
import com.goal_tracker.auth.dto.PasswordUpdateRequest;
import com.goal_tracker.auth.dto.RegisterRequest;
import com.goal_tracker.auth.exceptions.EmailAreadyInUseException;
import com.goal_tracker.auth.exceptions.IncorrectCurrentPasswordException;
import com.goal_tracker.auth.exceptions.PasswordMismatchException;
import com.goal_tracker.config.security.provider.JwtProvider;
import com.goal_tracker.users.entity.User;
import com.goal_tracker.users.repository.UserRepository;
import com.goal_tracker.goals.dto.GoalResponseDto;
import com.goal_tracker.goals.service.GoalService;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for orchestrating user authentication, registration,
 * guest creation, and JWT token issuance.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtTokenProvider;
    private final GoalService goalService;

    /**
     * Constructor for dependency injection of the authentication service.
     *
     * @param userRepository   User repository for data access.
     * @param passwordEncoder  Password encoder component.
     * @param jwtTokenProvider JWT token generation component.
     * @param goalService Service to manage goals.
     */
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtTokenProvider,
            GoalService goalService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.goalService = goalService;
    }

    /**
     * Registers a new regular user in the platform.
     *
     * @param request DTO containing email, password, and password confirmation.
     * @return {@link AuthResponse} with the signed token, email, and guest flag.
     * @throws PasswordMismatchException If the provided passwords do not match.
     * @throws EmailAreadyInUseException If the email is already registered.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!request.passwordsMatch()) {
            throw new PasswordMismatchException();
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAreadyInUseException(request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setIsGuest(false);

        User savedUser = userRepository.save(user);

        String token = jwtTokenProvider.generateToken(savedUser.getId(), savedUser.getEmail());

        return new AuthResponse(token, savedUser.getEmail(), savedUser.getIsGuest());
    }

    /**
     * Authenticates a user via credentials and issues a JWT.
     *
     * @param request DTO containing email and password.
     * @return {@link AuthResponse} with the signed token, email, and guest flag.
     * @throws BadCredentialsException If the user does not exist or the password does not match.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());

        return new AuthResponse(token, user.getEmail(), user.getIsGuest());
    }

    /**
     * Creates a temporary guest user and issues a JWT.
     *
     * @return {@link AuthResponse} with the signed token, guest email, and guest flag.
     */
    @Transactional
    public AuthResponse createGuest() {
        String guestEmail = "guest_" + UUID.randomUUID() + "@goaltracker.local";

        User guest = new User();
        guest.setEmail(guestEmail);
        guest.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        guest.setIsGuest(true);
        guest.setExpiresAt(OffsetDateTime.now().plusDays(1));

        User savedGuest = userRepository.save(guest);

        String token = jwtTokenProvider.generateToken(savedGuest.getId(), savedGuest.getEmail());

        return new AuthResponse(token, savedGuest.getEmail(), savedGuest.getIsGuest());
    }

    /**
     * Updates the password of the currently authenticated user.
     *
     * @param request DTO containing current password, new password, and confirmation.
     * @throws IncorrectCurrentPasswordException If the current password is incorrect.
     * @throws PasswordMismatchException If the new passwords do not match.
     * @throws BadCredentialsException If the userId does not bellong to an existing user.
     */
    @Transactional 
    public void updatePassword(Long userId, PasswordUpdateRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new BadCredentialsException("Invalid credentials");
        }
        User user = userRepository.findById(userId).get();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IncorrectCurrentPasswordException();
        }

        if (!request.passwordsMatch()) {
            throw new PasswordMismatchException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    /**
     * Deletes a user account along with all associated root goals and their status histories.
     *
     * @param userId The unique identifier of the user to be deleted.
     * @throws BadCredentialsException If the userId does not bellong to an existing user.
     */
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BadCredentialsException("Invalid credentials");
        }

        List<GoalResponseDto> rootGoals = goalService.getRootGoals(userId, null);

        for (GoalResponseDto rootGoal : rootGoals) {
            goalService.deleteGoal(userId, rootGoal.id());
        }

        userRepository.deleteById(userId);
    }

    /**
     * Automatically deletes expired guest users along with all their associated goals and histories.
     */
    @Transactional
    public void deleteExpiredGuests() {
        List<User> expiredGuests = userRepository.findExpiredGuests();

        for (User guest : expiredGuests) {
            deleteUser(guest.getId());
        }
    }
}
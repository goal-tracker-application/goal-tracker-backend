package com.goal_tracker.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.goal_tracker.auth.dto.AuthResponse;
import com.goal_tracker.auth.dto.CaptchaResponseDto;
import com.goal_tracker.auth.dto.GuestRequest;
import com.goal_tracker.auth.dto.LoginRequest;
import com.goal_tracker.auth.dto.PasswordUpdateRequest;
import com.goal_tracker.auth.dto.RegisterRequest;
import com.goal_tracker.auth.exceptions.EmailAreadyInUseException;
import com.goal_tracker.auth.exceptions.IncorrectCurrentPasswordException;
import com.goal_tracker.auth.exceptions.InvalidCaptchaException;
import com.goal_tracker.auth.exceptions.PasswordMismatchException;
import com.goal_tracker.auth.service.AuthService;
import com.goal_tracker.auth.service.CaptchaValidationService;
import com.goal_tracker.errors.ErrorCode;
import com.goal_tracker.exceptions.GlobalExceptionHandler;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;


/**
 * Unit tests for {@link AuthController}.
 * <p>
 * Focuses on testing the web layer independently by mocking the underlying {@link AuthService}.
 * Validates request mapping, request body validation, HTTP status code responses,
 * and JSON payload formatting using {@link MockMvc}.
 * </p>
 */
class AuthControllerUnitTest {

    /**
     * Main entry point for server-side Spring MVC testing.
     * Used to perform HTTP requests and assert responses.
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * Jackson Object Mapper used for serializing DTO objects to JSON format 
     * and deserializing JSON responses during tests.
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Mocked authentication service layer to isolate controller testing 
     * from business logic execution.
     */
    @MockitoBean
    private AuthService authService;

    @MockitoBean 
    private AuthController authController;

    @MockitoBean
    private CaptchaValidationService captchaValidationService;

    /**
     * Sets up the test environment before each test execution.
     * <p>
     * Initializes mocks, instantiates the target controller, and configures 
     * {@link MockMvc} in standalone mode to bypass the Spring application context 
     * and ensure isolated unit testing of the web layer.
     * </p>
     */
    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
        AuthController authController = new AuthController(authService);
        
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        objectMapper = JsonMapper.builder()
            .withCoercionConfig(LogicalType.Integer, config -> 
                config.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
            )
            .build();
        captchaValidationService = Mockito.mock(CaptchaValidationService.class);
    }

    /**
     * Tests successful user registration via POST /auth/register.
     * Expects a successful response containing the generated JWT token and user details.
     */
    @Test
    @DisplayName("POST /auth/register - Should register user successfully when payload is valid")
    void registerShouldRegisterUserSuccessfully() throws Exception {
        String validCaptchaToken = "dummy-valid-captcha-token";
        doNothing().when(captchaValidationService).validateCaptcha(validCaptchaToken);
        // Arrange
        RegisterRequest request = new RegisterRequest("test@example.com", "SecurePassword123!", "SecurePassword123!", validCaptchaToken);
        AuthResponse response = new AuthResponse("mock-jwt-token","test@example.com", false);

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.isGuest").value(false));
    }

    /**
     * Tests POST /auth/register failure due to invalid captcha token.
     */
    @Test
    @DisplayName("POST /auth/register - Should return 400 Bad Request when the captcha token is invalid.")
    void registerShouldReturn400WhenInvalidCaptchaToken() throws Exception {
        String invalidCaptchaToken = "invalid";
        RegisterRequest request = new RegisterRequest
        (
            "test@goaltracker.com", 
            "SecurePassword123!", 
            "SecurePassword123!",
            invalidCaptchaToken
        );

        CaptchaResponseDto captchaResponse = new CaptchaResponseDto(false);
        
        doThrow(new InvalidCaptchaException(captchaResponse)).when(authService).register(request);
        

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_CAPTCHA.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_INVALID_CAPTCHA.getDefaultMessage()) )
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests POST /auth/register failure due to type mismatch in the JSON payload 
     * (e.g., passing invalid data types or malformed structures).
     */
    @Test
    @DisplayName("POST /auth/register - Should return 400 Bad Request when JSON payload has type mismatch")
    void registerShouldReturn400WhenTypeMismatch() throws Exception {
        String validCaptchaToken = "dummy-valid-captcha-token";
        doNothing().when(captchaValidationService).validateCaptcha(validCaptchaToken);
        Map<String,Object> maformedJsonMap = Map.of(
            "email", "test@example.com",
            "password" , List.of("SecurePassword123!"),
            "confirmPassword", "SecurePassword123",
            "captchaToken", validCaptchaToken
        );
        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maformedJsonMap)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_TYPE_MISMATCH.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_TYPE_MISMATCH.getDefaultMessage()) )
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests POST /auth/register failure due to standard bean validation errors 
     * (e.g., blank email or empty fields violating constraints).
     */
    @Test
    @DisplayName("POST /auth/register - Should return 400 Bad Request when validation constraints fail")
    void registerShouldReturn400WhenValidationFails() throws Exception {
        String validCaptchaToken = "dummy-valid-captcha-token";
        doNothing().when(captchaValidationService).validateCaptcha(validCaptchaToken);
        RegisterRequest request = new RegisterRequest("invalid-email", "SecurePassword123!", "SecurePassword123!", validCaptchaToken);

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_VALIDATION_FAILED.getDefaultMessage()) )
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests POST /auth/register failure when the provided password is too weak 
     * according to security policy requirements.
     */
    @Test
    @DisplayName("POST /auth/register - Should return 400 Bad Request when password is too weak")
    void registerShouldReturn400WhenPasswordIsTooWeak() throws Exception {
        String validCaptchaToken = "dummy-valid-captcha-token";
        doNothing().when(captchaValidationService).validateCaptcha(validCaptchaToken);
        Map<String,Object> weakPasswordJson = Map.of(
            "email", "test@example.com",
            "password" , "12345",
            "confirmPassword", "12345",
            "captchaToken", validCaptchaToken
        );
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weakPasswordJson)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_VALIDATION_FAILED.getDefaultMessage()) )
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests POST /auth/register failure when password confirmation fields do not match 
     * (if your application handles password matching during registration).
     */
    @Test
    @DisplayName("POST /auth/register - Should return 400 Bad Request when passwords mismatch")
    void registerShouldReturn400WhenPasswordsMismatch() throws Exception {
        String validCaptchaToken = "dummy-valid-captcha-token";
        doNothing().when(captchaValidationService).validateCaptcha(validCaptchaToken);
        RegisterRequest request =  new RegisterRequest
        (
            "test@example.com", 
            "StrongPassword123!", 
            "StrongPassword124!",
            validCaptchaToken
        );

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new PasswordMismatchException());
        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_PASSWORD_MISMATCH.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_PASSWORD_MISMATCH.getDefaultMessage()) )
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests POST /auth/register failure when the email address is already registered 
     * in the system, expecting a conflict (409) or appropriate error status.
     */
    @Test
    @DisplayName("POST /auth/register - Should return 409 Conflict when email is already taken")
    void registerShouldReturn409WhenEmailAlreadyInUse() throws Exception {
        String validCaptchaToken = "dummy-valid-captcha-token";
        doNothing().when(captchaValidationService).validateCaptcha(validCaptchaToken);
        RegisterRequest request = new RegisterRequest
        (
            "existing@example.com", 
            "SecurePassword123!", 
            "SecurePassword123!",
            validCaptchaToken
        );

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAreadyInUseException("existing@example.com"));

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_EMAIL_ALREADY_EXISTS.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_EMAIL_ALREADY_EXISTS.getDefaultMessage()) )
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests successful user login via POST /auth/login.
     * Expects a successful response containing the generated JWT token and user details.
     */
    @Test
    @DisplayName("POST /auth/login - Should authenticate user successfully when credentials are valid")
    void loginShouldLoginUserSuccessfully() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest("test@example.com", "SecurePassword123!");
        AuthResponse response = new AuthResponse("mock-jwt-token", "test@example.com", false);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.isGuest").value(false));
    }

    /**
     * Tests POST /auth/login failure due to type mismatch in the JSON payload 
     * (e.g., passing invalid data types or malformed structures).
     */
    @Test
    @DisplayName("POST /auth/login - Should return 400 Bad Request when JSON payload has type mismatch")
    void loginShouldReturn400WhenTypeMismatch() throws Exception {
        String malformedJson = "{\"email\": \"test@example.com\", \"password\": [true]}";

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_TYPE_MISMATCH.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_TYPE_MISMATCH.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests POST /auth/login failure due to standard bean validation errors 
     * (e.g., blank email or empty password violating constraints).
     */
    @Test
    @DisplayName("POST /auth/login - Should return 400 Bad Request when validation constraints fail")
    void loginShouldReturn400WhenValidationFails() throws Exception {
        LoginRequest request = new LoginRequest("invalid-email", "");

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_VALIDATION_FAILED.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests POST /auth/login failure when credentials are invalid 
     * (covers both non-existent email and incorrect password, throwing BadCredentialsException).
     */
    @Test
    @DisplayName("POST /auth/login - Should return 401 Unauthorized when credentials are invalid")
    void loginShouldReturn401WhenCredentialsAreInvalid() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest("test@example.com", "WrongPassword123!");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_UNAUTHORIZED.name())) // Asegúrate de tener este ErrorCode o ajústalo al tuyo
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_UNAUTHORIZED.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }
    
    /**
     * Tests successful guest session creation via POST /auth/guest.
     * Expects a successful response containing a temporary guest JWT token and details.
     */
    @Test
    @DisplayName("POST /auth/guest - Should create guest session successfully")
    void createGuestShouldCreateGuestSuccessfully() throws Exception {
        String validCaptchaToken = "dummy-valid-captcha-token";
        doNothing().when(captchaValidationService).validateCaptcha(validCaptchaToken);
        
        GuestRequest request = new GuestRequest(validCaptchaToken);
        AuthResponse response = new AuthResponse("mock-guest-jwt-token", "guest_123@goaltracker.local", true);

        when(authService.createGuest(request)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").value("mock-guest-jwt-token"))
                .andExpect(jsonPath("$.email").value("guest_123@goaltracker.local"))
                .andExpect(jsonPath("$.isGuest").value(true));
    }


    /**
     * Tests faillure guest session creation via POST /auth/guest.
     * Expects bad request status after the service throws {@link InvalidCaptchaException}
     */
    @Test
    @DisplayName("POST /auth/guest - Should throw InvalidCaptchaException when the capatcha token is invald")
    void createGuestShouldReturn400WhenInvalidCaptchaToken() throws Exception {
        String invalidCaptchaToken = "invalid";

        GuestRequest request = new GuestRequest(invalidCaptchaToken);
        CaptchaResponseDto captchaResponse = new CaptchaResponseDto(false);
        doThrow(new InvalidCaptchaException(captchaResponse)).when(authService).createGuest(request);

        // Act & Assert
        mockMvc.perform(post("/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_CAPTCHA.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_INVALID_CAPTCHA.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests successful password update via PATCH /auth/password.
     * Expects a 204 No Content response when current and new passwords are valid.
     */
    @Test
    @DisplayName("PATCH /auth/password - Should update password successfully when payload is valid")
    void updatePasswordShouldReturn204WhenValid() throws Exception {
        // Arrange
        PasswordUpdateRequest request = new PasswordUpdateRequest("CurrentPass123!", "NewSecurePass123!", "NewSecurePass123!");

        // Act & Assert
        mockMvc.perform(patch("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    /**
     * Tests PATCH /auth/password failure due to type mismatch in the JSON payload 
     * (e.g., passing invalid data types or malformed structures).
     */
    @Test
    @DisplayName("PATCH /auth/password - Should return 400 Bad Request when JSON payload has type mismatch")
    void updatePasswordShouldReturn400WhenTypeMismatch() throws Exception {
        String malformedJson = "{\"currentPassword\": 12345, \"newPassword\": true, \"confirmPassword\": []}";

        // Act & Assert
        mockMvc.perform(patch("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_TYPE_MISMATCH.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_TYPE_MISMATCH.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests PATCH /auth/password failure due to standard bean validation errors 
     * (e.g., blank fields violating constraints).
     */
    @Test
    @DisplayName("PATCH /auth/password - Should return 400 Bad Request when validation constraints fail")
    void updatePasswordShouldReturn400WhenValidationFails() throws Exception {
        String malformedJson = "{\"currentPassword\": \"\", \"newPassword\": \"\", \"confirmPassword\": \"\"}";

        // Act & Assert
        mockMvc.perform(patch("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_VALIDATION_FAILED.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests PATCH /auth/password failure when the new password is too weak 
     * according to security policy annotations.
     */
    @Test
    @DisplayName("PATCH /auth/password - Should return 400 Bad Request when new password is too weak")
    void updatePasswordShouldReturn400WhenPasswordIsTooWeak() throws Exception {
        PasswordUpdateRequest request = new PasswordUpdateRequest("CurrentPass123!", "12345", "12345");

        // Act & Assert
        mockMvc.perform(patch("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_VALIDATION_FAILED.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests PATCH /auth/password failure when the provided current password does not match 
     * the user's actual current password in the system.
     */
    @Test
    @DisplayName("PATCH /auth/password - Should return 400 Bad Request when current password is incorrect")
    void updatePasswordShouldReturn400WhenCurrentPasswordIsIncorrect() throws Exception {
        // Arrange
        PasswordUpdateRequest request = new PasswordUpdateRequest("WrongCurrent123!", "NewSecurePass123!", "NewSecurePass123!");

        Mockito.doThrow(new IncorrectCurrentPasswordException())
                .when(authService).updatePassword(any(), any(PasswordUpdateRequest.class));

        // Act & Assert
        mockMvc.perform(patch("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CURRENT_PASSWORD.name())) 
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_CURRENT_PASSWORD.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests PATCH /auth/password failure when the new password and confirmation fields do not match.
     */
    @Test
    @DisplayName("PATCH /auth/password - Should return 400 Bad Request when new passwords mismatch")
    void updatePasswordShouldReturn400WhenPasswordsMismatch() throws Exception {
        // Arrange
        PasswordUpdateRequest request = new PasswordUpdateRequest("CurrentPass123!", "NewSecurePass123!", "DifferentPass123!");

        Mockito.doThrow(new PasswordMismatchException())
                .when(authService).updatePassword(any(), any(PasswordUpdateRequest.class));

        // Act & Assert
        mockMvc.perform(patch("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_PASSWORD_MISMATCH.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_PASSWORD_MISMATCH.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests PATCH /auth/password failure when the current user is not found.
     */
    @Test
    @DisplayName("PATCH /auth/password - Should return 401 Unauthorized when the current user is not found")
    void updatePasswordShouldReturn401WhenCurrentUserNotFound() throws Exception {
        // Arrange
        PasswordUpdateRequest request = new PasswordUpdateRequest("CurrentPass123!", "NewSecurePass123!", "NewSecurePass123!");

        Mockito.doThrow(new BadCredentialsException("Invalid credentials"))
                .when(authService).updatePassword(any(), any(PasswordUpdateRequest.class));

        mockMvc.perform(patch("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_UNAUTHORIZED.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }

    /**
     * Tests successful user account deletion via DELETE /auth/account.
     * Expects a 204 No Content response when the user account is deleted successfully.
     */
    @Test
    @DisplayName("DELETE /auth/account - Should delete user account successfully and return 204")
    void deleteAccountShouldReturn204WhenSuccessful() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/auth/account"))
                .andExpect(status().isNoContent());
    }

    /**
     * Tests DELETE /auth/account failure when the user is not found in the database.
     * Expects a 401 Unauthorized response.
     */
    @Test
    @DisplayName("DELETE /auth/account - Should return 401 Unauthorized when user is not found")
    void deleteAccountShouldReturn401WhenUserNotFound() throws Exception {
        Mockito.doThrow(new BadCredentialsException("Invalid credentials"))
                .when(authService).deleteUser(any());

        mockMvc.perform(delete("/auth/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ERR_UNAUTHORIZED.getDefaultMessage()))
                .andExpect(jsonPath("$.trackingId", startsWith("ERR-")));
    }
}
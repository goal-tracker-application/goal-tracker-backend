# goal-tracker-backend

[Read this README in Spanish (Español)](README.es.md)

RESTful API backend service for Goal Tracker built with Spring Boot, PostgreSQL, and Flyway. Handles user authentication (JWT), secure account management, version-controlled database migrations, cascading recursive goal trees, and automated maintenance tasks.

## 1. Configuration and Environment Variables

The application is configured through property files and relies heavily on environment variables to ensure security and flexibility across different environments:

- **Database (PostgreSQL):** Uses `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. Its schema policy is set to `validate` (relying on **Flyway** for migrations located in `classpath:db/migration`).
- **Security & JWT:** The secret key for token signing and expiration time are configured via `APP_SECURITY_JWT_SECRET` and `APP_SECURITY_JWT_EXPIRATION_MS` (with a secure fallback default for local testing).
- **CORS:** Allowed origins for external client requests are managed using `APP_SECURITY_CORS_ALLOWED_ORIGINS`.
- **Context Path:** All API endpoints are exposed under the `/api` prefix.
- **Profiles:** Specific profiles can be activated, such as `dev` (which enables detailed Hibernate SQL and query binding logs for debugging) or `prod`.

## 2. Security

### Spring Security

The application is secured using Spring Security, with public access explicitly granted to operational endpoints (such as Actuator health checks) and authentication routes, while securing private API endpoints.

### JWT (JSON Web Tokens)

Stateless authentication is implemented using JWT. Once a user successfully logs in or registers, a token is issued, which must be included in the `Authorization` header (`Bearer <token>`) to access protected resources.

### CORS (Cross-Origin Resource Sharing)

CORS is configured globally to allow authorized clients (such as frontend applications) to communicate securely with the API endpoints.

## 3. Endpoints

### Actuator for Database Health Indication (Public)

The following endpoint can be accessed to check the health of the system, specifically the database connection:

* **GET** `/actuator/health/` - It checks the status of the database connection and returns the following formatted JSON response:

    - **JSON Example:**
    ``` json
    {
        "application": "Goal Tracker API",
        "database": "UP",
        "timestamp": "2001-12-30T01:01:01"
    }
    ```
    
    - **Attribute details:**

        * `application`: Name of the application.
        * `database`: Database connection status, can be `UP` or `DOWN`.
        * `timestamp`: Current timestamp in `yyyy-mm-ddTHH:MM:SS` format.

### Authentication (Public)

* **POST** `/auth/register` - Validates registration data, creates a regular user account, and returns an access JWT.
    - **Request Payload Example:**
    ```json
    {
        "email": "test@example.com",
        "password": "SecurePassword123!",
        "confirmPassword": "SecurePassword123!"
    }
    ```
    - **Request Attribute Details:**
        * `email`: User's email address (must be valid and unique).
        * `password`: Account password (must meet security policies: 10-72 characters, at least one uppercase letter, one number, and one special character).
        * `confirmPassword`: Password confirmation (must match `password` exactly).

    - **Response Payload Example:**
    ```json
    {
        "token": "eyJhbGciOiJIUzI1NiIsIn...",
        "email": "test@example.com",
        "isGuest": false
    }
    ```
    - **Response Attribute Details:**
        * `token`: Signed JWT access token.
        * `email`: Registered user's email address.
        * `isGuest`: Indicator of whether the user is a guest (`false` for regular users).

* **POST** `/auth/login` - Authenticates a user via email and password, returning a signed JWT access token.
    - **Request Payload Example:**
    ```json
    {
        "email": "test@example.com",
        "password": "SecurePassword123!"
    }
    ```
    - **Request Attribute Details:**
        * `email`: User's registered email address.
        * `password`: User's current password.

    - **Response Payload Example:**
    ```json
    {
        "token": "eyJhbGciOiJIUzI1NiIsIn...",
        "email": "test@example.com",
        "isGuest": false
    }
    ```
    - **Response Attribute Details:**
        * `token`: Signed JWT access token.
        * `email`: User's email address.
        * `isGuest`: Indicator of whether the user is a guest.


* **POST** `/auth/guest` - Provisions a temporary guest user account and returns an access JWT.
    
    - This endpoint does not require a request body.

    - **Response Payload Example:**
    ```json
    {
        "token": "eyJhbGciOiJIUzI1NiIsIn...",
        "email": "guest_uuid@goaltracker.local",
        "isGuest": true
    }
    ```
    - **Response Attribute Details:**
        * `token`: Temporary JWT access token for the guest session.
        * `email`: Automatically generated temporary email address.
        * `isGuest`: Indicator of whether the user is a guest (`true` for guest sessions).


* **PATCH** `/auth/password` - Allows an authenticated user to change their password by validating the current one Returns an HTTP 204 No Content response with an empty body on success
    - **Request Payload Example:**
    ```json
    {
        "currentPassword": "CurrentPass123!",
        "newPassword": "NewSecurePass123!",
        "confirmNewPassword": "NewSecurePass123!"
    }
    ```
    - **Request Attribute Details:**
        * `currentPassword`: User's current password for security verification.
        * `newPassword`: New password to set (subject to security complexity policies: 10-72 characters, uppercase, number, and special character).
        * `confirmNewPassword`: Confirmation of the new password (must match `newPassword`).

    - No payload is returned in the body after a successful password update.

### Error Handling

The API implements a global exception interceptor `GlobalExceptionHandler` that unifies all failed responses under a strict semantic contract based on a domain enum `ErrorCode`, preventing the leakage of internal server stack traces.

#### General Error Structure (ErrorResponse)

All API error responses are standardized under the `ErrorResponse` Data Transfer Object. Upon any failure, the system automatically generates a unique tracking identifier `trackingId` based on a UUID v4 prefixed with ERR-. This identifier is replicated across audit logs and server logs to allow rapid traceability and operational support.

- **JSON Example**

``` json
{
  "code": "ERR_VALIDATION_FAILED",
  "message": "The submitted data does not meet the required validations.",
  "details": [
    "The field 'title' cannot be blank.",
    "The target value must be greater than 0."
  ],
  "trackingId": "ERR-7c9b1a2d-4e8f-4b3c-9a1d-5e6f7a8b9c0d"
}
```
- **Attribute details:**
    * code : A machine-readable string identifier representing the specific type or category of error, used by client applications to handle errors programmatically.
    * message: A human-readable, user-friendly summary describing what went wrong in plain language.
    * details: An array of specific error messages providing granular information about individual field validation failures or constraint violations (it's an optional field, if there are no details the JSON does not include the field).
    * trackingId: A unique identifier (UUID) assigned to the specific error occurrence, used for logging, debugging, and tracing the error in server logs.

> **Serialization Behavior:**  
> The `details` property is included as an array of explanatory strings or field validation specifications. If there is no supplementary information regarding the failure, the `details` key is automatically omitted from the JSON body sent to the client.

#### Available Error Codes

| Static Code | Description / Root Cause | HTTP Status |
| :--- | :--- | :--- |
| `ERR_VALIDATION_FAILED` | Input validation failures (`@Valid` on DTOs or constraints violations). Includes detailed explanatory messages per field. | 400 Bad Request |
| `ERR_TYPE_MISMATCH` | Malformed URL/Query parameters or strict type coercion failures in parameters (e.g., sending text where a numeric ID is expected). | 400 Bad Request |
| `ERR_PASSWORD_MISMATCH` | The provided password do not match it's confirmation on register or update password requests. | 400 Bad Request |
| `INVALID_CURRENT_PASSWORD` | The provided current password is incorrect for an update password request. | 400 Bad Request |
| `ERR_UNAUTHORIZED` | The request lacks valid authentication credentials, the token has expired, or the provided access data is incorrect. | 401 Unauthorized |
| `ERR_FORBIDDEN` | The authenticated user attempts to access a resource or perform an action for which they lack sufficient permissions. | 403 Forbidden |
| `ERR_NOT_FOUND` | The requested resource does not exist in the database or is not active in the system. | 404 Not Found |
| `ERR_EMAIL_ALREADY_EXISTS` | Occurs when attempting to register a user with an email address that is already occupied by another account. | 409 Conflict |
| `ERR_DUPLICATE_GOAL_LOG` | Occurs when attempting to create a progress entry (check-in) for a goal on a date that already has an existing entry. | 409 Conflict |
| `ERR_INVALID_GOAL_HIERARCHY` | Occurs when the goal hierarchy is invalid (e.g., a goal cannot be its own parent or generate circular dependencies). | 409 Conflict |
| `ERR_GOAL_DATE_RANGE_INVALID` | Occurs when the start date is later than the end date for a goal. | 422 Unprocessable Entity |
| `ERR_INTERNAL_SERVER_ERROR` | Final safety net to capture unexpected system exceptions (connectivity drops, null pointers, or unforeseen infrastructure failures). Full traceability remains hidden behind the `trackingId`. | 500 Internal Server Error |

## 4. Testing

### Configuring Testcontainers

#### Linux Configuration

To enable Testcontainers to run using the local Docker installation, run the following command:
``` bash
echo "api.version=1.44" > ~/.docker-java.properties
```

#### VS Code Configuration

To enable Testcontainers in **VS Code**, add the following configuration to the root of the project:
- Create the **.vscode** hidden folder:
``` bash
mkdir .vscode
```

- Create the **settings.json** file:
``` bash
touch settings.json
```

- Open or edit the file:
``` bash
nano settings.json
```

- Add this content to the file, save with **Ctrl+O**, and close the file with **Ctrl+X**:
``` json
{
    "java.test.config": {
        "vmArgs": [
            "-Ddocker.host=unix:///run/docker.sock",
            "-Dtestcontainers.docker.socket.override=/run/docker.sock"
        ]
    },
    "terminal.integrated.env.linux": {
        "DOCKER_HOST": "unix:///run/docker.sock"
    },
    "java.configuration.updateBuildConfiguration": "interactive"
}
```

### Running the Tests

- **Run the full test suite:** Runs all unit and integration tests using the `test` configuration profile, utilizing Testcontainers for the database connection:

``` bash
./mvnw test
```

- **Run a specific test suite:** Runs the tests of a specific file:

``` bash
./mvnw test -Dtest=NameOfTheTestFileWithOutExtention
```
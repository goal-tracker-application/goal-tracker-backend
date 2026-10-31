# goal-tracker-backend

[Read this README in Spanish (Español)](README.es.md)

RESTful API backend service for Goal Tracker built with Spring Boot, PostgreSQL, and Flyway. Handles user authentication (JWT), secure account management, version-controlled database migrations, cascading recursive goal trees, and automated maintenance tasks.

## 1. Configuration and Environment Variables

The application is configured through properties in the configuration file and relies primarily on environment variables to ensure security and flexibility across different environments:

- **Database (PostgreSQL):** Uses `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. Its schema policy is set to `validate` (relying on **Flyway** for migrations at `classpath:db/migration`).
- **Redis (Cache & Rate Limiting):** Configured via `REDIS_HOST` and `REDIS_PORT` to establish the connection for state persistence (defaulting to `localhost` and `6379` for local environments).
- **Security & JWT:** The secret key for token signing and its expiration time are configured via `APP_SECURITY_JWT_SECRET` and `APP_SECURITY_JWT_EXPIRATION_MS` (with a secure default value for local testing).
- **Rate Limiting (IP Protection):** Managed under the `app.security.rate-limit` prefix, allowing configuration of maximum capacity, refill rate, and periodicity via environment variables:
  - `APP_SECURITY_RATE_LIMIT_CAPACITY` (default: `5` tokens).
  - `APP_SECURITY_RATE_LIMIT_REFILL_TOKENS` (default: `5` tokens).
  - `APP_SECURITY_RATE_LIMIT_REFILL_DURATION_MINUTES` (default: `1` minute).
- **CORS:** Allowed origins for external client requests are managed using `APP_SECURITY_CORS_ALLOWED_ORIGINS`.
- **CAPTCHA Validation:** Configured under the `app.security.captcha` prefix to verify frontend tokens against external providers (such as Google reCAPTCHA or Cloudflare Turnstile):
  - `CAPTCHA_SECRET_KEY` (default: `dummy-secret-key`).
  - `CAPTCHA_VERIFY_URL` (default: `https://www.google.com/recaptcha/api/siteverify`).
- **Base Path (Context Path):** All API endpoints are exposed under the `/api` prefix.
- **Profiles:** Specific profiles such as `dev` (which enables detailed SQL and Hibernate query logs for debugging) or `prod` can be activated.

## 2. Security

### 2.1 Spring Security

The application is secured using Spring Security, with public access explicitly granted to operational endpoints (such as Actuator health checks) and authentication routes, while securing private API endpoints.

### 2.2 JWT (JSON Web Tokens)

Stateless authentication is implemented using JWT. Once a user successfully logs in or registers, a token is issued, which must be included in the `Authorization` header (`Bearer <token>`) to access protected resources.

### 2.3 CORS (Cross-Origin Resource Sharing)

CORS is configured globally to allow authorized clients (such as frontend applications) to communicate securely with the API endpoints.

### 2.4 Rate Limiting (IP-based Protection)

To protect authentication and registration endpoints (`/auth/**`) against brute-force attacks and automated abuse, the application enforces a distributed IP-based rate-limiting mechanism backed by **Redis** and **Bucket4j**. Requests exceeding the configured threshold are intercepted and blocked with an **HTTP 429 (Too Many Requests)** response.

### 2.5 CAPTCHA Protection

To prevent automated bot abuse, spam, and mass account creation, critical public endpoints (`/auth/register` and `/auth/guest`) require a valid CAPTCHA token (compatible with Google reCAPTCHA or Cloudflare Turnstile). Requests with missing, blank, or invalid tokens are intercepted and rejected with an **HTTP 400 (Bad Request)** response (`ERR_INVALID_CAPTCHA`).

## 3. Endpoints

### 3.1 Actuator for Database Health Indication (Public)

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

### 3.2 Authentication (Public)

#### 3.2.1 Common response payload:

This response payload is common to all the endpoints with a response payload in the authentication **API**:

- **Response Payload Example (AuthResponse):**
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

#### 3.2.2 Endpoints
* **POST** `/auth/register` - Validates registration data, creates a regular user account after validating the CAPTCHA token of the  and returns an access JWT.

    - **Request Payload Example:**
    ```json
    {
        "email": "test@example.com",
        "password": "SecurePassword123!",
        "confirmPassword": "SecurePassword123!",
        "captchaToken": "dummy-captcha-token-from-frontend"
    }
    ```
    - **Request Attribute Details:**
        * `email`: User's email address (must be valid and unique).
        * `password`: Account password (must meet security policies: 10-72 characters, at least one uppercase letter, one number, and one special character).
        * `confirmPassword`: Password confirmation (must match `password` exactly).
        * `captchaToken`: Token generated by the client's CAPTCHA verification widget (mandatory).

    - **Response Payload (AuthResponse)**: This endpoint responses with a **AuthResponse** json as the one examplified in the [3.2.1 section](#321-common-response-payload-example) .

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

    - **Response Payload (AuthResponse)**: This endpoint responses with a **AuthResponse** json as the one examplified in the [3.2.1 section](#321-common-response-payload-example).


* **POST** `/auth/guest` - Provisions a temporary guest user account after validating the CAPTCHA token and returns an access JWT.

    - **Request Payload Example:**
    ```json
    {
        "captchaToken": "dummy-captcha-token-from-frontend"
    }
    ```
    - **Request Attribute Details:**
        * `captchaToken`: Token generated by the client's CAPTCHA verification widget (mandatory).
    
    - **Response Payload (AuthResponse)**: This endpoint responses with a **AuthResponse** json as the one examplified in the [3.2.1 section](#321-common-response-payload-example).


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

* **DELETE** `/auth/account` - Deletes the currently authenticated user account, along with all associated goals, recursive sub-goal trees, and status histories.

    - This endpoint does not require a request body.

    - No payload is returned in the body upon successful deletion (HTTP 204 No Content).

### 3.3 Goals (Authenticated)

Endpoints under `/goals` require a valid JWT token passed in the `Authorization` header (`Bearer <token>`).

#### 3.3.1 Common response payload:

This response payload is common to various endpoints with a response payload in the goals **API**:

- **Response Payload Example (GoalResponseDto):**
    ```json
    {
        "id": 6,
        "title": "Goal Title",
        "description": "Goal Description",
        "startDate": "1999-09-25",
        "endDate": "1999-09-27",
        "statusId": 1,
        "parentId": 5,
        "createdAt": "1999-09-24T21:22:33",
        "updatedAt": "1999-09-25T08:01:25"
    }
    ```
    - **Response Attribute Details:**
        * `id`: Unique numeric identifier of the goal (it cannot be null).
        * `title`: Title of the goal (it cannot be blank and its maximum size is 255 characters).
        * `description`: Description of the goal (it cannot be blank and its maximum size it's 1000 characters).
        * `startDate`: Starting date of the goal for scheduling it (it can be null, but if provided must have the format "yyyy-mm-dd").
        * `endDate`: Ending date of the goal for scheduling it (it can be null, but if provided must have the format "yyyy-mm-dd").
        * `statusId`: Numeric identifier of the status associated with the goal (it cannot be null and will only take the following values [WAITING(1), IN_PROGRESS(2), PAUSED(3), COMPLETED(4), FAILED(5), DISCARDED(6)]).
        * `parentId`: Numeric identifier of the goal associated as the parent of this goal (it can be null, but if provided must belong to an existing goal).
        * `createdAt`: Time stamp taken at the moment of the register creation (it cannot be null and it must have the format "yyyy-mm-ddTHH:MM:SS")
        * `updatedAt`: Time stamp taken at the moment of the last update of the register (it can be null, but if provided it must have the format "yyyy-mm-ddTHH:MM:SS")

#### 3.3.2 Endpoints

* **GET** `/goals/roots?status_id={statusId}` - Retrieves all root goals belonging to the authenticated user, with an optional filter by status ID (the `status_id` parameter is optional, but if provided, it must belong to a valid status within the following list [WAITING(1), IN_PROGRESS(2), PAUSED(3), COMPLETED(4), FAILED(5), DISCARDED(6)]).
    
    - This endpoint does not require a request body.

    - **Response Payload (GoalResponseDto[]):** This endpoint responses with a list of **GoalResponseDto** json as the one examplified in the [3.3.1 section](#331-common-response-payload).

* **GET** `/goals/{id}/tree` - Retrieves the complete hierarchical tree of a goal along with its sub-goals for the authenticated user (the `id` parameter is obligatory, must belong to an existing goal and be greater than zero).

    - This endpoint does not require a request body.

    - **Response Payload Example (GoalTreeResponseDto):**
     ```json
    {
        "id": 6,
        "title": "Goal Title",
        "statusId": 1,
        "startDate": "1999-09-25",
        "endDate": "1999-09-27",
        "subGoals": [
            {
                "id": 8,
                "title": "Sub Goal Title",
                "statusId": 1,
                "startDate": "1999-09-25",
                "endDate": "1999-09-27",
                "subGoals": []
            },
            {
                "id": 9,
                "title": "Sub Goal Title",
                "statusId": 1,
                "startDate": "1999-09-25",
                "endDate": "1999-09-27",
                "subGoals": []
            }
        ]
    }
    ```
    - **Response Attribute Details:**
        * `id`: Unique numeric identifier of the goal (it cannot be null).
        * `title`: Title of the goal (it cannot be blank and its maximum size is 255 characters).
        * `statusId`: Numeric identifier of the status associated with the goal (it cannot be null and will only take the following values [WAITING(1), IN_PROGRESS(2), PAUSED(3), COMPLETED(4), FAILED(5), DISCARDED(6)]).
        * `startDate`: Starting date of the goal for scheduling it (it can be null, but if provided must have the format "yyyy-mm-dd").
        * `endDate`: Ending date of the goal for scheduling it (it can be null, but if provided must have the format "yyyy-mm-dd").
        * `subGoals`: List of objects of the **GoalTreeResponseDto** type representing all the goals this goal depends on, every sub goal can have it's own list of subGoals.
    
* **GET** `/goals/{id}/history` - Retrieves the chronological timeline of status changes for a specific goal belonging to the authenticated user (the `id` parameter is obligatory, must belong to an existing goal and be greater than zero).

    - This endpoint does not require a request body.

    - **Response Payload Example (GoalStatusHistoryResponseDto[]):**
     ```json
     [
        {
            "statusId": 1,
            "cause": "CREATION",
            "notes": null,
            "changedAt": "1999-09-24T21:22:33"
        },
        {
            "statusId": 2,
            "cause": "USER",
            "notes": "Goal set in progress",
            "changedAt": "1999-09-27T08:30:43"
        }
     ]
     ```
    - **Response Attribute Details:**
        * `statusId`: Numeric identifier of the status associated with the goal (it cannot be null and will only take the following values [WAITING(1), IN_PROGRESS(2), PAUSED(3), COMPLETED(4), FAILED(5), DISCARDED(6)]).
        * `cause`: Cause of the status change, it can only be one of the following:
            - **CREATION:** Automatic transition triggered on goal creation.
            - **USER:** Direct transition triggered explicitly by the user.
            - **UPWARD_CASCADE:** Automatic transition triggered upwards by an event on a child goal.
            - **DOWNWARD_CASCADE:** Automatic transition triggered downwards by an event on a parent goal.
            - **AUTO_ACTIVATION:** Automatic transition triggered when the goal automatically starts based on schedule.
            - **AUTO_FAILURE:** Automatic transition triggered when the goal fails due to expiration or unmet deadlines.
        * `notes`: Notes taken by user when updating the goal status.
        * `changedAt`: Timestamp of the moment of the status change following the format "yyyy-mm-ddTHH:MM:DD"

* **POST** `/goals` - Creates a new goal for the authenticated user, supporting optional parent goal association and hierarchical date validations.

    - **Request Payload Example (GoalCreateRequestDto):**
    ``` json
    {
        "title": "Goal Title",
        "description": "Goal description",
        "startDate": "1999-09-25",
        "endDate": "1999-09-27",
        "parentId": 1
    }
    ```
    - **Request Attribute Details:**
        * `title`: Title of the goal (it cannot be blank and its maximum size is 255 characters).
        * `description`: Description of the goal (it cannot be blank and its maximum size it's 1000 characters).
        * `startDate`: Starting date of the goal for scheduling it, it must follow the following specifications:
            - It can be null, but not while `endDate` has value.
            - If provided must have the format "yyyy-mm-dd".
            - it cannot be on the past.
            - it cannot be before any ancestor goal `startDate`
            - If the `startDate` is the current date it will cascade the **IN_PROCESS** status to itself and to all the **PAUSED** or **WAITING** ancestors
        * `endDate`: Ending date of the goal for scheduling it, it must follow the following specifications: 
            - It can be null.
            - It must be null if the `startDate` is null.
            - If provided must have the format "yyyy-mm-dd".
            - It must be after the `startDate`.
            - it cannot be after any ancestor goal `endDate`.
        * `parentId`: Numeric identifier of the goal associated as the parent of this goal (it can be null, but if provided must belong to an existing goal).

    - **Response Payload (GoalResponseDto):** This endpoint responses with a **GoalResponseDto** json as the one examplified in the [3.3.1 section](#331-common-response-payload).

* **PATCH** `/goals/{id}` - Updates an existing goal's properties and dates for the authenticated user, excluding parent changes and direct status modifications (the `id` must be equal or greater than 1 and belong to an existing goal that is not in the following statuses [COMPLETED(4), FAILED(5), DISCARDED(6)])."

    - **Request Payload Example (GoalUpdateRequestDto):**
    ``` json
    {
        "title": "Goal Title",
        "description": "Goal description",
        "startDate": "1999-09-25",
        "endDate": "1999-09-27"
    }
    ```
    - **Request Attribute Details:**
        * `title`: Title of the goal (it cannot be blank and its maximum size is 255 characters).
        * `description`: Description of the goal (it cannot be blank and its maximum size it's 1000 characters).
        * `startDate`: Starting date of the goal for scheduling it, it must follow the following specifications:
            - If provided must have the format "yyyy-mm-dd".
            - If the goal is currently **WAITING**:
                * If it has value it cannot be in the past
                * It can be null, but not while `endDate` has value.
                * It cannot be after the `endDate`.
            - If the goal is currently **PAUSED** it cannot be nulled.
            - If the goal is currently **IN_PROCESS** it cannot be changed.
            - it cannot be before any ancestor goal `startDate`
            - it cannot be after any descendant goal `startDate`
            - If it is the current date it will cascade the **IN_PROCESS** status to itself and to all the **PAUSED** or **WAITING** ancestors.
        * `endDate`: Ending date of the goal for scheduling it, it must follow the following specifications: 
            - If provided must have the format "yyyy-mm-dd".
            - If the goal is currently **WAITING**:
                * It can be null.
                * It must be null if the `startDate` is null.
                * If provided it must be after the `startDate`.
            - If the goal is currently **PAUSED** it cannot changed.
            - If the goal is currently **IN_PROCESS** it must be after the `startDate` or null.
            - If provided it cannot be after any ancestor goal `endDate`.
            - If provided it cannot be before any descendant goal `endDate`.

    - **Response Payload (GoalResponseDto):** This endpoint responses with a **GoalResponseDto** json as the one examplified in the [3.3.1 section](#331-common-response-payload).

* **PATCH** `/goals/{id}/status` - Changes the status of a specific goal for the authenticated user, supporting state machine transitions, unpausing, and cascading updates (the `id` must be equal or greater than 1 and belong to an existing goal that is not in the following statuses [COMPLETED(4), FAILED(5), DISCARDED(6)]).

    - **Request Payload Example (GoalStatusChangeRequestDto):**
    ``` json
    {
        "statusId": 2,
        "notes": "Status change notes"
    }
    ```
    - **Request Attribute Details:**
        * `statusId`: Numeric identifier of the status associated with the goal (it cannot be null and will only take the following values [IN_PROGRESS(2), PAUSED(3), COMPLETED(4), FAILED(5), DISCARDED(6)]), it will follow the following rules:
            - If it was on  COMPLETED(4), FAILED(5) or DISCARDED(6) the status cannot be changed any further.
            - It is imposible to transition from one status to the same one.
            - The status WAITING(1) can only be assign at creating and it's imposible to transition from any other status to WAITING(1).
            - If a goal transitions to COMPLETED(4), FAILED(5) or DISCARDED(6) all it descendants that are not already in one of those status automatically transition to DISCARDED(6) with `DOWNWARD_CASCADE` cause.
            - If a goal transition to PAUSED(3) all its descendants that are not COMPLETED(4), FAILED(5) or DISCARDED(6) will automatically transition to PAUSED(3) with `DOWNWARD_CASCADE` cause.
            - If a goal transitions to IN_PROGRESS(2) its start date will be updated to the current one and all its WAITING(1) and PAUSED(3) desendants and ancestors will transition to IN_PROGRESS(2) also updating their start date, with `DOWNWARD_CASCADE` cause for the descendants and `UPWARD_CASCADE` cause for the ancestors.
        * `notes`: Optional notes taken by user when updating the goal status.

    - **Response Payload (GoalResponseDto):** This endpoint responses with a **GoalResponseDto** json as the one examplified in the [3.3.1 section](#331-common-response-payload).

* **DELETE** `/goals/{id}` - Deletes an existing goal and its entire sub-goal tree recursively and all it's status histories for the authenticated user, enforcing ownership rules (the `id` parameter is obligatory, must belong to an existing goal and be greater than zero).

    - This endpoint does not require a request body.

    - No payload is returned in the body after a successful goal deletion.

### 3.4 Error Handling

The API implements a global exception interceptor `GlobalExceptionHandler` that unifies all failed responses under a strict semantic contract based on a domain enum `ErrorCode`, preventing the leakage of internal server stack traces.

#### 3.4.1 General Error Structure (ErrorResponse)

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

#### 3.4.2 Available Error Codes

| Static Code | Description / Root Cause | HTTP Status |
| :--- | :--- | :--- |
| `ERR_VALIDATION_FAILED` | Input validation failures (`@Valid` on DTOs or constraints violations). Includes detailed explanatory messages per field. | 400 Bad Request |
| `ERR_TYPE_MISMATCH` | Malformed URL/Query parameters or strict type coercion failures in parameters (e.g., sending text where a numeric ID is expected). | 400 Bad Request |
| `ERR_PASSWORD_MISMATCH` | The provided password do not match its confirmation on register or update password requests. | 400 Bad Request |
| `INVALID_CURRENT_PASSWORD` | The provided current password is incorrect for an update password request. | 400 Bad Request |
| `ERR_GOAL_START_DATE_VIOLATION` | The subgoal's start date cannot be earlier than its ancestor's start date. | 400 Bad Request |
| `ERR_GOAL_END_DATE_VIOLATION` | The subgoal's end date cannot be later than its ancestor's end date. | 400 Bad Request |
| `ERR_GOAL_STATUS_UNCHANGED` | The goal is already in the requested status. | 400 Bad Request |
| `ERR_GOAL_TERMINAL_STATE` | Cannot modify a goal that has already reached a terminal state.| 400 Bad Request |
| `ERR_INVALID_GOAL_TRANSITION` | The requested goal status transition is not allowed.| 400 Bad Request |
| `ERR_GOAL_STATUS_NOT_FOUND` | The provided goal status identifier is invalid.| 400 Bad Request |
| `ERR_GOAL_START_DATE_IMMUTABLE` | Cannot modify the start date of a goal that is already in progress.| 400 Bad Request |
| `ERR_GOAL_END_DATE_IMMUTABLE` | Cannot modify the end date of a goal that is currently paused.| 400 Bad Request |
| `ERR_GOAL_START_DATE_REQUIRED` | The start date cannot be null for a goal that is in progress or paused.| 400 Bad Request |
| `ERR_GOAL_START_DATE_REQUIRED_WHEN_END_DATE_PRESENT` | The start date cannot be null while the end date is not null.| 400 Bad Request |
| `ERR_GOAL_START_DATE_IN_PAST` | The start date cannot be in the past.| 400 Bad Request |
| `ERR_GOAL_END_DATE_BEFORE_START` | The end date cannot be before the start date.| 400 Bad Request |
| `ERR_INVALID_CAPTCHA` | The provided CAPTCHA validation failed or is invalid.| 400 Bad Request |
| `ERR_UNAUTHORIZED` | The request lacks valid authentication credentials, the token has expired, or the provided access data is incorrect. | 401 Unauthorized |
| `ERR_FORBIDDEN` | The authenticated user attempts to access a resource or perform an action for which they lack sufficient permissions. | 403 Forbidden |
| `ERR_NOT_FOUND` | The requested resource does not exist in the database or is not active in the system. | 404 Not Found |
| `ERR_METHOD_NOT_SUPPORTED` | The requested HTTP method is not supported by the endpoint.| 405 - Method Not Allowed. |
| `ERR_EMAIL_ALREADY_EXISTS` | Occurs when attempting to register a user with an email address that is already occupied by another account. | 409 Conflict |
| `ERR_TOO_MANY_REQUESTS` | The allowed request rate limit has been exceeded for authentication and registration endpoints (`/auth/**`), protecting the system against abuse or brute-force attacks. | 429 Too Many Requests |
| `ERR_INTERNAL_SERVER_ERROR` | Final safety net to capture unexpected system exceptions (connectivity drops, null pointers, or unforeseen infrastructure failures). Full traceability remains hidden behind the `trackingId`. | 500 Internal Server Error |

## 4 Scheduled Jobs

The application includes automated background components scheduled to run daily at midnight (`0 0 0 * * *`) to maintain system state, goal lifecycles, and database consistency:

- **`GoalActivationJob`**: Scans for waiting goals whose start date has arrived or passed, automatically transitioning them to `IN_PROGRESS` and generating their corresponding status history record with the **`AUTO_ACTIVATION`** cause and all its descendants and ancestors that are `WAITING` or `PAUSED` will transition automatically to `IN_PROGRESS`, updating their start date to the current one, with `DOWNWARD_CASCADE` cause for the descendants and `UPWARD_CASCADE` cause for the ancestors.
- **`GoalExpirationJob`**: Evaluates expired `IN_PROGRESS` goals from bottom to top (hierarchical post-order) and automatically marks them as `FAILED`, registering the status change history entry with the correct **`AUTO_FAILURE`** cause and all its descendants that were not on a terminal status already (`COMPLETED`, `FAILED` or `DISCARDED`) will transition automatically to `DISCARDED` with the cause being `DOWNWARD_CASCADE`.
- **`GuestCleanupJob`**: Deletes all expired guest users along with all of their associated goals and status histories.

## 5. Testing

### 5.1 Configuring Testcontainers

#### 5.1.1 Linux Configuration

To enable Testcontainers to run using the local Docker installation, run the following command:
``` bash
echo "api.version=1.44" > ~/.docker-java.properties
```

#### 5.1.2 VS Code Configuration

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

### 5.2 Running the Tests

- **Run the full test suite:** Runs all unit and integration tests using the `test` configuration profile, utilizing Testcontainers for the database connection:

``` bash
./mvnw test
```

- **Run a specific test suite:** Runs the tests of a specific file:

``` bash
./mvnw test -Dtest=NameOfTheTestFileWithOutExtention
```
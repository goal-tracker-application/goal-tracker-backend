# goal-tracker-backend

[Read this README in English (Inglés)](README.md)

Servicio de API REST backend para Goal Tracker construido con Spring Boot, PostgreSQL y Flyway. Gestiona la autenticación de usuarios (JWT), la administración segura de cuentas, migraciones de bases de datos versionadas, árboles de objetivos recursivos en cascada y tareas de mantenimiento automatizadas.

## 1. Configuración y Variables de Entorno

La aplicación se configura mediante propiedades en el archivo de configuración y se alimenta principalmente de variables de entorno para garantizar la seguridad y flexibilidad en distintos entornos:

- **Base de Datos (PostgreSQL):** Utiliza `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`. Su política de esquema está configurada en `validate` (confiando en **Flyway** para las migraciones en `classpath:db/migration`).
- **Redis (Caché y Rate Limiting):** Configurado a través de `REDIS_HOST` y `REDIS_PORT` para establecer la conexión para la persistencia de estados (utilizando por defecto `localhost` y `6379` para entornos locales).
- **Seguridad y JWT:** El secreto para la firma de los tokens y su tiempo de expiración se configuran a través de `APP_SECURITY_JWT_SECRET` y `APP_SECURITY_JWT_EXPIRATION_MS` (con un valor por defecto seguro para pruebas locales).
- **Rate Limiting (Protección IP):** Se gestiona bajo el prefijo `app.security.rate-limit` y permite configurar la capacidad máxima, la tasa de recarga y su periodicidad mediante variables de entorno:
  - `APP_SECURITY_RATE_LIMIT_CAPACITY` (por defecto: `5` tokens).
  - `APP_SECURITY_RATE_LIMIT_REFILL_TOKENS` (por defecto: `5` tokens).
  - `APP_SECURITY_RATE_LIMIT_REFILL_DURATION_MINUTES` (por defecto: `1` minuto).
- **CORS:** Los orígenes permitidos para peticiones de clientes externos se gestionan mediante `APP_SECURITY_CORS_ALLOWED_ORIGINS`.
- **Ruta Base (Context Path):** Todos los endpoints de la API se exponen bajo el prefijo `/api`.
- **Perfiles:** Se pueden activar perfiles específicos como `dev` (que habilita logs detallados de SQL y consultas en Hibernate para debugging) o `prod`.

## 2. Seguridad

### 2.1 Spring Security

La aplicación está asegurada mediante Spring Security, otorgando acceso público explícito a los endpoints operacionales (como las verificaciones de salud del Actuator) y a las rutas de autenticación, mientras se protegen los endpoints privados de la API.

### 2.2 JWT (JSON Web Tokens)

La autenticación sin estado (*stateless*) se implementa mediante JWT. Una vez que un usuario inicia sesión o se registra exitosamente, se emite un token que debe incluirse en la cabecera `Authorization` (`Bearer <token>`) para acceder a los recursos protegidos.

### 2.3 CORS (Intercambio de Orígenes Cruzados)

CORS está configurado globalmente para permitir que clientes autorizados (como aplicaciones de frontend) se comuniquen de forma segura con los endpoints de la API.

### 2.4 Rate Limiting (Protección basada en IP)

Para proteger los endpoints de autenticación y registro (`/auth/**`) contra ataques de fuerza bruta y abusos automatizados, la aplicación implementa un mecanismo distribuido de limitación de tasa (*rate-limiting*) basado en IP respaldado por **Redis** y **Bucket4j**. Las solicitudes que superan el umbral configurado son interceptadas y bloqueadas con una respuesta **HTTP 429 (Too Many Requests)**.

## 3. Endpoints

### 3.1 Actuator para la indicación de salud de la base de datos (Público)

Se puede acceder al siguiente endpoint para verificar la salud del sistema, específicamente la conexión de la base de datos:

* **GET** `/actuator/health/` - Verifica el estado de la conexión a la base de datos y devuelve la siguiente respuesta JSON formateada:

    - **Ejemplo JSON:**
    ``` json
    {
        "application": "Goal Tracker API",
        "database": "UP",
        "timestamp": "2001-12-30T01:01:01"
    }
    ```
    
    - **Detalles de los atributos:**

        * `application`: Nombre de la aplicación.
        * `database`: Estado de la conexión a la base de datos, puede ser `UP` o `DOWN`.
        * `timestamp`: Marca de tiempo actual en formato `yyyy-mm-ddTHH:MM:SS`.

### 3.2 Autenticación (Pública)

#### 3.2.1 Payload de respuesta común:

Este payload de respuesta es común para todos los endpoints con payload de respuesta en la **API** de autenticación:

- **Ejemplo de Payload de Respuesta (AuthResponse):**
```json
{
    "token": "eyJhbGciOiJIUzI1NiIsIn...",
    "email": "test@example.com",
    "isGuest": false
}
```
- **Detalles de los atributos de respuesta:**
    * `token`: Token JWT de acceso firmado.
    * `email`: Correo electrónico del usuario registrado.
    * `isGuest`: Indicador de si el usuario es invitado (`false` para usuarios regulares).

#### 3.2.2 Endpoints
* **POST** `/auth/register` - Valida los datos de registro, crea una cuenta de usuario regular y devuelve un JWT de acceso.
    - **Ejemplo de Payload de Solicitud:**
    ```json
    {
        "email": "test@example.com",
        "password": "SecurePassword123!",
        "confirmPassword": "SecurePassword123!"
    }
    ```
    - **Detalles de los atributos de solicitud:**
        * `email`: Correo electrónico del usuario (debe ser válido y único).
        * `password`: Contraseña de la cuenta (debe cumplir con las políticas de seguridad: de 10 a 72 caracteres, al menos una letra mayúscula, un número y un carácter especial).
        * `confirmPassword`: Confirmación de la contraseña (debe coincidir exactamente con `password`).

    - **Payload de Respuesta (AuthResponse)**: Este endpoint responde con un JSON **AuthResponse** como el ejemplificado en la [sección 3.2.1](#321-payload-de-respuesta-comun).

* **POST** `/auth/login` - Autentica a un usuario mediante su correo electrónico y contraseña, devolviendo un token JWT de acceso firmado.
    - **Ejemplo de Payload de Solicitud:**
    ```json
    {
        "email": "test@example.com",
        "password": "SecurePassword123!"
    }
    ```
    - **Detalles de los atributos de solicitud:**
        * `email`: Correo electrónico registrado del usuario.
        * `password`: Contraseña actual del usuario.

    - **Payload de Respuesta (AuthResponse)**: Este endpoint responde con un JSON **AuthResponse** como el ejemplificado en la [sección 3.2.1](#321-payload-de-respuesta-comun).

* **POST** `/auth/guest` - Aprovisiona una cuenta temporal de usuario invitado y devuelve un JWT de acceso.
    
    - Este endpoint no requiere un cuerpo de solicitud.

    - **Payload de Respuesta (AuthResponse)**: Este endpoint responde con un JSON **AuthResponse** como el ejemplificado en la [sección 3.2.1](#321-payload-de-respuesta-comun).

* **PATCH** `/auth/password` - Permite a un usuario autenticado cambiar su contraseña validando la actual. Devuelve una respuesta HTTP 204 No Content con el cuerpo vacío en caso de éxito.
    - **Ejemplo de Payload de Solicitud:**
    ```json
    {
        "currentPassword": "CurrentPass123!",
        "newPassword": "NewSecurePass123!",
        "confirmNewPassword": "NewSecurePass123!"
    }
    ```
    - **Detalles de los atributos de solicitud:**
        * `currentPassword`: Contraseña actual del usuario para verificación de seguridad.
        * `newPassword`: Nueva contraseña que desea establecer (sujeta a las políticas de complejidad de seguridad: de 10 a 72 caracteres, mayúscula, número y carácter especial).
        * `confirmNewPassword`: Confirmación de la nueva contraseña (debe coincidir con `newPassword`).

    - No se retorna ningún payload en el cuerpo tras una actualización exitosa de contraseña.

### 3.3 Objetivos (Autenticado)

Los endpoints bajo `/goals` requieren un token JWT válido enviado en la cabecera `Authorization` (`Bearer <token>`).

#### 3.3.1 Payload de respuesta común:

Este payload de respuesta es común para varios endpoints con payload de respuesta en la **API** de objetivos:

- **Ejemplo de Payload de Respuesta (GoalResponseDto):**
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
    - **Detalles de los atributos de respuesta:**
        * `id`: Identificador numérico único del objetivo (no puede ser nulo).
        * `title`: Título del objetivo (no puede estar en blanco y su tamaño máximo es de 255 caracteres).
        * `description`: Descripción del objetivo (no puede estar en blanco y su tamaño máximo es de 1000 caracteres).
        * `startDate`: Fecha de inicio del objetivo para su programación (puede ser nula, pero si se proporciona debe tener el formato "yyyy-mm-dd").
        * `endDate`: Fecha de fin del objetivo para su programación (puede ser nula, pero si se proporciona debe tener el formato "yyyy-mm-dd").
        * `statusId`: Identificador numérico del estado asociado al objetivo (no puede ser nulo y solo tomará los siguientes valores [WAITING(1), IN_PROGRESS(2), PAUSED(3), COMPLETED(4), FAILED(5), DISCARDED(6)]).
        * `parentId`: Identificador numérico del objetivo asociado como padre de este objetivo (puede ser nulo, pero si se proporciona debe pertenecer a un objetivo existente).
        * `createdAt`: Marca de tiempo tomada en el momento de creación del registro (no puede ser nula y debe tener el formato "yyyy-mm-ddTHH:MM:SS").
        * `updatedAt`: Marca de tiempo tomada en el momento de la última actualización del registro (puede ser nula, pero si se proporciona debe tener el formato "yyyy-mm-ddTHH:MM:SS").

#### 3.3.2 Endpoints

* **GET** `/goals/roots?status_id={statusId}` - Recupera todos los objetivos raíz pertenecientes al usuario autenticado, con un filtro opcional por ID de estado (el parámetro `status_id` es opcional, pero si se proporciona debe corresponder a un estado válido dentro de la siguiente lista [WAITING(1), IN_PROGRESS(2), PAUSED(3), COMPLETED(4), FAILED(5), DISCARDED(6)]).
    
    - Este endpoint no requiere un cuerpo de solicitud.

    - **Payload de Respuesta (GoalResponseDto[]):** Este endpoint responde con una lista de JSON **GoalResponseDto** como el ejemplificado en la [sección 3.3.1](#331-payload-de-respuesta-comun).

* **GET** `/goals/{id}/tree` - Recupera el árbol jerárquico completo de un objetivo junto con sus subobjetivos para el usuario autenticado (el parámetro `id` es obligatorio, debe pertenecer a un objetivo existente y ser mayor a cero).

    - Este endpoint no requiere un cuerpo de solicitud.

    - **Ejemplo de Payload de Respuesta (GoalTreeResponseDto):**
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
    - **Detalles de los atributos de respuesta:**
        * `id`: Identificador numérico único del objetivo (no puede ser nulo).
        * `title`: Título del objetivo (no puede estar en blanco y su tamaño máximo es de 255 caracteres).
        * `statusId`: Identificador numérico del estado asociado al objetivo (no puede ser nulo y solo tomará los siguientes valores [WAITING(1), IN_PROGRESS(2), PAUSED(3), COMPLETED(4), FAILED(5), DISCARDED(6)]).
        * `startDate`: Fecha de inicio del objetivo para su programación (puede ser nula, pero si se proporciona debe tener el formato "yyyy-mm-dd").
        * `endDate`: Fecha de fin del objetivo para su programación (puede ser nula, pero si se proporciona debe tener el formato "yyyy-mm-dd").
        * `subGoals`: Lista de objetos del tipo **GoalTreeResponseDto** que representan todos los objetivos de los que depende este objetivo; cada subobjetivo puede tener su propia lista de `subGoals`.
    
* **GET** `/goals/{id}/history` - Recupera la cronología histórica de cambios de estado para un objetivo específico perteneciente al usuario autenticado (el parámetro `id` es obligatorio, debe pertenecer a un objetivo existente y ser mayor a cero).

    - Este endpoint no requiere un cuerpo de solicitud.

    - **Ejemplo de Payload de Respuesta (GoalStatusHistoryResponseDto[]):**
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
    - **Detalles de los atributos de respuesta:**
        * `statusId`: Identificador numérico del estado asociado al objetivo (no puede ser nulo y solo tomará los siguientes valores [WAITING(1), IN_PROGRESS(2), PAUSED(3), COMPLETED(4), FAILED(5), DISCARDED(6)]).
        * `cause`: Causa del cambio de estado, solo puede ser una de las siguientes:
            - **CREATION:** Transición automática activada en la creación del objetivo.
            - **USER:** Transición directa activada explícitamente por el usuario.
            - **UPWARD_CASCADE:** Transición automática en cascada ascendente desencadenada por un evento en un objetivo hijo.
            - **DOWNWARD_CASCADE:** Transición automática en cascada descendente desencadenada por un evento en un objetivo padre.
            - **AUTO_ACTIVATION:** Transición automática desencadenada cuando el objetivo inicia automáticamente según su programación.
            - **AUTO_FAILURE:** Transición automática desencadenada cuando el objetivo falla por vencimiento o plazos no cumplidos.
        * `notes`: Notas registradas por el usuario al actualizar el estado del objetivo.
        * `changedAt`: Marca de tiempo del momento del cambio de estado con el formato "yyyy-mm-ddTHH:MM:DD".

* **POST** `/goals` - Crea un nuevo objetivo para el usuario autenticado, soportando la asociación opcional con un objetivo padre y validaciones jerárquicas de fechas.

    - **Ejemplo de Payload de Solicitud (GoalCreateRequestDto):**
    ``` json
    {
        "title": "Goal Title",
        "description": "Goal description",
        "startDate": "1999-09-25",
        "endDate": "1999-09-27",
        "parentId": 1
    }
    ```
    - **Detalles de los atributos de solicitud:**
        * `title`: Título del objetivo (no puede estar en blanco y su tamaño máximo es de 255 caracteres).
        * `description`: Descripción del objetivo (no puede estar en blanco y su tamaño máximo es de 1000 caracteres).
        * `startDate`: Fecha de inicio del objetivo para su programación, debe seguir las siguientes especificaciones:
            - Puede ser nula, pero no mientras `endDate` tenga valor.
            - Si se proporciona debe tener el formato "yyyy-mm-dd".
            - No puede ser una fecha en el pasado.
            - No puede ser anterior a la fecha `startDate` de ningún objetivo ancestro.
            - Si `startDate` es la fecha actual, propagará el estado **IN_PROGRESS** a sí mismo y a todos sus ancestros en **PAUSED** o **WAITING**.
        * `endDate`: Fecha de fin del objetivo para su programación, debe seguir las siguientes especificaciones: 
            - Puede ser nula.
            - Debe ser nula si `startDate` es nula.
            - Si se proporciona debe tener el formato "yyyy-mm-dd".
            - Debe ser posterior a `startDate`.
            - No puede ser posterior a la fecha `endDate` de ningún objetivo ancestro.
        * `parentId`: Identificador numérico del objetivo asociado como padre de este objetivo (puede ser nulo, pero si se proporciona debe pertenecer a un objetivo existente).

    - **Payload de Respuesta (GoalResponseDto):** Este endpoint responde con un JSON **GoalResponseDto** como el ejemplificado en la [sección 3.3.1](#331-payload-de-respuesta-comun).

* **PATCH** `/goals/{id}` - Actualiza las propiedades y fechas de un objetivo existente para el usuario autenticado, excluyendo cambios de padre y modificaciones directas de estado (el `id` debe ser igual o mayor a 1 y pertenecer a un objetivo existente que no se encuentre en los siguientes estados [COMPLETED(4), FAILED(5), DISCARDED(6)]).

    - **Ejemplo de Payload de Solicitud (GoalUpdateRequestDto):**
    ``` json
    {
        "title": "Goal Title",
        "description": "Goal description",
        "startDate": "1999-09-25",
        "endDate": "1999-09-27"
    }
    ```
    - **Detalles de los atributos de solicitud:**
        * `title`: Título del objetivo (no puede estar en blanco y su tamaño máximo es de 255 caracteres).
        * `description`: Descripción del objetivo (no puede estar en blanco y su tamaño máximo es de 1000 caracteres).
        * `startDate`: Fecha de inicio del objetivo para su programación, debe seguir las siguientes especificaciones:
            - Si se proporciona debe tener el formato "yyyy-mm-dd".
            - Si el objetivo está actualmente en **WAITING**:
                * Si tiene valor, no puede estar en el pasado.
                * Puede ser nula, pero no mientras `endDate` tenga valor.
                * No puede ser posterior a `endDate`.
            - Si el objetivo está actualmente en **PAUSED**, no puede anularse (dejarse en nulo).
            - Si el objetivo está actualmente en **IN_PROGRESS**, no puede ser modificada.
            - No puede ser anterior a la fecha `startDate` de ningún objetivo ancestro.
            - No puede ser posterior a la fecha `startDate` de ningún objetivo descendiente.
            - Si es la fecha actual, propagará en cascada el estado **IN_PROGRESS** a sí mismo y a todos los ancestros en **PAUSED** o **WAITING**.
        * `endDate`: Fecha de fin del objetivo para su programación, debe seguir las siguientes especificaciones: 
            - Si se proporciona debe tener el formato "yyyy-mm-dd".
            - Si el objetivo está actualmente en **WAITING**:
                * Puede ser nula.
                * Debe ser nula si `startDate` es nula.
                * Si se proporciona, debe ser posterior a `startDate`.
            - Si el objetivo está actualmente en **PAUSED**, no puede cambiarse.
            - Si el objetivo está actualmente en **IN_PROGRESS**, debe ser posterior a `startDate` o nula.
            - Si se proporciona, no puede ser posterior a la fecha `endDate` de ningún objetivo ancestro.
            - Si se proporciona, no puede ser anterior a la fecha `endDate` de ningún objetivo descendiente.

    - **Payload de Respuesta (GoalResponseDto):** Este endpoint responde con un JSON **GoalResponseDto** como el ejemplificado en la [sección 3.3.1](#331-payload-de-respuesta-comun).

* **PATCH** `/goals/{id}/status` - Cambia el estado de un objetivo específico para el usuario autenticado, soportando transiciones de máquina de estados, reanudaciones y actualizaciones en cascada (el `id` debe ser igual o mayor a 1 y pertenecer a un objetivo existente que no esté en los siguientes estados [COMPLETED(4), FAILED(5), DISCARDED(6)]).

    - **Ejemplo de Payload de Solicitud (GoalStatusChangeRequestDto):**
    ``` json
    {
        "statusId": 2,
        "notes": "Status change notes"
    }
    ```
    - **Detalles de los atributos de solicitud:**
        * `statusId`: Identificador numérico del estado asociado al objetivo (no puede ser nulo y solo tomará los siguientes valores [IN_PROGRESS(2), PAUSED(3), COMPLETED(4), FAILED(5), DISCARDED(6)]), siguiendo estas reglas:
            - Si estaba en COMPLETED(4), FAILED(5) o DISCARDED(6), el estado no se puede cambiar más.
            - Es imposible realizar una transición de un estado a sí mismo.
            - El estado WAITING(1) solo se puede asignar al momento de la creación y es imposible transicionar desde cualquier otro estado a WAITING(1).
            - Si un objetivo transiciona a COMPLETED(4), FAILED(5) o DISCARDED(6), todos sus descendientes que no estén ya en un estado terminal transicionan automáticamente a DISCARDED(6) con causa `DOWNWARD_CASCADE`.
            - Si un objetivo transiciona a PAUSED(3), todos sus descendientes que no estén en COMPLETED(4), FAILED(5) o DISCARDED(6) transicionarán automáticamente a PAUSED(3) con causa `DOWNWARD_CASCADE`.
            - Si un objetivo transiciona a IN_PROGRESS(2), su fecha de inicio se actualizará a la fecha actual y todos sus descendientes y ancestros en WAITING(1) y PAUSED(3) transicionarán también a IN_PROGRESS(2) actualizando su fecha de inicio, con causa `DOWNWARD_CASCADE` para descendientes y `UPWARD_CASCADE` para ancestros.
        * `notes`: Notas opcionales ingresadas por el usuario al actualizar el estado del objetivo.

    - **Payload de Respuesta (GoalResponseDto):** Este endpoint responde con un JSON **GoalResponseDto** como el ejemplificado en la [sección 3.3.1](#331-payload-de-respuesta-comun).

* **DELETE** `/goals/{id}` - Elimina un objetivo existente y todo su árbol de subobjetivos de forma recursiva, así como todo su historial de estados para el usuario autenticado, aplicando las reglas de propiedad (el parámetro `id` es obligatorio, debe pertenecer a un objetivo existente y ser mayor a cero).

    - Este endpoint no requiere un cuerpo de solicitud.

    - No se retorna ningún payload en el cuerpo tras una eliminación exitosa de un objetivo.

### 3.4 Gestión de Errores

La API implementa un interceptor global de excepciones `GlobalExceptionHandler` que unifica todas las respuestas fallidas bajo un contrato semántico estricto basado en un enumerado de dominio `ErrorCode`, evitando la filtración de trazas de la pila interna del servidor.

#### 3.4.1 Estructura General de Error (ErrorResponse)

Todas las respuestas de error de la API están estandarizadas bajo el Objeto de Transferencia de Datos `ErrorResponse`. Ante cualquier fallo, el sistema genera automáticamente un identificador de seguimiento único `trackingId` basado en un UUID v4 prefijado con ERR-. Este identificador se replica en los registros de auditoría y del servidor para permitir una rápida trazabilidad y soporte operativo.

- **Ejemplo JSON**

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
- **Detalles de los atributos:**
    * code: Un identificador de cadena legible por máquina que representa el tipo o categoría específica de error, utilizado por las aplicaciones cliente para gestionar los errores de forma programática.
    * message: Un resumen legible y amigable para el usuario que describe qué salió mal en un lenguaje sencillo.
    * details: Una matriz (array) de mensajes de error específicos que proporcionan información detallada sobre los fallos de validación de campos individuales o las violaciones de restricciones (es un campo opcional, si no hay detalles el JSON no incluye el campo).
    * trackingId: Un identificador único (UUID) asignado a la ocurrencia específica del error, utilizado para el registro (logging), la depuración (debugging) y el rastreo del error en los registros del servidor.

> **Comportamiento de Serialización:**  
> La propiedad `details` se incluye como un arreglo de cadenas explicativas o especificaciones de validación por campo. Si no existe información complementaria sobre el fallo, la clave `details` se omite automáticamente del cuerpo JSON enviado al cliente.

#### 3.4.2 Códigos de Error Disponibles

| Código Estático | Descripción / Causa Raíz | Estado HTTP |
| :--- | :--- | :--- |
| `ERR_VALIDATION_FAILED` | Fallos de validación de entrada (`@Valid` en DTOs o violaciones de restricciones). Incluye mensajes detallados explicativos por campo. | 400 Bad Request |
| `ERR_TYPE_MISMATCH` | Parámetros de URL/Query mal formados o fallas de coerción de tipos en parámetros (ej., enviar texto donde se espera un ID numérico). | 400 Bad Request |
| `ERR_PASSWORD_MISMATCH` | La contraseña proporcionada no coincide con su confirmación en las solicitudes de registro o actualización de contraseña. | 400 Bad Request |
| `INVALID_CURRENT_PASSWORD` | La contraseña actual proporcionada es incorrecta para una solicitud de actualización de contraseña. | 400 Bad Request |
| `ERR_GOAL_START_DATE_VIOLATION` | La fecha de inicio del subobjetivo no puede ser anterior a la fecha de inicio de su ancestro. | 400 Bad Request |
| `ERR_GOAL_END_DATE_VIOLATION` | La fecha de fin del subobjetivo no puede ser posterior a la fecha de fin de su ancestro. | 400 Bad Request |
| `ERR_GOAL_STATUS_UNCHANGED` | El objetivo ya se encuentra en el estado solicitado. | 400 Bad Request |
| `ERR_GOAL_TERMINAL_STATE` | No se puede modificar un objetivo que ya ha alcanzado un estado terminal.| 400 Bad Request |
| `ERR_INVALID_GOAL_TRANSITION` | La transición de estado solicitada para el objetivo no está permitida.| 400 Bad Request |
| `ERR_GOAL_STATUS_NOT_FOUND` | El identificador de estado de objetivo proporcionado no es válido.| 400 Bad Request |
| `ERR_GOAL_START_DATE_IMMUTABLE` | No se puede modificar la fecha de inicio de un objetivo que ya está en progreso.| 400 Bad Request |
| `ERR_GOAL_END_DATE_IMMUTABLE` | No se puede modificar la fecha de fin de un objetivo que está actualmente pausado.| 400 Bad Request |
| `ERR_GOAL_START_DATE_REQUIRED` | La fecha de inicio no puede ser nula para un objetivo que está en progreso o pausado.| 400 Bad Request |
| `ERR_GOAL_START_DATE_REQUIRED_WHEN_END_DATE_PRESENT` | La fecha de inicio no puede ser nula cuando la fecha de fin no es nula.| 400 Bad Request |
| `ERR_GOAL_START_DATE_IN_PAST` | La fecha de inicio no puede estar en el pasado.| 400 Bad Request |
| `ERR_GOAL_END_DATE_BEFORE_START` | La fecha de fin no puede ser anterior a la fecha de inicio.| 400 Bad Request |
| `ERR_UNAUTHORIZED` | La petición carece de credenciales de autenticación válidas, el token ha expirado o los datos de acceso proporcionados son incorrectos. | 401 Unauthorized |
| `ERR_FORBIDDEN` | El usuario autenticado intenta acceder a un recurso o realizar una acción para la cual carece de permisos suficientes. | 403 Forbidden |
| `ERR_NOT_FOUND` | El recurso solicitado no existe en la base de datos o no se encuentra activo en el sistema. | 404 Not Found |
| `ERR_METHOD_NOT_SUPPORTED` | El método HTTP solicitado no está soportado por el endpoint.| 405 Method Not Allowed |
| `ERR_EMAIL_ALREADY_EXISTS` | Ocurre al intentar registrar un usuario con una dirección de correo electrónico que ya está ocupada por otra cuenta. | 409 Conflict |
| `ERR_TOO_MANY_REQUESTS` | Se ha superado el límite de peticiones permitidas (rate-limiting) para los endpoints de autenticación y registro (`/auth/**`), protegiendo al sistema contra abusos o ataques de fuerza bruta. | 429 Too Many Requests |
| `ERR_INTERNAL_SERVER_ERROR` | Red de seguridad final para capturar excepciones inesperadas del sistema (caídas de conectividad, punteros nulos o fallas imprevistas de infraestructura). La trazabilidad completa permanece oculta detrás del `trackingId`. | 500 Internal Server Error |

## 4. Tareas Programadas (Scheduled Jobs)

La aplicación incluye componentes automatizados en segundo plano programados para ejecutarse diariamente a medianoche (`0 0 0 * * *`) para mantener el ciclo de vida de los objetivos y garantizar la consistencia:

- **`GoalActivationJob`**: Escanea objetivos en espera (`WAITING`) cuya fecha de inicio ha llegado o pasado, transicionándolos automáticamente a `IN_PROGRESS` y generando su correspondiente registro de historial de estado con la causa **`AUTO_ACTIVATION`**. Todos sus descendientes y ancestros que se encuentren en `WAITING` o `PAUSED` transicionarán automáticamente a `IN_PROGRESS`, actualizando su fecha de inicio a la fecha actual, con causa `DOWNWARD_CASCADE` para descendientes y `UPWARD_CASCADE` para ancestros.
- **`GoalExpirationJob`**: Evalúa objetivos en progreso (`IN_PROGRESS`) vencidos de abajo hacia arriba (orden jerárquico post-orden) y los marca automáticamente como `FAILED`, registrando la entrada de cambio de estado con la causa **`AUTO_FAILURE`**. Todos sus descendientes que no estuvieran ya en un estado terminal (`COMPLETED`, `FAILED` o `DISCARDED`) transicionarán automáticamente a `DISCARDED` con causa `DOWNWARD_CASCADE`.

## 5. Pruebas

### 5.1 Configuración de Testcontainers

#### 5.1.1 Configuración en Linux

Para permitir que Testcontainers se ejecute utilizando la instalación local de Docker, ejecute el siguiente comando:
``` bash
echo "api.version=1.44" > ~/.docker-java.properties
```

#### 5.1.2 Configuración en VS Code

Para habilitar Testcontainers en **VS Code**, agregue la siguiente configuración en la raíz del proyecto:
- Cree la carpeta oculta **.vscode**:
``` bash
mkdir .vscode
```

- Cree el archivo **settings.json**:
``` bash
touch settings.json
```

- Abra o edite el archivo:
``` bash
nano settings.json
```

- Agregue este contenido al archivo, guarde con **Ctrl+O** y cierre el archivo con **Ctrl+X**:
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

### 5.2 Ejecución de las Pruebas

- **Ejecutar la suite completa de pruebas:** Ejecuta todas las pruebas unitarias y de integración utilizando el perfil de configuración `test`, empleando Testcontainers para la conexión a la base de datos:

``` bash
./mvnw test
```

- **Ejecutar una suite de pruebas específica:** Ejecuta las pruebas de un archivo específico:

``` bash
./mvnw test -Dtest=NombreDelArchivoSinExtension
```
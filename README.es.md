# goal-tracker-backend

[Leer este README en inglés (English)](README.md)

Servicio de API REST backend para Goal Tracker construido con Spring Boot, PostgreSQL y Flyway. Gestiona la autenticación de usuarios (JWT), la administración segura de cuentas, migraciones de bases de datos versionadas, árboles de objetivos recursivos en cascada y tareas de mantenimiento automatizadas."

## 1. Configuración y Variables de Entorno

La aplicación se configura mediante propiedades en el archivo de configuración y se alimenta principalmente de variables de entorno para garantizar la seguridad y flexibilidad en distintos entornos:

- **Base de Datos (PostgreSQL):** Utiliza `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`. Su política de esquema está configurada en `validate` (confiando en **Flyway** para las migraciones en `classpath:db/migration`).
- **Seguridad y JWT:** El secreto para la firma de los tokens y su tiempo de expiración se configuran a través de `APP_SECURITY_JWT_SECRET` y `APP_SECURITY_JWT_EXPIRATION_MS` (con un valor por defecto seguro para pruebas locales).
- **Rate Limiting (Protección IP):** Se gestiona bajo el prefijo `app.security.rate-limit` y permite configurar la capacidad máxima, la tasa de recarga y su periodicidad mediante variables de entorno:
  - `APP_SECURITY_RATE_LIMIT_CAPACITY` (por defecto: `5` tokens).
  - `APP_SECURITY_RATE_LIMIT_REFILL_TOKENS` (por defecto: `5` tokens).
  - `APP_SECURITY_RATE_LIMIT_REFILL_DURATION_MINUTES` (por defecto: `1` minuto).
- **CORS:** Los orígenes permitidos para peticiones de clientes externos se gestionan mediante `APP_SECURITY_CORS_ALLOWED_ORIGINS`.
- **Ruta Base (Context Path):** Todos los endpoints de la API se exponen bajo el prefijo `/api`.
- **Perfiles:** Se pueden activar perfiles específicos como `dev` (que habilita logs detallados de SQL y consultas en Hibernate para debugging) o `prod`.

## 2. Seguridad

### Spring Security

La aplicación está asegurada mediante Spring Security, otorgando acceso público explícito a los endpoints operacionales (como las verificaciones de salud del Actuator) y a las rutas de autenticación, mientras se protegen los endpoints privados de la API.

### JWT (JSON Web Tokens)

La autenticación sin estado (*stateless*) se implementa mediante JWT. Una vez que un usuario inicia sesión o se registra exitosamente, se emite un token que debe incluirse en la cabecera `Authorization` (`Bearer <token>`) para acceder a los recursos protegidos.

### CORS (Intercambio de Orígenes Cruzados)

CORS está configurado globalmente para permitir que clientes autorizados (como aplicaciones de frontend) se comuniquen de forma segura con los endpoints de la API.

### Rate Limiting (Protección basada en IP)

Para proteger los endpoints de autenticación y registro (`/auth/**`) contra ataques de fuerza bruta y abusos automatizados, la aplicación implementa un mecanismo distribuido de limitación de tasa (*rate-limiting*) basado en IP respaldado por **Redis** y **Bucket4j**. Las solicitudes que superan el umbral configurado son interceptadas y bloqueadas con una respuesta **HTTP 429 (Too Many Requests)**.

## 3. Puntos de acceso

### Actuator para la indicación de salud de la base de datos (Publico)

Se puede acceder al siguiente endpoint para verificar la salud del sistema, específicamente la conexión de la base de datos:

* **GET** `/actuator/health/` - Verifica el estado de la conexión a la base de datos y devuelve la siguiente respuesta JSON formateada:

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

### Autenticación (Pública)

* **POST** `/auth/register` - Valida los datos de registro, crea una cuenta de usuario regular y devuelve un JWT de acceso.
    - **Request Payload Example:**
    ```json
    {
        "email": "test@example.com",
        "password": "SecurePassword123!",
        "confirmPassword": "SecurePassword123!"
    }
    ```
    - **Request Attribute Details:**
        * `email`: Correo electrónico del usuario (debe ser válido y único).
        * `password`: Contraseña de la cuenta (debe cumplir con las políticas de seguridad: de 10 a 72 caracteres, al menos una letra mayúscula, un número y un carácter especial).
        * `confirmPassword`: Confirmación de la contraseña (debe coincidir exactamente con `password`).

    - **Response Payload Example:**
    ```json
    {
        "token": "eyJhbGciOiJIUzI1NiIsIn...",
        "email": "test@example.com",
        "isGuest": false
    }
    ```
    - **Response Attribute Details:**
        * `token`: Token JWT de acceso firmado.
        * `email`: Correo electrónico del usuario registrado.
        * `isGuest`: Indicador de si el usuario es invitado (`false` para usuarios regulares).

* **POST** `/auth/login` - Autentica a un usuario mediante su correo electrónico y contraseña, devolviendo un token JWT de acceso.
    - **Request Payload Example:**
    ```json
    {
        "email": "test@example.com",
        "password": "SecurePassword123!"
    }
    ```
    - **Request Attribute Details:**
        * `email`: Correo electrónico registrado del usuario.
        * `password`: Contraseña actual del usuario.

    - **Response Payload Example:**
    ```json
    {
        "token": "eyJhbGciOiJIUzI1NiIsIn...",
        "email": "test@example.com",
        "isGuest": false
    }
    ```
    - **Response Attribute Details:**
        * `token`: Token JWT de acceso firmado.
        * `email`: Correo electrónico del usuario.
        * `isGuest`: Indicador de si el usuario es invitado.

* **POST** `/auth/guest` - Aprovisiona una cuenta temporal de usuario invitado y devuelve un JWT de acceso.
    - Este endpoint no requiere un cuerpo de solicitud 

    - **Response Payload Example:**
    ```json
    {
        "token": "eyJhbGciOiJIUzI1NiIsIn...",
        "email": "guest_uuid@goaltracker.local",
        "isGuest": true
    }
    ```
    - **Response Attribute Details:**
        * `token`: Token JWT de acceso temporal para la sesión de invitado.
        * `email`: Dirección de correo electrónico temporal generada automáticamente.
        * `isGuest`: Indicador de si el usuario es invitado (`true` para sesiones de invitado).


* **PATCH** `/auth/password` - Permite a un usuario autenticado cambiar su contraseña validando la actual. Devuelve una respuesta HTTP 204 No Content sin cuerpo en caso de éxito.
    - **Request Payload Example:**
    ```json
    {
        "currentPassword": "CurrentPass123!",
        "newPassword": "NewSecurePass123!",
        "confirmNewPassword": "NewSecurePass123!"
    }
    ```
    - **Request Attribute Details:**
        * `currentPassword`: Contraseña actual del usuario para verificación de seguridad.
        * `newPassword`: Nueva contraseña que desea establecer (sujeta a las políticas de complejidad de seguridad: de 10 a 72 caracteres, mayúscula, número y carácter especial).
        * `confirmNewPassword`: Confirmación de la nueva contraseña (debe coincidir con `newPassword`).

    - No se retorna ningún payload en el cuerpo tras una actualización exitosa de contraseña.


### Gestión de Errores

La API implementa un interceptor global de excepciones `GlobalExceptionHandler` que unifica todas las respuestas fallidas bajo un contrato semántico estricto basado en un enumerado de dominio `ErrorCode`, evitando la filtración de trazas de la pila interna del servidor.

#### Estructura General de Error (ErrorResponse)

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
- **Attribute details:**
    * **code**: Un identificador de cadena legible por máquina que representa el tipo o categoría específica de error, utilizado por las aplicaciones cliente para gestionar los errores de forma programática.
    * **message**: Un resumen legible y amigable para el usuario que describe qué salió mal en un lenguaje sencillo.
    * **details**: Una matriz (array) de mensajes de error específicos que proporcionan información detallada sobre los fallos de validación de campos individuales o las violaciones de restricciones (es un campo opcional, si no hay detalles el JSON no lo incluye).
    * **trackingId**: Un identificador único (UUID) asignado a la ocurrencia específica del error, utilizado para el registro (logging), la depuración (debugging) y el rastreo del error en los registros del servidor.

> **Comportamiento de Serialización:**  
> La propiedad `details` se incluye como un arreglo de cadenas explicativas o especificaciones de validación por campo. Si no existe información complementaria sobre el fallo, la clave `details` se omite automáticamente del cuerpo JSON enviado al cliente.

#### Códigos de Error Disponibles

| Código Estático | Descripción / Causa Raíz | Estado HTTP |
| :--- | :--- | :--- |
| `ERR_VALIDATION_FAILED` | Fallos de validación de entrada (`@Valid` en DTOs o violaciones de restricciones). Incluye mensajes detallados explicativos por campo. | 400 Bad Request |
| `ERR_TYPE_MISMATCH` | Parámetros de URL/Query mal formados o fallas estipuladas de coerción de tipos en parámetros (ej., enviar texto donde se espera un ID numérico). | 400 Bad Request |
| `ERR_PASSWORD_MISMATCH` | La contraseña proporcionada no coincide con su confirmación en las solicitudes de registro o actualización de contraseña. | 400 Bad Request |
| `INVALID_CURRENT_PASSWORD` | La contraseña actual proporcionada es incorrecta para una solicitud de actualización de contraseña. | 400 Bad Request |
| `ERR_UNAUTHORIZED` | La petición carece de credenciales de autenticación válidas, el token ha expirado o los datos de acceso proporcionados son incorrectos. | 401 Unauthorized |
| `ERR_FORBIDDEN` | El usuario autenticado intenta acceder a un recurso o realizar una acción para la cual carece de permisos suficientes. | 403 Forbidden |
| `ERR_NOT_FOUND` | El recurso solicitado no existe en la base de datos o no se encuentra activo en el sistema. | 404 Not Found |
| `ERR_EMAIL_ALREADY_EXISTS` | Ocurre al intentar registrar un usuario con una dirección de correo electrónico que ya está ocupada por otra cuenta. | 409 Conflict |
| `ERR_DUPLICATE_GOAL_LOG` | Ocurre al intentar crear una entrada de progreso (check-in) para una meta en una fecha que ya tiene un registro existente. | 409 Conflict |
| `ERR_INVALID_GOAL_HIERARCHY` | Ocurre cuando la jerarquía de metas no es válida (ej., una meta no puede ser su propio padre o generar dependencias circulares). | 409 Conflict |
| `ERR_GOAL_DATE_RANGE_INVALID` | Ocurre cuando la fecha de inicio es posterior a la fecha de finalización de una meta. | 422 Unprocessable Entity |
| `ERR_TOO_MANY_REQUESTS` | Se ha superado el límite de peticiones permitidas (rate-limiting) para los endpoints de autenticación y registro (`/auth/**`), protegiendo al sistema contra abusos o ataques de fuerza bruta. | 429 Too Many Requests |
| `ERR_INTERNAL_SERVER_ERROR` | Red de seguridad final para capturar excepciones inesperadas del sistema (caídas de conectividad, punteros nulos o fallas imprevistas de infraestructura). La trazabilidad completa permanece oculta detrás del `trackingId`. | 500 Internal Server Error |

## 4. Pruebas

### Configuración de Testcontainers

#### Configuración en Linux

Para permitir que Testcontainers se ejecute utilizando la instalación local de Docker, ejecute el siguiente comando:
``` bash
echo "api.version=1.44" > ~/.docker-java.properties
```

#### Configuración en VS Code

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

### Ejecución de las pruebas

- **Ejecutar la suite completa de pruebas:** Ejecuta todas las pruebas unitarias y de integración utilizando el perfil de configuración `test`, empleando Testcontainers para la conexión a la base de datos:
``` bash
./mvnw test
```

- **Ejecutar una suite de pruebas específica:** Ejecuta las pruebas de un archivo específico:
``` bash
./mvnw test -Dtest=NombreDelArchivoSinExtension
```
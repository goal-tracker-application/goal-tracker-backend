# goal-tracker-backend

[Leer este README en inglés (English)](README.md)

Servicio de API REST backend para Goal Tracker construido con Spring Boot, PostgreSQL y Flyway. Gestiona la autenticación de usuarios (JWT), la administración segura de cuentas, migraciones de bases de datos versionadas, árboles de objetivos recursivos en cascada y tareas de mantenimiento automatizadas."

## 1. Gestión de Errores

La API implementa un interceptor global de excepciones `GlobalExceptionHandler` que unifica todas las respuestas fallidas bajo un contrato semántico estricto basado en un enumerado de dominio `ErrorCode`, evitando la filtración de trazas de la pila interna del servidor.

### Estructura General de Error (ErrorResponse)

Todas las respuestas de error de la API están estandarizadas bajo el Objeto de Transferencia de Datos `ErrorResponse`. Ante cualquier fallo, el sistema genera automáticamente un identificador de seguimiento único `trackingId` basado en un UUID v4 prefijado con ERR-. Este identificador se replica en los registros de auditoría y del servidor para permitir una rápida trazabilidad y soporte operativo.

#### Ejemplo JSON

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

#### Definición en TypeScript

Para la integración con el frontend (por ejemplo, en clientes de TypeScript/React o Angular), el contrato de respuesta de errores se modela de la siguiente manera:

``` typescript
export interface ErrorResponse {
  code: string;
  message: string;
  details?: string[];
  trackingId: string;
}
```

> **Comportamiento de Serialización:**  
> La propiedad `details` se incluye como un arreglo de cadenas explicativas o especificaciones de validación por campo. Si no existe información complementaria sobre el fallo, la clave `details` se omite automáticamente del cuerpo JSON enviado al cliente gracias a la regla de serialización `@JsonInclude(JsonInclude.Include.NON_EMPTY)`.

### Códigos de Error Disponibles

| Código Estático | Descripción / Causa Raíz | Estado HTTP |
| :--- | :--- | :--- |
| `ERR_VALIDATION_FAILED` | Fallos de validación de entrada (`@Valid` en DTOs o violaciones de restricciones). Incluye mensajes detallados explicativos por campo. | 400 Bad Request |
| `ERR_TYPE_MISMATCH` | Parámetros de URL/Query mal formados o fallas estipuladas de coerción de tipos en parámetros (ej., enviar texto donde se espera un ID numérico). | 400 Bad Request |
| `ERR_UNAUTHORIZED` | La petición carece de credenciales de autenticación válidas, el token ha expirado o los datos de acceso proporcionados son incorrectos. | 401 Unauthorized |
| `ERR_FORBIDDEN` | El usuario autenticado intenta acceder a un recurso o realizar una acción para la cual carece de permisos suficientes. | 403 Forbidden |
| `ERR_NOT_FOUND` | El recurso solicitado no existe en la base de datos o no se encuentra activo en el sistema. | 404 Not Found |
| `ERR_EMAIL_ALREADY_EXISTS` | Ocurre al intentar registrar un usuario con una dirección de correo electrónico que ya está ocupada por otra cuenta. | 409 Conflict |
| `ERR_DUPLICATE_GOAL_LOG` | Ocurre al intentar crear una entrada de progreso (check-in) para una meta en una fecha que ya tiene un registro existente. | 409 Conflict |
| `ERR_INVALID_GOAL_HIERARCHY` | Ocurre cuando la jerarquía de metas no es válida (ej., una meta no puede ser su propio padre o generar dependencias circulares). | 409 Conflict |
| `ERR_GOAL_DATE_RANGE_INVALID` | Ocurre cuando la fecha de inicio es posterior a la fecha de finalización de una meta. | 422 Unprocessable Entity |
| `ERR_INTERNAL_SERVER_ERROR` | Red de seguridad final para capturar excepciones inesperadas del sistema (caídas de conectividad, punteros nulos o fallas imprevistas de infraestructura). La trazabilidad completa permanece oculta detrás del `trackingId`. | 500 Internal Server Error |
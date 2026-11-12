# VetSM Backend — Veterinaria San Marcos

API REST de la Veterinaria San Marcos, desarrollada con Spring Boot para la asignatura
DSY1104 Desarrollo Fullstack II (DuocUC). El frontend en React vive en un repositorio aparte.

> Estado: fase 3 (API REST con Swagger, autenticación con JWT y permisos por rol). Falta
> conectar el frontend (fase 4) y publicar en AWS con Oracle (fase 6).

## Tecnologías

- Java 17 y Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security)
- Spring Security OAuth2 Resource Server (JWT firmados con HS256 mediante Nimbus JOSE)
- springdoc-openapi (Swagger UI)
- Oracle Autonomous Database (OCI) como base de datos del proyecto
- H2 en memoria (modo Oracle) para desarrollo local y pruebas
- Maven Wrapper: no hace falta instalar Maven

## Cómo ejecutar

Con H2 en memoria (perfil por defecto, no requiere configuración):

```bash
./mvnw spring-boot:run
```

Con Oracle (perfil `oracle`): define las variables de entorno de `.env.example`
(`DB_URL`, `DB_USER`, `DB_PASSWORD`, `SEED_ADMIN_PASSWORD`, `SEED_RECEPCION_PASSWORD`) y ejecuta:

```bash
SPRING_PROFILES_ACTIVE=oracle ./mvnw spring-boot:run
```

En Windows se usa `mvnw.cmd` en lugar de `./mvnw`.

Con la aplicación arriba:

- **Swagger UI:** <http://localhost:8080/swagger-ui.html> — para los endpoints protegidos, ejecuta
  `POST /api/v1/auth/login`, copia el `accessToken`, presiona **Authorize** y pégalo.
- **Especificación OpenAPI:** <http://localhost:8080/v3/api-docs>
- **Consola H2** (solo perfil `h2`): <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:mem:vetsm`, usuario `sa`)

## Arquitectura

```text
controller/   Endpoints REST: reciben y validan la petición (@Valid) y llaman al servicio
service/      Lógica de negocio y transacciones; devuelven DTOs
repository/   Acceso a la base de datos con Spring Data JPA
model/        Entidades JPA (tablas)
dto/          Lo que la API recibe y devuelve (nunca expone contraseñas)
exception/    Errores controlados y el manejador que los convierte en JSON
security/     Tokens JWT y de renovación, cookie de sesión y respuestas 401/403
config/       Swagger, reglas de seguridad, CORS, reloj, cifrado de contraseñas y datos iniciales
```

## Modelo de datos

| Tabla | Descripción |
|---|---|
| `usuario` | Personas que usan el sistema. Rol: `ADMIN`, `RECEPCIONISTA` o `CLIENTE`. Contraseña con hash BCrypt. Incluye los datos de perfil (región, comuna, dirección, avatar). |
| `mascota` | Pacientes de la clínica. Cada una pertenece a un cliente. |
| `categoria` | Agrupa los productos de la tienda. |
| `producto` | Lo que se vende: servicios de la clínica (sin stock, con duración) y artículos (con stock). |
| `cita` | Hora agendada para un servicio. Puede ser de una mascota registrada o una solicitud sin cuenta (solo datos de contacto). |
| `boleta` | Compra de un cliente, con el medio de pago (solo marca, últimos 4 dígitos y código de autorización). |
| `detalle_boleta` | Líneas de la boleta: producto, cantidad y precio al momento de la compra. |
| `tarjeta` | Tarjetas guardadas de un cliente: marca, últimos 4 dígitos, titular y vencimiento. **Nunca el número completo ni el CVV.** |
| `refresh_token` | Sesiones abiertas: hash SHA-256 del token de renovación, vencimiento y si fue revocado. |

Relaciones: `usuario 1—N mascota`, `mascota 1—N cita`, `producto 1—N cita`, `categoria 1—N producto`,
`usuario 1—N boleta`, `boleta 1—N detalle_boleta`, `producto 1—N detalle_boleta`, `usuario 1—N tarjeta`,
`usuario 1—N refresh_token`.

## Endpoints (`/api/v1`) y permisos

| Recurso | Endpoints | Quién |
|---|---|---|
| Autenticación | `POST /auth/login` · `POST /auth/registro` · `POST /auth/refresh` · `POST /auth/logout` | Público |
| Mi cuenta | `GET /perfil` · `PUT /perfil` · `PUT /perfil/password` | Cualquier usuario con sesión |
| Mis tarjetas | `GET /tarjetas` · `POST /tarjetas` · `PATCH /tarjetas/{id}/predeterminada` · `DELETE /tarjetas/{id}` | `CLIENTE` (solo las suyas) |
| Categorías | `GET /categorias` · `GET /categorias/{id}` | Público |
| | `POST /categorias` · `PUT /categorias/{id}` · `DELETE /categorias/{id}` | `ADMIN` |
| Productos | `GET /productos?categoriaId=&tipo=` · `GET /productos/{id}` | Público |
| | `POST /productos` · `PUT /productos/{id}` · `DELETE /productos/{id}` (baja lógica) | `ADMIN` |
| Usuarios | `GET /usuarios?rol=` · `GET /usuarios/{id}` · `POST /usuarios` · `PUT /usuarios/{id}` · `PATCH /usuarios/{id}/activo` · `DELETE /usuarios/{id}` | `ADMIN` |
| Mascotas | `GET /mascotas/mias` · `POST /mascotas/mias` | `CLIENTE` |
| | `GET /mascotas?duenoId=` · `GET /mascotas/{id}` · `POST /mascotas` · `PUT /mascotas/{id}` · `DELETE /mascotas/{id}` | `ADMIN`, `RECEPCIONISTA` |
| Citas | `POST /citas` | Público (con sesión de cliente puede usar sus mascotas) |
| | `GET /citas/mias` | `CLIENTE` |
| | `GET /citas?estado=&fecha=` · `GET /citas/{id}` · `PUT /citas/{id}` · `PATCH /citas/{id}/estado` · `DELETE /citas/{id}` | `ADMIN`, `RECEPCIONISTA` |
| Boletas | `POST /boletas` (comprar y pagar) · `GET /boletas/mias` | `CLIENTE` |
| | `GET /boletas/{id}` | `ADMIN` (todas), `CLIENTE` (solo las suyas) |
| | `GET /boletas` · `PATCH /boletas/{id}/anular` | `ADMIN` |
| Reportes | `GET /reportes/resumen` | `ADMIN` |

Cada endpoint está documentado en Swagger con su descripción, sus datos de entrada, sus posibles
respuestas y el rol que lo puede usar.

## Seguridad

### Autenticación con JWT

1. `POST /auth/login` verifica el correo y la contraseña (contra el hash BCrypt) y entrega dos tokens:
   - **Token de acceso (JWT):** en el cuerpo de la respuesta. Dura **15 minutos**. Lleva el id del
     usuario (`sub`), su rol (`rol`), su nombre y correo, el emisor (`iss`) y el vencimiento (`exp`).
     El frontend lo envía en cada petición: `Authorization: Bearer <token>`.
   - **Token de renovación:** en una cookie `HttpOnly`, `SameSite=Strict`, limitada a `/api/v1/auth`.
     Dura **7 días** y permite mantener la sesión al recargar la página sin pedir la contraseña.
2. **Generación:** el JWT se firma con **HMAC-SHA256** usando un secreto que solo conoce el servidor
   (`JWT_SECRET`, mínimo 32 caracteres; sin él la aplicación no arranca en producción).
3. **Verificación:** en cada petición, Spring Security comprueba la firma, el vencimiento y el emisor.
   Un token alterado (por ejemplo, con el rol cambiado), vencido o de otro sistema recibe `401`.
4. **Renovación:** `POST /auth/refresh` cambia la cookie por una nueva y entrega un JWT nuevo
   (**rotación**: cada token de renovación sirve una sola vez). Si un token ya usado vuelve a
   aparecer, se asume que fue robado y **se cierran todas las sesiones** de esa cuenta. Excepción:
   si reaparece menos de 10 segundos después de cambiarse (`app.jwt.segundos-gracia`), lo más
   probable es que dos pestañas del mismo navegador hayan renovado a la vez; ese intento recibe
   `401`, pero no se cierran las demás sesiones.
5. **Revocación:** cerrar sesión, cambiar la contraseña o desactivar la cuenta revoca los tokens de
   renovación. En la base de datos solo se guarda su hash SHA-256.

### Autorización por rol

- En `SecurityConfig` se declaran las rutas públicas; todo lo demás exige un token válido.
- En cada controlador, `@PreAuthorize` indica el rol permitido (`hasRole('ADMIN')`,
  `hasAnyRole('ADMIN', 'RECEPCIONISTA')`, `hasRole('CLIENTE')`). Sin el rol se responde `403`.
- Los datos propios se toman **del token, no de la URL**: un cliente solo ve y usa sus tarjetas,
  compras, mascotas y citas. Pedir un recurso ajeno responde `404`, sin revelar que existe.
- Nadie puede desactivarse, eliminarse ni quitarse el rol de administrador a sí mismo.

### Otras medidas

- Contraseñas con **BCrypt**. El login responde igual si el correo no existe o si la contraseña es
  incorrecta, y tarda lo mismo, para no revelar qué correos están registrados.
- **CORS**: solo el frontend configurado en `CORS_ORIGENES` puede llamar a la API desde el navegador.
- API sin estado (`STATELESS`); CSRF desactivado porque el token viaja en una cabecera y la cookie es `SameSite=Strict`.
- Errores `401` y `403` en JSON con el mismo formato que el resto de la API.

### Variables de entorno (producción)

| Variable | Para qué |
|---|---|
| `JWT_SECRET` | Firma de los tokens (mínimo 32 caracteres; por ejemplo `openssl rand -base64 48`) |
| `CORS_ORIGENES` | Dirección del frontend, por ejemplo `http://mi-dominio.cl` |
| `JWT_COOKIE_SEGURA` | `true` cuando el sitio use HTTPS |

### Códigos de respuesta

| Código | Cuándo |
|---|---|
| `200 OK` / `201 Created` / `204 No Content` | Operación exitosa (`201` incluye la cabecera `Location`) |
| `400 Bad Request` | Datos inválidos: el cuerpo trae un mensaje por campo en `errores` |
| `401 Unauthorized` | Sin sesión, credenciales incorrectas o token alterado, vencido o revocado |
| `402 Payment Required` | El banco (simulado) rechazó la tarjeta |
| `403 Forbidden` | Con sesión, pero el rol no tiene permiso |
| `404 Not Found` | El registro no existe |
| `409 Conflict` | Choca con los datos actuales: correo duplicado, horario ocupado, sin stock, transición de estado no permitida |
| `422 Unprocessable Content` | Regla de negocio: fecha pasada, producto no disponible, único administrador |

Todos los errores tienen el mismo formato:

```json
{
  "timestamp": "2026-11-11T14:30:00Z",
  "status": 409,
  "error": "Conflict",
  "mensaje": "Ya hay una cita agendada el 2026-12-01 a las 10:30. Elige otro horario.",
  "ruta": "/api/v1/citas",
  "errores": null
}
```

## Reglas de negocio

**Citas**
- No se agenda en el pasado (con la hora de Chile, aunque el servidor esté en UTC) ni en un horario ocupado; una cita cancelada libera su horario.
- Solo se agendan servicios de la clínica que estén activos.
- Los estados avanzan en orden: `PENDIENTE → CONFIRMADA → COMPLETADA`, y se puede cancelar antes de completar.
- Una cita completada o cancelada no se edita; una completada no se elimina (historial clínico).

**Compras**
- Los precios los pone el servidor desde la base de datos; el navegador solo envía productos y cantidades.
- No se vende más que el stock, ni productos dados de baja, ni con una cuenta del personal.
- Todo ocurre en una transacción: si el pago se rechaza, no se descuenta stock ni queda una boleta.
- `@Version` en `producto` evita vender dos veces la última unidad si dos clientes compran a la vez.
- Anular una boleta devuelve el stock.

**Pago (simulado)**
- Se valida la tarjeta igual que en el frontend: marca, algoritmo de Luhn, vencimiento y largo del CVV.
- Tarjetas de prueba: `4111 1111 1111 1111` (aprobada) y `4000 0000 0000 0002` (rechazada), con vencimiento futuro.
- Nunca se guarda ni se devuelve el número completo ni el CVV. Una tarjeta guardada solo se puede usar por su dueño.

**Usuarios**
- El correo es único y se guarda en minúsculas. El registro público siempre crea clientes.
- El sistema nunca queda sin un administrador activo.
- Una cuenta con compras o mascotas no se elimina: se desactiva.

## Pruebas

```bash
./mvnw test
```

89 pruebas sobre H2 en memoria (no necesitan Oracle):

- `ReglasDelModeloTest` y `ValidadorTarjetaTest`: reglas puras, sin base de datos.
- `RepositoriosTest`: consultas JPA.
- `SeguridadTest`: login, tokens alterados o vencidos, permisos por rol, rotación de la renovación
  con detección de robo, cierre de sesión, CORS y acceso solo a los datos propios.
- `ReglasDeNegocioTest`: cada regla de citas, compras, pagos, usuarios y catálogo.
- `ApiRestTest`: la API por HTTP (códigos, JSON, errores y Swagger).

## Datos iniciales

Al arrancar sobre una base vacía se crean las cuentas del personal (administrador y recepcionista)
y el catálogo de la tienda. Las contraseñas iniciales se toman de la configuración: en el perfil
`h2` son de demostración (ver `application-h2.properties`) y en el perfil `oracle` vienen de
variables de entorno.

## Seguridad de las credenciales

El wallet de Oracle, el archivo `.env` y cualquier contraseña real **no se suben al repositorio**
(están en `.gitignore`).

# VetSM Backend — Veterinaria San Marcos

API REST de la Veterinaria San Marcos, desarrollada con Spring Boot para la asignatura
DSY1104 Desarrollo Fullstack II (DuocUC). El frontend en React vive en un repositorio aparte.

> Estado: fase 2 (API REST con Swagger). La autenticación con JWT y los permisos por rol se
> agregan en la fase 3; mientras tanto la API está abierta (ver `config/SecurityConfig.java`).

## Tecnologías

- Java 17 y Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security)
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

- **Swagger UI:** <http://localhost:8080/swagger-ui.html>
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
config/       Swagger, seguridad, reloj, cifrado de contraseñas y datos iniciales
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

Relaciones: `usuario 1—N mascota`, `mascota 1—N cita`, `producto 1—N cita`, `categoria 1—N producto`,
`usuario 1—N boleta`, `boleta 1—N detalle_boleta`, `producto 1—N detalle_boleta`, `usuario 1—N tarjeta`.

## Endpoints (`/api/v1`)

| Recurso | Endpoints |
|---|---|
| Categorías | `GET /categorias` · `GET /categorias/{id}` · `POST /categorias` · `PUT /categorias/{id}` · `DELETE /categorias/{id}` |
| Productos | `GET /productos?categoriaId=&tipo=&incluirInactivos=` · `GET /productos/{id}` · `POST /productos` · `PUT /productos/{id}` · `DELETE /productos/{id}` (baja lógica) |
| Usuarios | `GET /usuarios?rol=` · `GET /usuarios/{id}` · `POST /usuarios` · `PUT /usuarios/{id}` · `PATCH /usuarios/{id}/activo` · `DELETE /usuarios/{id}` |
| Mascotas | `GET /mascotas?duenoId=` · `GET /mascotas/{id}` · `POST /mascotas` · `PUT /mascotas/{id}` · `DELETE /mascotas/{id}` |
| Citas | `GET /citas?estado=&fecha=` · `GET /citas/{id}` · `POST /citas` · `PUT /citas/{id}` · `PATCH /citas/{id}/estado` · `DELETE /citas/{id}` |
| Boletas | `GET /boletas` · `GET /boletas/{id}` · `POST /boletas` (comprar y pagar) · `PATCH /boletas/{id}/anular` |
| Reportes | `GET /reportes/resumen` |

Cada endpoint está documentado en Swagger con su descripción, sus datos de entrada, sus posibles
respuestas y el rol que lo podrá usar desde la fase 3.

### Códigos de respuesta

| Código | Cuándo |
|---|---|
| `200 OK` / `201 Created` / `204 No Content` | Operación exitosa (`201` incluye la cabecera `Location`) |
| `400 Bad Request` | Datos inválidos: el cuerpo trae un mensaje por campo en `errores` |
| `402 Payment Required` | El banco (simulado) rechazó la tarjeta |
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

61 pruebas sobre H2 en memoria (no necesitan Oracle):

- `ReglasDelModeloTest` y `ValidadorTarjetaTest`: reglas puras, sin base de datos.
- `RepositoriosTest`: consultas JPA.
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

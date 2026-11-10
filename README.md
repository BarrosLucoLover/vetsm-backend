# VetSM Backend — Veterinaria San Marcos

API REST de la Veterinaria San Marcos, desarrollada con Spring Boot para la asignatura
DSY1104 Desarrollo Fullstack II (DuocUC). El frontend en React vive en un repositorio aparte.

> Estado: fase 1 (modelo de datos y repositorios). Los endpoints REST, Swagger y la seguridad
> con JWT se agregan en las fases siguientes.

## Tecnologías

- Java 17 y Spring Boot 4.1 (Web MVC, Data JPA, Validation, Security)
- Oracle Autonomous Database (OCI) como base de datos del proyecto
- H2 en memoria (modo Oracle) para desarrollo local y pruebas
- Maven Wrapper: no hace falta instalar Maven

## Modelo de datos

| Tabla | Descripción |
|---|---|
| `usuario` | Personas que usan el sistema. Rol: `ADMIN`, `RECEPCIONISTA` o `CLIENTE`. La contraseña se guarda con hash BCrypt. |
| `mascota` | Pacientes de la clínica. Cada una pertenece a un dueño (`usuario`). |
| `categoria` | Agrupa los productos de la tienda. |
| `producto` | Lo que se vende: servicios de la clínica (sin stock) y artículos (con stock). |
| `cita` | Hora agendada para una mascota y un servicio. Estados: pendiente, confirmada, completada, cancelada. |
| `boleta` | Compra de un cliente al confirmar su carrito. |
| `detalle_boleta` | Líneas de la boleta: producto, cantidad y precio al momento de la compra. |

Relaciones: `usuario 1—N mascota`, `mascota 1—N cita`, `producto 1—N cita`,
`categoria 1—N producto`, `usuario 1—N boleta`, `boleta 1—N detalle_boleta`, `producto 1—N detalle_boleta`.

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

## Pruebas

```bash
./mvnw test
```

Las pruebas usan H2 en memoria, así que no necesitan conexión a Oracle.

## Datos iniciales

Al arrancar sobre una base vacía se crean las cuentas del personal (administrador y recepcionista)
y el catálogo de la tienda. Las contraseñas iniciales se toman de la configuración: en el perfil
`h2` son de demostración (ver `application-h2.properties`) y en el perfil `oracle` vienen de
variables de entorno.

## Seguridad de las credenciales

El wallet de Oracle, el archivo `.env` y cualquier contraseña real **no se suben al repositorio**
(están en `.gitignore`).

# Wallet de Oracle

Esta carpeta guarda el **wallet** de la Oracle Autonomous Database: los certificados con los que los
microservicios se conectan a la base de forma cifrada. Docker la monta en `/wallet` dentro de los
cuatro servicios.

**Todo lo que se ponga aquí, salvo este archivo, está en `.gitignore`: nunca se sube al repositorio.**

## 1. Preparar la base (lo hace quien administra la base en OCI)

1. Crear una **Autonomous Database** de tipo **Transaction Processing** (sirve la capa Always Free),
   con acceso desde cualquier lugar y **mTLS obligatorio** (opciones por defecto).
2. En **Database actions → SQL**, como `ADMIN`, crear un usuario por microservicio. Cada contraseña
   debe tener al menos 12 caracteres, con mayúscula, minúscula y número:

   ```sql
   CREATE USER VETSM_USUARIOS IDENTIFIED BY "Cambiar_Usuarios#2026";
   GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO VETSM_USUARIOS;
   ALTER USER VETSM_USUARIOS QUOTA UNLIMITED ON DATA;

   CREATE USER VETSM_CATALOGO IDENTIFIED BY "Cambiar_Catalogo#2026";
   GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO VETSM_CATALOGO;
   ALTER USER VETSM_CATALOGO QUOTA UNLIMITED ON DATA;

   CREATE USER VETSM_VENTAS IDENTIFIED BY "Cambiar_Ventas#2026";
   GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO VETSM_VENTAS;
   ALTER USER VETSM_VENTAS QUOTA UNLIMITED ON DATA;

   CREATE USER VETSM_AGENDA IDENTIFIED BY "Cambiar_Agenda#2026";
   GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO VETSM_AGENDA;
   ALTER USER VETSM_AGENDA QUOTA UNLIMITED ON DATA;
   ```

   No hay que crear tablas: cada microservicio crea las suyas al arrancar.
3. **Database connection → Download wallet**. Descarga un `.zip` (por ejemplo `Wallet_Vetsm.zip`).

## 2. Poner el wallet aquí (en cada computador o servidor que levante el backend)

Descomprimir el `.zip` **dentro de esta carpeta**, sin subcarpetas. Debe quedar así:

```
wallet/
├── LEEME.md
├── cwallet.sso
├── ewallet.p12
├── ojdbc.properties
├── sqlnet.ora
├── tnsnames.ora
└── ... (otros archivos del zip)
```

No hay que editar ninguno. `tnsnames.ora` trae los nombres de conexión (`vetsm_high`, `vetsm_tp`...);
el que se usa va en `ORACLE_SERVICIO` del archivo `.env`.

## 3. Levantar con Oracle

1. Copiar `.env.example` como `.env` y completar `JWT_SECRET`, `ORACLE_SERVICIO`, las cuatro
   contraseñas y las de las cuentas iniciales.
2. `docker compose up --build`
3. En el log de cada servicio debe aparecer `HikariPool-1 - Start completed.`

## Sin Docker

Cada servicio se puede ejecutar con su `.jar` indicando dónde está el wallet:

```bash
SPRING_PROFILES_ACTIVE=oracle ORACLE_WALLET=/ruta/al/proyecto/wallet ORACLE_SERVICIO=vetsm_tp \
DB_USER=VETSM_CATALOGO DB_PASSWORD=... JWT_SECRET=... java -jar ms-catalogo/target/ms-catalogo-*.jar
```

## Si falla la conexión

| Mensaje en el log | Causa | Solución |
|---|---|---|
| `ORA-12263: Fallo al acceder a tnsnames.ora` | El wallet no está descomprimido en esta carpeta, o quedó dentro de una subcarpeta | Descomprimir el `.zip` de modo que `tnsnames.ora` quede directamente en `wallet/` |
| `ORA-12154` o `could not resolve the connect identifier` | `ORACLE_SERVICIO` no existe en `tnsnames.ora` | Abrir `tnsnames.ora` y copiar uno de los nombres que aparecen ahí (por ejemplo `vetsm_tp`) |
| `ORA-17957` y `SSO KeyStore not available` | Falta la librería que lee `cwallet.sso` | Ya está incluida (`oraclepki` en el `pom.xml` de cada servicio): volver a compilar con `./mvnw package` |
| `ORA-17002` y `PKIX path building failed` | Un antivirus (como Norton) revisa las conexiones cifradas y pone su propio certificado | Con Docker: copiar su certificado raíz a `certificados-extra/` (ver su LEEME) y reconstruir. Con los `.jar`: agregar `-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NUL` al comando `java` en Windows |
| `ORA-01017: invalid username/password` | Usuario o contraseña incorrectos en `.env` | Revisar el `.env`; si hace falta, cambiar la clave con `ALTER USER ... IDENTIFIED BY "..."` |
| `ORA-01950: no privileges on tablespace 'DATA'` | Falta la cuota del usuario | Ejecutar `ALTER USER <usuario> QUOTA UNLIMITED ON DATA;` |
| `ORA-01031: insufficient privileges` | Faltan permisos para crear tablas | Ejecutar el `GRANT` del paso 1 para ese usuario |
| Tarda y termina en `Connection timed out` | La base está detenida (las Always Free se detienen tras 7 días sin uso) | En la consola de OCI, abrir la base y apretar **Start** |

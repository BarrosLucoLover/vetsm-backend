# Publicar el sistema en AWS (EC2)

El servidor corre lo mismo que en desarrollo: los cuatro microservicios y el gateway con Docker
Compose, conectados a la Oracle Autonomous Database. La diferencia es que el gateway también
sirve el frontend compilado y escucha en el puerto 80, así el sitio y la API quedan en la misma
dirección: `http://IP_DEL_SERVIDOR`.

```
Navegador ──▶ EC2 :80 ──▶ Gateway Nginx ──┬──▶ frontend (carpeta dist)
                                          └──▶ /api/v1 ──▶ 4 microservicios ──▶ Oracle (OCI)
```

## 1. Crear el servidor (consola de AWS)

En el laboratorio de AWS Academy, abrir la consola y ir a **EC2 → Instances → Launch instances**:

| Opción | Valor |
|---|---|
| Name | `vetsm-servidor` |
| AMI | **Ubuntu Server 24.04 LTS** (64-bit x86) |
| Instance type | **t3.medium** (2 vCPU, 4 GB): cada microservicio usa unos 310 MB |
| Key pair | **vockey** (la del laboratorio) |
| Network settings → Firewall | Crear un security group y marcar **Allow SSH traffic** y **Allow HTTP traffic from the internet** |
| Configure storage | **20 GiB** |

Apretar **Launch instance** y esperar a que el estado sea **Running**. La **Public IPv4 address**
de la instancia es la dirección del sitio.

> En AWS Academy la IP pública cambia cada vez que el laboratorio se apaga y se vuelve a encender.
> Después de encenderlo, revisar la IP nueva en la consola. El sistema se levanta solo
> (los contenedores tienen `restart: unless-stopped`).

## 2. Descargar la llave SSH

En la página del laboratorio: **AWS Details → SSH key → Download PEM** (`labsuser.pem`).
En Windows, dejar el archivo solo legible para tu usuario (PowerShell, en la carpeta de la llave):

```powershell
icacls labsuser.pem /inheritance:r /grant:r "$($env:USERNAME):R"
```

## 3. Instalar (en el servidor)

Conectarse por SSH (o con **EC2 → Connect → EC2 Instance Connect**, en el navegador):

```powershell
ssh -i labsuser.pem ubuntu@IP_DEL_SERVIDOR
```

Y ejecutar el script de instalación:

```bash
curl -fsSL https://raw.githubusercontent.com/BarrosLucoLover/vetsm-backend/main/desplegar/instalar.sh -o instalar.sh
bash instalar.sh
```

La primera vez instala Docker, descarga los dos repositorios y se detiene porque faltan el wallet
y el `.env`, que no están en GitHub.

## 4. Copiar las credenciales (desde tu computador)

En PowerShell, dentro de la carpeta `vetsm-backend` de tu computador (donde ya están `wallet/` y `.env`):

```powershell
scp -i RUTA\labsuser.pem -r wallet .env ubuntu@IP_DEL_SERVIDOR:~/vetsm/vetsm-backend/
```

## 5. Levantar

De vuelta en el servidor:

```bash
bash instalar.sh
```

El script ajusta solo el `.env` del servidor (`PUERTO=80` y `SITIO_WEB` apuntando al frontend
compilado), compila el frontend, construye los cuatro microservicios (unos 5 a 10 minutos la
primera vez) y al final muestra la dirección del sitio.

## Actualizar después de un cambio

Subir los cambios a GitHub y, en el servidor, volver a ejecutar `bash instalar.sh`.

## Comandos útiles en el servidor

```bash
cd ~/vetsm/vetsm-backend
sudo docker compose ps                         # estado de los contenedores
sudo docker compose logs --tail 50 ms-ventas   # registros de un servicio
sudo docker compose restart                    # reiniciar todo
sudo docker compose down                       # apagar
```

## Problemas comunes

| Síntoma | Causa | Solución |
|---|---|---|
| El sitio no abre desde el navegador | El security group no permite HTTP | EC2 → la instancia → Security → el security group → Edit inbound rules → agregar HTTP (80) desde 0.0.0.0/0 |
| `ssh` dice "UNPROTECTED PRIVATE KEY FILE" | Permisos de `labsuser.pem` en Windows | Ejecutar el comando `icacls` del paso 2 |
| El sitio abre pero no carga datos | Algún servicio no se conectó a Oracle | `sudo docker compose logs ms-catalogo` y buscar `ORA-`; ver la tabla de errores de `wallet/LEEME.md` |
| `ORA-12170` o tiempo de espera agotado | La base de OCI está detenida | En la consola de OCI, abrir la base y apretar **Start** |
| La IP cambió | Se apagó el laboratorio de AWS Academy | Usar la nueva Public IPv4 address de la consola |

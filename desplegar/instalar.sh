#!/usr/bin/env bash
# Instala y levanta la Veterinaria San Marcos en un servidor Ubuntu (AWS EC2).
# Se puede ejecutar varias veces: la primera instala y descarga todo; las siguientes actualizan el
# código desde GitHub y vuelven a levantar el sistema.
#
#   bash instalar.sh
#
# Necesita, en ~/vetsm/vetsm-backend, el wallet de Oracle (carpeta wallet/) y el archivo .env,
# que no están en GitHub: se copian desde el computador con scp (ver desplegar/LEEME.md).
set -euo pipefail

DIR="$HOME/vetsm"
BACK="$DIR/vetsm-backend"
FRONT="$DIR/Mi_Primera_pagina_fullstacks2"
REPO_BACK="https://github.com/BarrosLucoLover/vetsm-backend.git"
REPO_FRONT="https://github.com/Cyro792/Mi_Primera_pagina_fullstacks2.git"

paso() { printf '\n\033[1;32m==> %s\033[0m\n' "$1"; }
falla() { printf '\n\033[1;31mERROR: %s\033[0m\n' "$1"; exit 1; }

# ---------- 1. Docker y Git ----------
if ! command -v docker >/dev/null || ! sudo docker compose version >/dev/null 2>&1; then
  paso "Instalando Docker y Git"
  sudo apt-get update -y
  sudo apt-get install -y docker.io docker-compose-v2 git curl
  sudo systemctl enable --now docker
fi

# ---------- 2. Memoria de intercambio (swap) ----------
# Compilar con Maven y correr cuatro servicios Java pide memoria: con menos de 4 GB se agrega swap.
if [ "$(swapon --show | wc -l)" -eq 0 ] && [ "$(free -m | awk '/Mem:/ {print $2}')" -lt 3900 ]; then
  paso "Creando 2 GB de swap"
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

# ---------- 3. Código ----------
paso "Descargando el código desde GitHub"
mkdir -p "$DIR"
if [ -d "$BACK/.git" ]; then git -C "$BACK" pull --ff-only; else git clone "$REPO_BACK" "$BACK"; fi
if [ -d "$FRONT/.git" ]; then git -C "$FRONT" pull --ff-only; else git clone "$REPO_FRONT" "$FRONT"; fi

# ---------- 4. Credenciales (no están en GitHub) ----------
if [ ! -f "$BACK/wallet/tnsnames.ora" ] || [ ! -f "$BACK/.env" ]; then
  IP=$(curl -s --max-time 3 http://checkip.amazonaws.com || echo "IP_DEL_SERVIDOR")
  printf '\n\033[1;33mFaltan el wallet de Oracle y/o el archivo .env en %s.\033[0m\n' "$BACK"
  echo "Cópialos desde tu computador (PowerShell, en la carpeta vetsm-backend) y vuelve a ejecutar este script:"
  echo
  echo "  scp -i labsuser.pem -r wallet .env ubuntu@$IP:~/vetsm/vetsm-backend/"
  echo
  exit 1
fi

# El .env del servidor: puerto 80 y el frontend compilado servido por el gateway.
ajustar() { # ajustar CLAVE VALOR
  if grep -q "^$1=" "$BACK/.env"; then sed -i "s|^$1=.*|$1=$2|" "$BACK/.env"; else echo "$1=$2" >> "$BACK/.env"; fi
}
sed -i 's/\r$//' "$BACK/.env"
ajustar PERFIL oracle
ajustar PUERTO 80
ajustar SITIO_WEB ../Mi_Primera_pagina_fullstacks2/dist
grep -q '^JWT_SECRET=..*' "$BACK/.env" || falla "JWT_SECRET está vacío en .env"

# ---------- 5. Frontend ----------
paso "Compilando el frontend (npm run build dentro de un contenedor de Node)"
sudo docker run --rm -v "$FRONT":/app -w /app node:22-alpine sh -c "npm ci --no-audit --no-fund && npm run build"
[ -f "$FRONT/dist/index.html" ] || falla "No se generó dist/index.html"

# ---------- 6. Backend ----------
paso "Construyendo y levantando los microservicios (la primera vez tarda varios minutos)"
cd "$BACK"
sudo docker compose up --build -d

paso "Esperando a que los cuatro servicios se conecten a Oracle"
for _ in $(seq 1 60); do
  listos=$(sudo docker compose logs 2>/dev/null | grep -c -E "Started Ms(Usuarios|Catalogo|Ventas|Agenda)Application" || true)
  [ "$listos" -ge 4 ] && break
  sleep 5
done
sudo docker compose ps

if curl -fs http://localhost/api/v1/categorias >/dev/null; then
  IP=$(curl -s --max-time 3 http://checkip.amazonaws.com || echo "IP_DEL_SERVIDOR")
  printf '\n\033[1;32mListo. El sitio está publicado en: http://%s\033[0m\n' "$IP"
  echo "Swagger: http://$IP/swagger-ui.html"
else
  echo
  echo "La API todavía no responde. Revisa los registros con:  cd $BACK && sudo docker compose logs --tail 50"
  exit 1
fi

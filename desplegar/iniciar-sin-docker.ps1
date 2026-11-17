# Levanta los cuatro microservicios SIN Docker, por ejemplo en un PC del laboratorio que solo tiene Java 17.
#
#   powershell -ExecutionPolicy Bypass -File desplegar\iniciar-sin-docker.ps1          # con Oracle (wallet y .env)
#   powershell -ExecutionPolicy Bypass -File desplegar\iniciar-sin-docker.ps1 -H2      # base en memoria, sin Oracle
#   powershell -ExecutionPolicy Bypass -File desplegar\iniciar-sin-docker.ps1 -Detener # apaga lo que se levantó con -Oculto
#
# Cada servicio se abre en su propia ventana (cerrarla lo apaga). Con -Oculto corren en segundo plano
# y sus registros quedan en la carpeta logs\. Después, en la carpeta del frontend:
#   npm install            (solo la primera vez)
#   npm run dev:sin-docker  y abrir http://localhost:3000
param(
    [switch]$H2,
    [switch]$Oculto,
    [switch]$Detener
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $raiz 'logs'
$archivoPids = Join-Path $logs 'pids.txt'
$servicios = @(
    @{ Nombre = 'ms-usuarios'; Puerto = 8081; Prefijo = 'USUARIOS' },
    @{ Nombre = 'ms-catalogo'; Puerto = 8082; Prefijo = 'CATALOGO' },
    @{ Nombre = 'ms-ventas';   Puerto = 8083; Prefijo = 'VENTAS' },
    @{ Nombre = 'ms-agenda';   Puerto = 8084; Prefijo = 'AGENDA' }
)

function Paso($texto) { Write-Host "`n==> $texto" -ForegroundColor Green }
function Falla($texto) { Write-Host "`nERROR: $texto" -ForegroundColor Red; exit 1 }

# ---------- Apagar ----------
if ($Detener) {
    if (-not (Test-Path $archivoPids)) { Falla 'No hay servicios levantados con -Oculto (no existe logs\pids.txt).' }
    foreach ($id in Get-Content $archivoPids) {
        try { Stop-Process -Id ([int]$id) -Force -ErrorAction Stop; Write-Host "Apagado el proceso $id" } catch { }
    }
    Remove-Item $archivoPids -Force
    exit 0
}

# ---------- 1. Java 17 ----------
Paso 'Revisando Java'
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) { Falla 'No se encontró Java. Instala el JDK 17 (o un JRE 17 portátil) y vuelve a intentar.' }
$version = (cmd /c 'java -version 2>&1' | Select-Object -First 1)
Write-Host $version
if ($version -notmatch 'version "(\d+)') { Falla 'No se pudo leer la versión de Java.' }
if ([int]$Matches[1] -lt 17) { Falla "Se necesita Java 17 o superior (este PC tiene $($Matches[1]))." }

# ---------- 2. Los .jar ----------
$faltan = $servicios | Where-Object { -not (Test-Path (Join-Path $raiz "$($_.Nombre)\target\$($_.Nombre).jar")) }
if ($faltan) {
    Paso 'Compilando los microservicios con Maven (la primera vez descarga librerías y tarda unos minutos)'
    Push-Location $raiz
    try { & .\mvnw.cmd -q -B package -DskipTests } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Falla 'Falló la compilación. Revisa que haya internet.' }
}

# ---------- 3. Configuración ----------
$comunes = @{}
$porServicio = @{}
if ($H2) {
    Paso 'Modo H2: base en memoria con datos de demostración (no usa Oracle)'
    $comunes['SPRING_PROFILES_ACTIVE'] = 'h2'
} else {
    Paso 'Modo Oracle: leyendo .env y el wallet'
    $env_ = Join-Path $raiz '.env'
    $wallet = Join-Path $raiz 'wallet'
    if (-not (Test-Path $env_)) { Falla 'Falta el archivo .env en la carpeta vetsm-backend (copia el tuyo o usa -H2).' }
    if (-not (Test-Path (Join-Path $wallet 'tnsnames.ora'))) { Falla 'Falta el wallet descomprimido en vetsm-backend\wallet (o usa -H2).' }
    $valores = @{}
    foreach ($linea in Get-Content $env_) {
        $l = $linea.Trim()
        if ($l -eq '' -or $l.StartsWith('#') -or -not $l.Contains('=')) { continue }
        $i = $l.IndexOf('=')
        $valores[$l.Substring(0, $i).Trim()] = $l.Substring($i + 1).Trim()
    }
    if (-not $valores['JWT_SECRET']) { Falla 'JWT_SECRET está vacío en .env.' }
    $comunes['SPRING_PROFILES_ACTIVE'] = 'oracle'
    $comunes['JWT_SECRET'] = $valores['JWT_SECRET']
    $comunes['ORACLE_SERVICIO'] = $(if ($valores['ORACLE_SERVICIO']) { $valores['ORACLE_SERVICIO'] } else { 'vetsm_tp' })
    $comunes['ORACLE_WALLET'] = $wallet.Replace('\', '/')
    $comunes['SEED_ADMIN_PASSWORD'] = $valores['SEED_ADMIN_PASSWORD']
    $comunes['SEED_RECEPCION_PASSWORD'] = $valores['SEED_RECEPCION_PASSWORD']
    foreach ($s in $servicios) {
        $porServicio[$s.Nombre] = @{
            DB_USER = $valores["$($s.Prefijo)_DB_USER"]
            DB_PASSWORD = $valores["$($s.Prefijo)_DB_PASSWORD"]
        }
    }
}

# ---------- 4. Levantar ----------
New-Item -ItemType Directory -Force $logs | Out-Null
foreach ($s in $servicios) {
    $ocupado = Get-NetTCPConnection -LocalPort $s.Puerto -State Listen -ErrorAction SilentlyContinue
    if ($ocupado) { Falla "El puerto $($s.Puerto) ya está en uso. Apaga lo que lo ocupa (o usa -Detener) y vuelve a intentar." }
}
$pids = @()
foreach ($s in $servicios) {
    Paso "Levantando $($s.Nombre) en el puerto $($s.Puerto)"
    foreach ($k in $comunes.Keys) { Set-Item -Path "Env:$k" -Value $comunes[$k] }
    if ($porServicio[$s.Nombre]) {
        foreach ($k in $porServicio[$s.Nombre].Keys) { Set-Item -Path "Env:$k" -Value $porServicio[$s.Nombre][$k] }
    }
    $jar = Join-Path $raiz "$($s.Nombre)\target\$($s.Nombre).jar"
    # Confía en los certificados de Windows (necesario si un antivirus revisa las conexiones cifradas).
    $argumentos = @('-Djavax.net.ssl.trustStoreType=Windows-ROOT', '-Djavax.net.ssl.trustStore=NUL', '-jar', "`"$jar`"")
    if ($Oculto) {
        $p = Start-Process -FilePath 'java' -ArgumentList $argumentos -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $logs "$($s.Nombre).log") -RedirectStandardError (Join-Path $logs "$($s.Nombre).err.log")
    } else {
        $comando = "`$Host.UI.RawUI.WindowTitle = '$($s.Nombre) :$($s.Puerto)'; java $($argumentos -join ' ')"
        $p = Start-Process -FilePath 'powershell' -ArgumentList '-NoExit', '-Command', $comando -PassThru
    }
    $pids += $p.Id
}
foreach ($k in @('DB_USER', 'DB_PASSWORD', 'JWT_SECRET', 'SEED_ADMIN_PASSWORD', 'SEED_RECEPCION_PASSWORD')) { Remove-Item "Env:$k" -ErrorAction SilentlyContinue }
$pids | Set-Content $archivoPids

# ---------- 5. Esperar ----------
Paso 'Esperando a que los cuatro servicios respondan (hasta 3 minutos)'
$fin = (Get-Date).AddMinutes(3)
$listos = @{}
while ((Get-Date) -lt $fin -and $listos.Count -lt 4) {
    foreach ($s in $servicios) {
        if ($listos[$s.Nombre]) { continue }
        try {
            Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://localhost:$($s.Puerto)/api/v1/categorias" | Out-Null
            $listos[$s.Nombre] = $true
        } catch {
            if ($_.Exception.Response) { $listos[$s.Nombre] = $true }   # respondió (aunque sea 401 o 404): está arriba
        }
    }
    Start-Sleep -Seconds 3
}
foreach ($s in $servicios) {
    $estado = $(if ($listos[$s.Nombre]) { 'listo' } else { 'NO responde (revisa su ventana o logs\' + $s.Nombre + '.log)' })
    Write-Host ("  {0,-12} http://localhost:{1}  {2}" -f $s.Nombre, $s.Puerto, $estado)
}
if ($listos.Count -lt 4) { Falla 'Algún servicio no arrancó. Si el error dice ORA- o "timed out", prueba con -H2.' }

Write-Host "`nBackend listo." -ForegroundColor Green
Write-Host 'Ahora el frontend, en la carpeta Mi_Primera_pagina_fullstacks2:'
Write-Host '  npm install            (solo la primera vez)'
Write-Host '  npm run dev:sin-docker  y abrir http://localhost:3000'
if ($H2) { Write-Host "`nCuentas de demostración (H2): admin@sanmarcos.cl / Admin123!  ·  recepcion@sanmarcos.cl / Recep123!  ·  ana.garcia@correo.cl / Cliente123!" }

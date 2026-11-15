# Certificados extra para construir con Docker

Normalmente esta carpeta queda vacía y no hay que hacer nada.

Sirve cuando un antivirus (por ejemplo Norton o Avast) o un proxy de empresa revisa las conexiones
HTTPS. En ese caso Maven, dentro del contenedor, no confía en el certificado del antivirus y falla al
descargar dependencias con el error `PKIX path building failed`.

Solución: exportar el certificado raíz del antivirus en formato PEM y dejarlo aquí con extensión
`.crt`. El `Dockerfile` lo importa en Java solo durante la compilación. En PowerShell:

```powershell
$c = Get-ChildItem Cert:\CurrentUser\Root | Where-Object Subject -match 'Norton'
"-----BEGIN CERTIFICATE-----`n" + [Convert]::ToBase64String($c.RawData, 'InsertLineBreaks') + "`n-----END CERTIFICATE-----" |
    Set-Content certificados-extra\antivirus.crt
```

Los archivos `.crt` de esta carpeta no se suben a git: son propios de cada computador.

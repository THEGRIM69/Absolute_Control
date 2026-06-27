# build.ps1
# Script de build completo para Absolute Control.
# Compila el proyecto, genera el instalador .exe con jpackage, lo firma con
# un certificado autofirmado, y genera el checksum SHA-256 del resultado.
#
# Uso:
#   .\build.ps1 -Version "1.0.0"
#
# Requisitos:
#   - JDK 17+ con jpackage disponible (verificar con: jpackage --version)
#   - signtool.exe en el PATH (viene con Windows SDK / Visual Studio)
#   - El certificado AbsoluteControl.pfx ya generado (ver instrucciones
#     más abajo, sección "Generar certificado", se hace una sola vez)
#   - lib\jnativehook-2.2.1.jar presente en el proyecto

param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$PfxPath = "AbsoluteControl.pfx",
    [string]$PfxPassword = $env:ABSOLUTE_CONTROL_PFX_PASSWORD
)

$ErrorActionPreference = "Stop"

# ── Configuración del proyecto ──────────────────────────────────────
$AppName      = "AbsoluteControl"
$MainClass    = "Absolute_Control.Main"
$SrcDir       = "src"
$LibJar       = "lib\jnativehook-2.2.1.jar"
$BuildDir     = "build"
$ClassesDir   = "$BuildDir\classes"
$JarPath      = "$BuildDir\$AppName.jar"
$OutputDir    = "$BuildDir\installer"

Write-Host "=== Absolute Control — build $Version ===" -ForegroundColor Cyan

# ── 1. Limpiar build anterior ───────────────────────────────────────
if (Test-Path $BuildDir) {
    Write-Host "Limpiando build anterior..."
    Remove-Item -Recurse -Force $BuildDir
}
New-Item -ItemType Directory -Path $ClassesDir | Out-Null
New-Item -ItemType Directory -Path $OutputDir  | Out-Null

# ── 2. Compilar ──────────────────────────────────────────────────────
Write-Host "Compilando..." -ForegroundColor Cyan
$sources = Get-ChildItem -Recurse -Path $SrcDir -Filter "*.java" | ForEach-Object { $_.FullName }
javac -cp $LibJar -d $ClassesDir $sources
if ($LASTEXITCODE -ne 0) { throw "Error de compilación" }

# ── 3. Extraer jnativehook dentro de las clases compiladas ─────────
# jpackage necesita un único .jar "fat" con todo adentro (o un classpath
# explícito de varios jars). Lo más simple para un proyecto sin Maven/Gradle
# es descomprimir la dependencia dentro del mismo directorio de clases antes
# de empaquetar el .jar final.
Write-Host "Empaquetando dependencias..." -ForegroundColor Cyan
Push-Location $ClassesDir
jar xf "..\..\$LibJar"
Pop-Location

# ── 4. Crear el .jar ejecutable ─────────────────────────────────────
Write-Host "Creando $JarPath..." -ForegroundColor Cyan
jar --create --file $JarPath --main-class $MainClass -C $ClassesDir .
if ($LASTEXITCODE -ne 0) { throw "Error creando el jar" }

# ── 5. Empaquetar con jpackage ──────────────────────────────────────
# --win-dir-chooser   : el usuario puede elegir la carpeta de instalación
# --win-menu          : crea entrada en el menú Inicio
# --win-shortcut      : crea acceso directo en el escritorio
# --win-per-user-install : instala solo para el usuario actual, sin pedir
#                          permisos de administrador (UAC)
# --win-upgrade-uuid  : mismo UUID en cada versión = jpackage desinstala
#                       la versión vieja automáticamente al instalar la nueva
Write-Host "Generando instalador con jpackage..." -ForegroundColor Cyan
jpackage `
    --type exe `
    --name $AppName `
    --app-version $Version `
    --input $BuildDir `
    --main-jar "$AppName.jar" `
    --main-class $MainClass `
    --dest $OutputDir `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut `
    --win-per-user-install `
    --win-upgrade-uuid "8f3a2b1c-4d5e-4f6a-9b8c-1234567890ab" `
    --description "Software KVM por red local — comparte mouse y teclado entre dos PCs" `
    --vendor "Ing. RRF - THEGRIM69"

if ($LASTEXITCODE -ne 0) { throw "Error en jpackage" }

$exePath = Join-Path $OutputDir "$AppName-$Version.exe"
if (-not (Test-Path $exePath)) {
    # jpackage puede nombrar el archivo distinto según versión de JDK;
    # buscamos el .exe generado más reciente en la carpeta de salida.
    $exePath = (Get-ChildItem $OutputDir -Filter "*.exe" | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
Write-Host "Instalador generado: $exePath" -ForegroundColor Green

# ── 6. Firmar el instalador (certificado autofirmado) ───────────────
if (Test-Path $PfxPath) {
    if (-not $PfxPassword) {
        Write-Host "Advertencia: no se definió la contraseña del certificado." -ForegroundColor Yellow
        Write-Host "Definila con: `$env:ABSOLUTE_CONTROL_PFX_PASSWORD = 'tu_contraseña'" -ForegroundColor Yellow
        Write-Host "Saltando la firma." -ForegroundColor Yellow
    } else {
        Write-Host "Firmando $exePath..." -ForegroundColor Cyan
        signtool sign /f $PfxPath /p $PfxPassword /fd SHA256 `
            /tr http://timestamp.digicert.com /td SHA256 $exePath
        if ($LASTEXITCODE -ne 0) { throw "Error firmando el instalador" }

        Write-Host "Verificando firma..." -ForegroundColor Cyan
        signtool verify /pa $exePath
    }
} else {
    Write-Host "No se encontró $PfxPath — saltando firma. Ver sección 'Generar certificado' en este script." -ForegroundColor Yellow
}

# ── 7. Generar checksum SHA-256 ──────────────────────────────────────
Write-Host "Generando checksum SHA-256..." -ForegroundColor Cyan
$hash = Get-FileHash -Path $exePath -Algorithm SHA256
$checksumFile = "$exePath.sha256.txt"
"$($hash.Hash)  $(Split-Path $exePath -Leaf)" | Out-File -Encoding ascii $checksumFile
Write-Host "Checksum guardado en: $checksumFile" -ForegroundColor Green
Write-Host $hash.Hash -ForegroundColor Green

Write-Host ""
Write-Host "=== Build completo ===" -ForegroundColor Cyan
Write-Host "Instalador: $exePath"
Write-Host "Checksum:   $checksumFile"

<#
─────────────────────────────────────────────────────────────────────
  GENERAR CERTIFICADO AUTOFIRMADO (una sola vez, antes del primer build)
─────────────────────────────────────────────────────────────────────

  $cert = New-SelfSignedCertificate `
      -Type CodeSigning `
      -Subject "CN=Rodri - Absolute Control" `
      -CertStoreLocation "Cert:\CurrentUser\My" `
      -NotAfter (Get-Date).AddYears(3)

  $password = ConvertTo-SecureString -String "TuContraseñaAca" -Force -AsPlainText
  Export-PfxCertificate -Cert $cert -FilePath "AbsoluteControl.pfx" -Password $password

  Después, para que este script la firme automáticamente, definí la
  contraseña en la sesión de PowerShell antes de correr build.ps1:

  $env:ABSOLUTE_CONTROL_PFX_PASSWORD = "TuContraseñaAca"
  .\build.ps1 -Version "1.0.0"

  IMPORTANTE: nunca subas AbsoluteControl.pfx ni la contraseña al repo de
  GitHub. Agregá "*.pfx" a tu .gitignore.
─────────────────────────────────────────────────────────────────────
#>

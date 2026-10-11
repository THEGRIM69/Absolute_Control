# Compila y prueba la persistencia sin tocar el archivo real del usuario.
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path $PSScriptRoot -Parent
$testClasses = Join-Path $env:TEMP ("AbsoluteControl-equipment-tests-" + [guid]::NewGuid().ToString("N"))
$dependency = Join-Path $projectRoot "kvm-client/lib/jnativehook-2.2.1.jar"
$sources = @(Get-ChildItem (Join-Path $projectRoot "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
javac -encoding UTF-8 --release 17 -cp $dependency -d $testClasses $sources (Join-Path $PSScriptRoot "EquipmentPersistenceCheck.java")
if ($LASTEXITCODE -ne 0) { throw "Compilación fallida" }
java '-Djava.awt.headless=true' -cp "$testClasses;$dependency" EquipmentPersistenceCheck
if ($LASTEXITCODE -ne 0) { throw "Pruebas de persistencia fallidas" }
Write-Host "Clases de prueba: $testClasses"

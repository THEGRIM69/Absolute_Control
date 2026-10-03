# Compila y prueba sin instalar dependencias ni escribir binarios en el repo.
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path $PSScriptRoot -Parent
$testClasses = Join-Path $env:TEMP ("AbsoluteControl-tests-" + [guid]::NewGuid().ToString("N"))
$dependency = Join-Path $projectRoot "kvm-client/lib/jnativehook-2.2.1.jar"
$sources = @(Get-ChildItem (Join-Path $projectRoot "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
javac -encoding UTF-8 --release 17 -cp $dependency -d $testClasses $sources (Join-Path $PSScriptRoot "StabilityCheck.java")
if ($LASTEXITCODE -ne 0) { throw "Compilacion fallida" }
java --enable-native-access=ALL-UNNAMED -cp "$testClasses;$dependency" StabilityCheck
if ($LASTEXITCODE -ne 0) { throw "Pruebas fallidas" }
Write-Host "Clases de prueba: $testClasses"

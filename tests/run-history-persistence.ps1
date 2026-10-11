# Prueba UI-5.3B con historial y datos temporales, sin conexiones externas.
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path $PSScriptRoot -Parent
$testClasses = Join-Path $env:TEMP ("AbsoluteControl-history-tests-" + [guid]::NewGuid().ToString("N"))
$dependency = Join-Path $projectRoot "kvm-client/lib/jnativehook-2.2.1.jar"
$sources = @(Get-ChildItem (Join-Path $projectRoot "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
javac -encoding UTF-8 --release 17 -cp $dependency -d $testClasses $sources (Join-Path $PSScriptRoot "HistoryPersistenceCheck.java")
if ($LASTEXITCODE -ne 0) { throw "Compilación fallida" }
java '-Djava.awt.headless=true' -cp "$testClasses;$dependency" HistoryPersistenceCheck
if ($LASTEXITCODE -ne 0) { throw "Pruebas de historial fallidas" }
Write-Host "Clases de prueba: $testClasses"

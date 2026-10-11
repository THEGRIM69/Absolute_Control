# Verifica UI-5.3A con dobles de coordinación y sin conexiones externas.
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path $PSScriptRoot -Parent
$testClasses = Join-Path $env:TEMP ("AbsoluteControl-ui53a-tests-" + [guid]::NewGuid().ToString("N"))
$dependency = Join-Path $projectRoot "kvm-client/lib/jnativehook-2.2.1.jar"
$sources = @(Get-ChildItem (Join-Path $projectRoot "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
javac -encoding UTF-8 --release 17 -cp $dependency -d $testClasses $sources (Join-Path $PSScriptRoot "QuickConnectionCheck.java")
if ($LASTEXITCODE -ne 0) { throw "Compilación fallida" }
java '-Djava.awt.headless=true' "-Dproject.root=$projectRoot" -cp "$testClasses;$dependency" QuickConnectionCheck
if ($LASTEXITCODE -ne 0) { throw "Pruebas de conexión rápida fallidas" }
Write-Host "Clases de prueba: $testClasses"

# Prueba Swing headless de UI-5.2 con almacenamiento temporal aislado.
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path $PSScriptRoot -Parent
$testClasses = Join-Path $env:TEMP ("AbsoluteControl-ui52-tests-" + [guid]::NewGuid().ToString("N"))
$dependency = Join-Path $projectRoot "kvm-client/lib/jnativehook-2.2.1.jar"
$sources = @(Get-ChildItem (Join-Path $projectRoot "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
javac -encoding UTF-8 --release 17 -cp $dependency -d $testClasses $sources (Join-Path $PSScriptRoot "UiEquipmentCheck.java")
if ($LASTEXITCODE -ne 0) { throw "Compilación fallida" }
java '-Djava.awt.headless=true' -cp "$testClasses;$dependency" UiEquipmentCheck
if ($LASTEXITCODE -ne 0) { throw "Pruebas UI-5.2 fallidas" }
Write-Host "Clases de prueba: $testClasses"

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path $PSScriptRoot -Parent
$testClasses = Join-Path $env:TEMP ("AbsoluteControl-mouse-tests-" + [guid]::NewGuid().ToString("N"))
$dependency = Join-Path $projectRoot "kvm-client/lib/jnativehook-2.2.1.jar"
$sources = @(Get-ChildItem (Join-Path $projectRoot "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
javac -encoding UTF-8 --release 17 -cp $dependency -d $testClasses $sources (Join-Path $PSScriptRoot "MouseTransitionCheck.java")
if ($LASTEXITCODE -ne 0) { throw "Compilacion fallida" }
java '-Djava.awt.headless=true' -cp "$testClasses;$dependency" MouseTransitionCheck --geometry
if ($LASTEXITCODE -ne 0) { throw "Pruebas de geometria fallidas" }
java --enable-native-access=ALL-UNNAMED -cp "$testClasses;$dependency" MouseTransitionCheck --integration
if ($LASTEXITCODE -ne 0) { throw "Pruebas de transicion fallidas" }
Write-Host "Clases de prueba: $testClasses"

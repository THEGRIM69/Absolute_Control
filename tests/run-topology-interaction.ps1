# Prueba headless de la distribución interactiva sin tocar el núcleo KVM.
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path $PSScriptRoot -Parent
$testClasses = Join-Path $env:TEMP ("AbsoluteControl-topology-tests-" + [guid]::NewGuid().ToString("N"))
$dependency = Join-Path $projectRoot "kvm-client/lib/jnativehook-2.2.1.jar"
$sources = @(Get-ChildItem (Join-Path $projectRoot "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
javac -encoding UTF-8 --release 17 -cp $dependency -d $testClasses $sources (Join-Path $PSScriptRoot "TopologyInteractionCheck.java")
if ($LASTEXITCODE -ne 0) { throw "Compilación fallida" }
java '-Djava.awt.headless=true' "-Dproject.root=$projectRoot" -cp "$testClasses;$dependency" TopologyInteractionCheck
if ($LASTEXITCODE -ne 0) { throw "Pruebas de topología fallidas" }
Write-Host "Clases de prueba: $testClasses"

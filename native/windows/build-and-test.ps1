param(
    [ValidateSet('Debug', 'Release')]
    [string]$Configuration = 'Release'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$nativeRoot = $PSScriptRoot
$outDir = Join-Path $nativeRoot "out\$Configuration"
$sourceDir = Join-Path $nativeRoot 'src'
$testDir = Join-Path $nativeRoot 'tests'

& (Join-Path $testDir 'run-source-audit.ps1')

function Import-MsvcEnvironment {
    if (Get-Command cl.exe -ErrorAction SilentlyContinue) {
        return
    }

    $vsRoots = @(
        'C:\Program Files\Microsoft Visual Studio',
        'C:\Program Files (x86)\Microsoft Visual Studio'
    )
    $vcVars = Get-ChildItem -Path $vsRoots -Filter 'vcvars64.bat' `
        -File -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if ($vcVars) {
        $environmentLines = & cmd.exe /d /s /c `
            "`"$($vcVars.FullName)`" -arch=x64 -host_arch=x64 >nul && set"
        if ($LASTEXITCODE -ne 0) {
            throw "vcvars64.bat fallo con codigo $LASTEXITCODE."
        }
        foreach ($line in $environmentLines) {
            $separator = $line.IndexOf('=')
            if ($separator -gt 0) {
                $name = $line.Substring(0, $separator)
                $value = $line.Substring($separator + 1)
                Set-Item -LiteralPath "Env:$name" -Value $value
            }
        }
    }
    else {
        # Some Visual Studio installations expose an isolated x64 C++ SDK for
        # editor tooling even when vcvars64.bat is absent. It is sufficient for
        # this Win32 prototype and keeps the build offline.
        $scopeCompiler = Get-ChildItem -Path $vsRoots -Filter 'cl.exe' `
            -File -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -like '*\SDK\ScopeCppSDK\*\VC\bin\cl.exe' } |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if (-not $scopeCompiler) {
            throw @'
No se encontro MSVC x64. Instale el workload "Desktop development with C++"
de Visual Studio, incluido un Windows SDK, y vuelva a ejecutar este script.
'@
        }

        $vcDir = Split-Path -Parent (Split-Path -Parent $scopeCompiler.FullName)
        $scopeRoot = Split-Path -Parent $vcDir
        $sdkDir = Join-Path $scopeRoot 'SDK'
        $env:Path = "$(Split-Path -Parent $scopeCompiler.FullName);$env:Path"
        $env:INCLUDE = @(
            (Join-Path $vcDir 'include'),
            (Join-Path $sdkDir 'include\ucrt'),
            (Join-Path $sdkDir 'include\shared'),
            (Join-Path $sdkDir 'include\um')
        ) -join ';'
        $env:LIB = @(
            (Join-Path $vcDir 'lib'),
            (Join-Path $sdkDir 'lib')
        ) -join ';'
    }

    if (-not (Get-Command cl.exe -ErrorAction SilentlyContinue)) {
        throw 'vcvars64.bat no expuso cl.exe; el workload C++ esta incompleto.'
    }
}

Import-MsvcEnvironment
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$optimization = if ($Configuration -eq 'Release') { '/O2' } else { '/Od' }
$common = @(
    '/nologo',
    '/std:c++17',
    '/EHsc',
    '/W4',
    '/WX',
    '/permissive-',
    $optimization,
    "/I$sourceDir"
)

$policySource = Join-Path $sourceDir 'input_policy.cpp'
$testSource = Join-Path $testDir 'input_policy_tests.cpp'
$helperSource = Join-Path $sourceDir 'absolute_control_input_helper.cpp'
$testExe = Join-Path $outDir 'input_policy_tests.exe'
$helperExe = Join-Path $outDir 'absolute-control-input-helper.exe'

Push-Location $outDir
try {
    & cl.exe @common "/Fe:$testExe" $testSource $policySource
    if ($LASTEXITCODE -ne 0) {
        throw "Compilacion de pruebas fallo con codigo $LASTEXITCODE."
    }
    & $testExe
    if ($LASTEXITCODE -ne 0) {
        throw "Pruebas nativas fallaron con codigo $LASTEXITCODE."
    }

    & cl.exe @common "/Fe:$helperExe" $helperSource $policySource user32.lib
    if ($LASTEXITCODE -ne 0) {
        throw "Compilacion del helper fallo con codigo $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Write-Host "NativeBuildAndTest: OK ($Configuration)"
Write-Host "Helper: $helperExe"

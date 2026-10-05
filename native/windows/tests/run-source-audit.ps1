$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$nativeRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Resolve-Path (Join-Path $nativeRoot '..\..')
$helper = Join-Path $nativeRoot 'src\absolute_control_input_helper.cpp'

$required = @(
    'WH_KEYBOARD_LL',
    'WH_MOUSE_LL',
    'LLKHF_INJECTED',
    'LLMHF_INJECTED',
    'CallNextHookEx',
    'UnhookWindowsHookEx',
    'kLeaseDurationMs',
    'absolute_control::kDrainingTimeoutMs',
    'LOCAL_DRAINING',
    'BoundedSpscQueue'
)

$source = Get-Content -LiteralPath $helper -Raw
foreach ($token in $required) {
    if (-not $source.Contains($token)) {
        throw "Falta el elemento requerido en el helper: $token"
    }
}

$forbidden = @(
    'BlockInput(',
    'WSAStartup(',
    'socket(',
    'connect(',
    'JNIEnv',
    'NativeInputEvent.reserved'
)
foreach ($token in $forbidden) {
    if ($source.Contains($token)) {
        throw "Elemento prohibido encontrado en el helper: $token"
    }
}

$protected = @(
    'src/Absolute_Control/core/Cliente.java',
    'src/Absolute_Control/core/Servidor.java',
    'src/Absolute_Control/input/MouseHandler.java',
    'src/Absolute_Control/input/KeyboardHandler.java',
    'src/Absolute_Control/Main.java',
    'src/Absolute_Control/core/Discovery.java'
)

Push-Location $repoRoot
try {
    $changed = @(git diff --name-only -- $protected)
    if ($LASTEXITCODE -ne 0) {
        throw 'git diff fallo durante la auditoria de archivos protegidos.'
    }
    if ($changed.Count -ne 0) {
        throw "Se modificaron archivos Java protegidos: $($changed -join ', ')"
    }
}
finally {
    Pop-Location
}

Write-Host 'SourceAudit: OK'

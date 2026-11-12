# CHANUO A1 read-only Fastboot probe - Windows PowerShell 5.1+
param([string]$FastbootPath = '')
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$bundle = Split-Path -Parent $here
$parent = Split-Path -Parent $bundle
$logDir = Join-Path $here 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$log = Join-Path $logDir ('fastboot_probe_{0}.txt' -f (Get-Date -Format 'yyyyMMdd_HHmmss'))

function Say([string]$message) {
    $message | Tee-Object -FilePath $log -Append
}
function Resolve-Fastboot {
    param([string]$Manual)
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($Manual)) { $candidates += $Manual }
    if (-not [string]::IsNullOrWhiteSpace($env:FASTBOOT_EXE)) { $candidates += $env:FASTBOOT_EXE }
    $candidates += @(
        (Join-Path $here 'platform-tools\fastboot.exe'),
        (Join-Path $bundle 'platform-tools\fastboot.exe'),
        (Join-Path $parent 'platform-tools\fastboot.exe'),
        (Join-Path $parent 'Android\platform-tools\fastboot.exe'),
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\fastboot.exe'),
        (Join-Path $env:USERPROFILE 'platform-tools\fastboot.exe'),
        (Join-Path $env:USERPROFILE 'Downloads\platform-tools\fastboot.exe')
    )
    foreach ($path in $candidates) {
        if ([string]::IsNullOrWhiteSpace($path)) { continue }
        try {
            if (Test-Path -LiteralPath $path -PathType Leaf) { return (Resolve-Path -LiteralPath $path).Path }
        } catch { }
    }
    $fromPath = Get-Command 'fastboot.exe' -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }
    return $null
}
function Query([string[]]$argsList) {
    Say ('> fastboot ' + ($argsList -join ' '))
    try {
        $output = (& $script:fastboot @argsList 2>&1 | Out-String)
        $code = $LASTEXITCODE
        $safe = $output -replace '(?im)(serialno|serialnumber|imei)\s*[:=]\s*\S+', '$1: [OMITIDO]'
        if ([string]::IsNullOrWhiteSpace($safe)) { Say '[Sem resposta]' } else { Say $safe.TrimEnd() }
        Say ('ExitCode: ' + $code)
        return $output
    } catch {
        Say ('ERRO AO CONSULTAR: ' + $_.Exception.Message)
        return ''
    }
}

Say ('Chanuo A1 - teste somente de consulta, ' + (Get-Date))
$script:fastboot = Resolve-Fastboot -Manual $FastbootPath
if (-not $script:fastboot) {
    Say 'ERRO: fastboot.exe NAO ENCONTRADO. Nenhum teste de conexao foi executado.'
    Say 'Baixe Android SDK Platform-Tools: https://developer.android.com/tools/releases/platform-tools'
    Say 'Ou informe -FastbootPath com o caminho completo do fastboot.exe.'
    Say ('Relatorio: ' + $log)
    exit 2
}
Say ('Executavel: ' + $script:fastboot)
Say 'ATENCAO: PreLoader USB VCOM (COM5/COM6) nao e o mesmo que Fastboot.'
$null = Query @('--version')
$found = Query @('devices','-l')
$foundLines = @($found -split "`r?`n" | Where-Object { $_ -match '^\S+\s+fastboot(?:\s|$)' })
if ($foundLines.Count -ne 1) {
    Say ('Dispositivos fastboot: ' + $foundLines.Count + '. Nao consultaremos getvar.')
    Say 'O projetor deve estar EM FASTBOOT MODE e o Windows precisa do driver adequado.'
    Say ('Relatorio: ' + $log)
    exit 0
}
foreach ($variable in @('product','version-bootloader','unlocked','secure','max-download-size')) {
    $null = Query @('getvar',$variable)
}
Say ('Relatorio: ' + $log)
Say 'Nenhum comando de flash, erase, unlock ou boot foi executado.'

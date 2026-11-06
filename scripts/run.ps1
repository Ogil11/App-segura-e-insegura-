<#
  run.ps1 - Lanzador para PowerShell (Windows): ejecuta cualquier script .sh
  de esta carpeta con Git Bash, para no duplicar la lógica en dos lenguajes.

  Ejemplos:
    .\scripts\run.ps1 setup-env
    .\scripts\run.ps1 run-backend
    .\scripts\run.ps1 start-emulator --sin-proxy
    .\scripts\run.ps1 ataque-almacenamiento

  Requiere Git for Windows (trae bash.exe). Si PowerShell bloquea el script:
    Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
#>
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Script,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$Argumentos
)

$candidatos = @(
    "$env:ProgramFiles\Git\bin\bash.exe",
    "${env:ProgramFiles(x86)}\Git\bin\bash.exe",
    "$env:LOCALAPPDATA\Programs\Git\bin\bash.exe"
)
$bash = $candidatos | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $bash) {
    Write-Error "No se encontró Git Bash. Instálalo con: winget install -e --id Git.Git"
    exit 1
}

$nombre = $Script
if (-not $nombre.EndsWith(".sh")) { $nombre = "$nombre.sh" }
$ruta = Join-Path $PSScriptRoot $nombre
if (-not (Test-Path $ruta)) { Write-Error "No existe $ruta"; exit 1 }

& $bash $ruta @Argumentos
exit $LASTEXITCODE

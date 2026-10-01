$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $projectRoot

$javaHomes = @(
    'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot',
    'C:\Program Files\Java\jdk-21.0.11',
    'C:\Program Files\Java\jdk-17'
)
$javaHome = $javaHomes | Where-Object {
    Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe')
} | Select-Object -First 1
if (-not $javaHome) {
    throw 'JDK 17 ou plus recent introuvable.'
}

$env:JAVA_HOME = $javaHome
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'

# NeoGradle's server run uses run\server as its game directory. Keep its
# world, EULA, server properties and launcher marker together in that folder.
$serverRunDirectory = Join-Path $projectRoot 'run\server'
New-Item -ItemType Directory -Force -Path $serverRunDirectory | Out-Null
$serverPidFile = Join-Path $serverRunDirectory '.customrecipe-server-launcher.json'

# Gradle can detach the actual Java game process from the PowerShell window.
# A stale PID file would then miss it, leaving world/session.lock held and
# making the next runServer stop immediately.  Restrict this sweep to Java
# commands that explicitly launch this project's NeoForge development server.
$escapedProjectRoot = [regex]::Escape($projectRoot)
$staleServerProcesses = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object {
        $_.CommandLine -match $escapedProjectRoot -and
        ($_.CommandLine -match 'forgeserverdev' -or $_.CommandLine -match 'gradle-wrapper\.jar"?\s+runServer')
    }
foreach ($staleServer in $staleServerProcesses) {
    Write-Host "Arrêt de l'ancien serveur NeoForge (PID $($staleServer.ProcessId))..."
    & taskkill.exe /PID $staleServer.ProcessId /T /F | Out-Null
}

if (Test-Path -LiteralPath $serverPidFile) {
    try {
        $previous = Get-Content -LiteralPath $serverPidFile -Raw | ConvertFrom-Json
        $oldProcess = Get-Process -Id $previous.processId -ErrorAction SilentlyContinue
        if ($oldProcess -and $oldProcess.StartTime.ToFileTimeUtc() -eq [long]$previous.startTime) {
            Write-Host "Arrêt de l'ancien serveur (PID $($previous.processId))..."
            & taskkill.exe /PID $previous.processId /T /F | Out-Null
        }
    } catch {
        Write-Warning "Impossible de lire l'ancien processus serveur : $($_.Exception.Message)"
    }
    Remove-Item -LiteralPath $serverPidFile -Force -ErrorAction SilentlyContinue
}

$lanIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -ne '127.0.0.1' -and $_.IPAddress -notlike '169.254.*' } |
    Select-Object -First 1 -ExpandProperty IPAddress
if (-not $lanIp) { $lanIp = '127.0.0.1' }

$serverEula = Join-Path $serverRunDirectory 'eula.txt'
if (-not (Test-Path -LiteralPath $serverEula) -or (Get-Content -LiteralPath $serverEula -Raw) -notmatch '(?m)^eula=true\s*$') {
    $accept = Read-Host 'Le serveur Minecraft exige EULA=true. Tape OUI pour accepter'
    if ($accept -ne 'OUI') { throw 'EULA non acceptée : lancement annulé.' }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $serverEula) | Out-Null
    Set-Content -LiteralPath $serverEula -Value 'eula=true' -NoNewline
}

# Le client de développement Forge n'a pas de session Microsoft authentifiée.
# Ce réglage ne concerne que run-server, jamais un serveur de production.
$serverProperties = Join-Path $serverRunDirectory 'server.properties'
$properties = if (Test-Path -LiteralPath $serverProperties) {
    Get-Content -LiteralPath $serverProperties -Raw
} else {
    ''
}
if ($properties -match '(?m)^online-mode=') {
    $properties = $properties -replace '(?m)^online-mode=.*$', 'online-mode=false'
} else {
    $properties += "`r`nonline-mode=false`r`n"
}
if ($properties -match '(?m)^white-list=') {
    $properties = $properties -replace '(?m)^white-list=.*$', 'white-list=false'
} else {
    $properties += "white-list=false`r`n"
}
if ($properties -match '(?m)^enforce-whitelist=') {
    $properties = $properties -replace '(?m)^enforce-whitelist=.*$', 'enforce-whitelist=false'
} else {
    $properties += "enforce-whitelist=false`r`n"
}
Set-Content -LiteralPath $serverProperties -Value $properties -NoNewline

# Le client de développement utilise le compte hors ligne \"Dev\". Il doit être
# opérateur pour exécuter les tests de recettes sans authentification Microsoft.
$devOperator = [pscustomobject]@{
    uuid = '380df991-f603-344c-a090-369bad2a924a'
    name = 'Dev'
    level = 4
    bypassesPlayerLimit = $false
}
$opsPath = Join-Path $serverRunDirectory 'ops.json'
$operators = if (Test-Path -LiteralPath $opsPath) {
    @(Get-Content -LiteralPath $opsPath -Raw | ConvertFrom-Json)
} else {
    @()
}
$operators = @($operators | Where-Object { $_ -and $_.name -ne 'Dev' }) + $devOperator
ConvertTo-Json -InputObject $operators | Set-Content -LiteralPath $opsPath -NoNewline

Write-Host 'Compilation du mod...'
& '.\gradlew.bat' build --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Compilation échouée : lancement annulé.' }

# Le build unique évite une course entre runServer et runClient sur les classes du mod.
$serverScript = "`$host.UI.RawUI.WindowTitle = 'Custom Recipe NeoForge 1.21.11 - Serveur de test'; `$env:JAVA_HOME = '$javaHome'; `$env:GRADLE_USER_HOME = '$env:GRADLE_USER_HOME'; Set-Location -LiteralPath '$projectRoot'; & '.\gradlew.bat' runServer --console=plain --no-daemon -x compileJava -x processResources -x classes"
$clientScript = "`$env:JAVA_HOME = '$javaHome'; `$env:GRADLE_USER_HOME = '$env:GRADLE_USER_HOME'; Set-Location -LiteralPath '$projectRoot'; & '.\gradlew.bat' runClient --no-daemon -x compileJava -x processResources -x classes"

Write-Host "Serveur : $lanIp`:25565"
Write-Host 'Mode local de développement : online-mode=false.'
Write-Host 'Le serveur démarre dans une fenêtre dédiée, puis le client dans 6 secondes.'
$serverLauncher = Start-Process -FilePath 'powershell.exe' -WorkingDirectory $projectRoot -ArgumentList '-NoExit', '-NoProfile', '-Command', $serverScript -PassThru
@{
    processId = $serverLauncher.Id
    startTime = $serverLauncher.StartTime.ToFileTimeUtc()
} | ConvertTo-Json | Set-Content -LiteralPath $serverPidFile -NoNewline
Start-Sleep -Seconds 6
Start-Process -FilePath 'powershell.exe' -WorkingDirectory $projectRoot -ArgumentList '-NoExit', '-NoProfile', '-Command', $clientScript

Write-Host "Dans le client : Multijoueur > Ajouter un serveur > $lanIp`:25565"

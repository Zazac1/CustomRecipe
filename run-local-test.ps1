$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $projectRoot

Write-Host 'Choisis la version Minecraft à tester :'
Write-Host '  1. Minecraft 26.1'
Write-Host '  2. Minecraft 26.1.1'
Write-Host '  3. Minecraft 26.1.2'
$choice = Read-Host 'Choix (1-3)'

switch ($choice) {
    '1' {
        $minecraftVersion = '26.1'
        $fabricApiVersion = '0.145.1+26.1'
        $testModProfile = '26.1'
        $profileNotice = 'Waystones et Shogi n''ont pas de version Fabric officielle pour Minecraft 26.1 : seul Balm sera chargé.'
    }
    '2' {
        $minecraftVersion = '26.1.1'
        $fabricApiVersion = '0.145.4+26.1.1'
        $testModProfile = '26.1.1'
        $profileNotice = 'Waystones n''a pas de version Fabric officielle pour Minecraft 26.1.1 : Balm et Shogi seront chargés.'
    }
    '3' {
        $minecraftVersion = '26.1.2'
        $fabricApiVersion = '0.155.3+26.1.2'
        $testModProfile = '26.1.2'
        $profileNotice = $null
    }
    default {
        throw 'Choix invalide. Relance le script et choisis 1, 2 ou 3.'
    }
}

$gradleVersionArgs = @(
    "-Pminecraft_version=$minecraftVersion"
    "-Pfabric_api_version=$fabricApiVersion"
)
Write-Host "Cible : Minecraft $minecraftVersion avec Fabric API $fabricApiVersion"
if ($profileNotice) { Write-Warning $profileNotice }

$javaHomes = Get-ChildItem -LiteralPath 'C:\Program Files\Java' -Directory -Filter 'jdk-25*' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending |
    Select-Object -ExpandProperty FullName
$javaHome = $javaHomes | Where-Object {
    Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe')
} | Select-Object -First 1
if (-not $javaHome) {
    throw 'JDK 25 introuvable. Installe un JDK 25 dans C:\Program Files\Java.'
}
$env:JAVA_HOME = $javaHome
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'

$serverPidFile = Join-Path $projectRoot 'run-server\.customrecipe-server-launcher.json'
$worldLock = Join-Path $projectRoot 'run-server\world\session.lock'
$modProfileDirectory = Join-Path $projectRoot "run-client\mod-profiles\$testModProfile"

function Set-TestModProfile {
    param([string]$sourceDirectory)

    if (-not (Test-Path -LiteralPath $sourceDirectory -PathType Container)) {
        throw "Profil de mods introuvable : $sourceDirectory"
    }

    $profileMods = Get-ChildItem -LiteralPath $sourceDirectory -Filter '*.jar' -File
    if ($profileMods.Count -eq 0) {
        throw "Le profil $testModProfile ne contient aucun mod."
    }

    foreach ($runDirectory in @('run-client', 'run-server')) {
        $modsDirectory = Join-Path $projectRoot "$runDirectory\mods"
        New-Item -ItemType Directory -Force -Path $modsDirectory | Out-Null
        Get-ChildItem -LiteralPath $modsDirectory -Filter '*.jar' -File |
            Remove-Item -Force
        Copy-Item -LiteralPath $profileMods.FullName -Destination $modsDirectory -Force
    }

    Write-Host "Profil de mods actif : $testModProfile ($($profileMods.Name -join ', '))"
}

function Test-ExclusiveFileAccess([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return $true }
    try {
        $handle = [System.IO.File]::Open(
            $path,
            [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::ReadWrite,
            [System.IO.FileShare]::None
        )
        $handle.Dispose()
        return $true
    } catch [System.IO.IOException] {
        return $false
    }
}

function Wait-ForWorldUnlock([int]$timeoutSeconds = 12) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    do {
        if (Test-ExclusiveFileAccess $worldLock) { return $true }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    return $false
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

# Never let a second development server crash on the existing world lock.
# A server stopped by the tracked launcher can take a few seconds to release it.
if (-not (Wait-ForWorldUnlock)) {
    $listener = Get-NetTCPConnection -LocalPort 25565 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty OwningProcess
    $owner = if ($listener) { " (PID $listener)" } else { '' }
    throw "Le monde run-server est encore utilisÃ©$owner. Ferme l'ancien serveur, puis relance ce script."
}

# Chaque version Minecraft reçoit uniquement les JARs déclarés compatibles.
# Cela supprime les anciens JARs du dossier de lancement avant runServer/runClient.
Set-TestModProfile -sourceDirectory $modProfileDirectory

$lanIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -ne '127.0.0.1' -and $_.IPAddress -notlike '169.254.*' } |
    Select-Object -First 1 -ExpandProperty IPAddress
if (-not $lanIp) { $lanIp = '127.0.0.1' }

$serverEula = Join-Path $projectRoot 'run-server\eula.txt'
if (-not (Test-Path -LiteralPath $serverEula) -or (Get-Content -LiteralPath $serverEula -Raw) -notmatch '(?m)^eula=true\s*$') {
    $accept = Read-Host 'Le serveur Minecraft exige EULA=true. Tape OUI pour accepter'
    if ($accept -ne 'OUI') { throw 'EULA non acceptée : lancement annulé.' }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $serverEula) | Out-Null
    Set-Content -LiteralPath $serverEula -Value 'eula=true' -NoNewline
}

# Le client de développement Fabric n'a pas de session Microsoft authentifiée.
# Ce réglage ne concerne que run-server, jamais un serveur de production.
$serverProperties = Join-Path $projectRoot 'run-server\server.properties'
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
Set-Content -LiteralPath $serverProperties -Value $properties -NoNewline

Write-Host 'Compilation du mod...'
& '.\gradlew.bat' build --no-daemon @gradleVersionArgs
if ($LASTEXITCODE -ne 0) { throw 'Compilation échouée : lancement annulé.' }

# Le build unique évite une course entre runServer et runClient sur les classes du mod.
$serverScript = "`$env:JAVA_HOME = '$javaHome'; `$env:GRADLE_USER_HOME = '$env:GRADLE_USER_HOME'; Set-Location -LiteralPath '$projectRoot'; & '.\gradlew.bat' runServer --no-daemon -x compileJava -x processResources -x classes '-Pminecraft_version=$minecraftVersion' '-Pfabric_api_version=$fabricApiVersion'"
$clientScript = "`$env:JAVA_HOME = '$javaHome'; `$env:GRADLE_USER_HOME = '$env:GRADLE_USER_HOME'; Set-Location -LiteralPath '$projectRoot'; & '.\gradlew.bat' runClient --no-daemon -x compileJava -x processResources -x classes '-Pminecraft_version=$minecraftVersion' '-Pfabric_api_version=$fabricApiVersion'"

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

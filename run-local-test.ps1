$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $projectRoot

$javaHome = 'C:\Program Files\Java\jdk-21.0.11'
if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe'))) {
    throw "JDK 21 introuvable : $javaHome"
}

$env:JAVA_HOME = $javaHome
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'

$serverPidFile = Join-Path $projectRoot 'run-server\.customrecipe-server-launcher.json'
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
& '.\gradlew.bat' build
if ($LASTEXITCODE -ne 0) { throw 'Compilation échouée : lancement annulé.' }

# Le build unique évite une course entre runServer et runClient sur les classes du mod.
$serverScript = "`$env:JAVA_HOME = '$javaHome'; `$env:GRADLE_USER_HOME = '$env:GRADLE_USER_HOME'; Set-Location -LiteralPath '$projectRoot'; & '.\gradlew.bat' runServer -x compileJava -x processResources -x classes"
$clientScript = "`$env:JAVA_HOME = '$javaHome'; `$env:GRADLE_USER_HOME = '$env:GRADLE_USER_HOME'; Set-Location -LiteralPath '$projectRoot'; & '.\gradlew.bat' runClient -x compileJava -x processResources -x classes"

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

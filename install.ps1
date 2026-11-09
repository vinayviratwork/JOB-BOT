#Requires -Version 5.1
# JobBot Install and Run Script for Windows
# Usage:
#   .\install.ps1              - full setup and run
#   .\install.ps1 -SkipDocker  - skip Docker (MySQL already running)
#   .\install.ps1 -TestOnly    - run tests only, do not start server
#   .\install.ps1 -RebuildDb   - drop and recreate database volume
param([switch]$SkipDocker, [switch]$TestOnly, [switch]$RebuildDb)

# Fix execution policy
$pol = Get-ExecutionPolicy -Scope CurrentUser
if ($pol -eq "Restricted" -or $pol -eq "Undefined") {
    Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser -Force
    Write-Host "  [OK] Execution policy fixed" -ForegroundColor Green
}

$ErrorActionPreference = "Stop"
function Write-Step { param($m) Write-Host "`n>> $m" -ForegroundColor Cyan }
function Write-Ok   { param($m) Write-Host "   [OK] $m" -ForegroundColor Green }
function Write-Warn { param($m) Write-Host "   [!!] $m" -ForegroundColor Yellow }
function Write-Info { param($m) Write-Host "   [..] $m" -ForegroundColor Gray }
function Write-Fail { param($m) Write-Host "`n   [FAIL] $m`n" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   JobBot - Install and Run              " -ForegroundColor Cyan
Write-Host "   Cost: FREE (Groq AI + Docker MySQL)   " -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan

# =============================================================================
# STEP 1 - Java 21
# =============================================================================
Write-Step "Checking Java 21"
$javaExe = $null

# Check pre-installed location first (already on this machine)
$knownJava = "C:\Users\vinay\.jobbot\java21\bin\java.exe"
if (Test-Path $knownJava) {
    $javaExe = $knownJava
    $env:JAVA_HOME = "C:\Users\vinay\.jobbot\java21"
    $env:PATH = "C:\Users\vinay\.jobbot\java21\bin;" + $env:PATH
    Write-Ok "Java 21 found at $javaExe"
} else {
    # Search standard locations
    $patterns = @(
        "C:\Program Files\Eclipse Adoptium\jdk-21*\bin\java.exe",
        "C:\Program Files\Eclipse Adoptium\jdk-22*\bin\java.exe",
        "C:\Program Files\Microsoft\jdk-21*\bin\java.exe",
        "C:\Program Files\Java\jdk-21*\bin\java.exe"
    )
    foreach ($pat in $patterns) {
        $f = Get-Item $pat -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($f) { $javaExe = $f.FullName; break }
    }
    if (-not $javaExe) {
        $fp = Get-Command java -ErrorAction SilentlyContinue
        if ($fp) { $javaExe = $fp.Source }
    }
    if ($javaExe) {
        $vout = & $javaExe -version 2>&1 | Out-String
        if ($vout -match '"(\d+)') {
            $major = [int]$Matches[1]
            if ($major -ge 21) {
                Write-Ok "Java $major found at $javaExe"
                $env:JAVA_HOME = Split-Path (Split-Path $javaExe)
                $env:PATH = (Split-Path $javaExe) + ";" + $env:PATH
            } else {
                Write-Warn "Java $major found but 21+ needed. Trying winget..."
                $javaExe = $null
            }
        }
    }
}

if (-not $javaExe) {
    $wg = Get-Command winget -ErrorAction SilentlyContinue
    if ($wg) {
        Write-Info "Installing Java 21 via winget (may show UAC prompt)..."
        winget install EclipseAdoptium.Temurin.21.JDK --silent --accept-package-agreements --accept-source-agreements
        $env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("PATH","User")
        $fp = Get-Command java -ErrorAction SilentlyContinue
        if ($fp) { $javaExe = $fp.Source; Write-Ok "Java 21 installed" }
        else { Write-Fail "Java installed but not on PATH. Restart PowerShell and re-run." }
    } else {
        Write-Fail "Java 21 not found. Install from https://adoptium.net/ then restart PowerShell."
    }
}

# =============================================================================
# STEP 2 - Maven
# =============================================================================
Write-Step "Checking Maven"
$mvnExe = $null

$knownMvn = "C:\Users\vinay\.jobbot\maven\bin\mvn.cmd"
if (Test-Path $knownMvn) {
    $mvnExe = $knownMvn
    $env:MAVEN_HOME = "C:\Users\vinay\.jobbot\maven"
    $env:PATH = "C:\Users\vinay\.jobbot\maven\bin;" + $env:PATH
    Write-Ok "Maven found at $mvnExe"
} else {
    $fp = Get-Command mvn -ErrorAction SilentlyContinue
    if ($fp) { $mvnExe = $fp.Source; Write-Ok "Maven found on PATH" }
}

if (-not $mvnExe) {
    Write-Warn "Maven not found. Auto-downloading Maven 3.9.6..."
    $url  = "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"
    $zip  = "$env:TEMP\mvn396.zip"
    $dest = "$env:USERPROFILE\.jobbot\maven"
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
        $tmp = "$env:TEMP\mvn-extract"
        Expand-Archive -Path $zip -DestinationPath $tmp -Force
        $ex = Get-Item "$tmp\apache-maven-3.9.6" -ErrorAction SilentlyContinue
        if ($ex) {
            New-Item -ItemType Directory -Path (Split-Path $dest) -Force | Out-Null
            if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
            Move-Item $ex.FullName $dest -Force
        }
        Remove-Item $zip,$tmp -Recurse -Force -ErrorAction SilentlyContinue
        $mvnExe = "$dest\bin\mvn.cmd"
        $env:PATH = "$dest\bin;" + $env:PATH
        Write-Ok "Maven installed to $dest"
    } catch { Write-Fail "Maven download failed: $($_.Exception.Message)" }
}

# =============================================================================
# STEP 3 - Docker
# =============================================================================
if (-not $SkipDocker) {
    Write-Step "Checking Docker"
    try {
        docker --version 2>&1 | Out-Null
        Write-Ok "Docker installed"
    } catch {
        Write-Host "" 
        Write-Host "  Docker Desktop is NOT installed." -ForegroundColor Red
        Write-Host "  This is the only manual step needed." -ForegroundColor Yellow
        Write-Host ""
        Write-Host "  Steps:" -ForegroundColor Yellow
        Write-Host "  1. Go to: https://www.docker.com/products/docker-desktop/" -ForegroundColor White
        Write-Host "  2. Click Download for Windows and install it" -ForegroundColor White
        Write-Host "  3. Start Docker Desktop from Start Menu" -ForegroundColor White
        Write-Host "  4. Wait for 'Engine running' in taskbar" -ForegroundColor White
        Write-Host "  5. Run: .\install.ps1  again" -ForegroundColor White
        Write-Host ""
        Start-Process "https://www.docker.com/products/docker-desktop/"
        exit 1
    }
    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Fail "Docker installed but not running. Start Docker Desktop then retry." }
    Write-Ok "Docker engine running"
}

# =============================================================================
# STEP 4 - Port 3306
# =============================================================================
if (-not $SkipDocker) {
    Write-Step "Checking port 3306"
    $p3306 = netstat -ano 2>$null | Select-String ":3306 "
    if ($p3306) {
        Write-Warn "Port 3306 in use. Trying to stop local MySQL..."
        foreach ($svc in @("MySQL","MySQL80","MySQL57","MySQL56","MariaDB")) {
            $s = Get-Service -Name $svc -ErrorAction SilentlyContinue
            if ($s -and $s.Status -eq "Running") {
                try { Stop-Service -Name $svc -Force; Write-Ok "Stopped $svc"; Start-Sleep 2 }
                catch { Write-Warn "Could not stop $svc - try: net stop $svc as Admin" }
            }
        }
        $still = netstat -ano 2>$null | Select-String ":3306 "
        if ($still) { Write-Warn "Port 3306 still in use. Docker may fail. Use -SkipDocker if needed." }
        else { Write-Ok "Port 3306 now free" }
    } else { Write-Ok "Port 3306 free" }
}

# =============================================================================
# STEP 5 - .env
# =============================================================================
Write-Step "Loading .env"
$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) {
    Copy-Item (Join-Path $PSScriptRoot ".env.example") $envFile
    Write-Host "  .env created. Opening Notepad..." -ForegroundColor Yellow
    Write-Host "  Set DB_PASSWORD and GROQ_API_KEY then save and re-run .\install.ps1" -ForegroundColor Yellow
    Start-Process notepad.exe -ArgumentList $envFile
    exit 0
}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line -match "^([^=]+)=(.*)$") {
        $k = $Matches[1].Trim()
        $v = $Matches[2].Trim()
        [System.Environment]::SetEnvironmentVariable($k, $v, "Process")
    }
}

# Validate GROQ_API_KEY
$apiKey = $env:GROQ_API_KEY
if ([string]::IsNullOrWhiteSpace($apiKey) -or $apiKey -like "gsk_xxx*") {
    Write-Host ""
    Write-Host "  GROQ_API_KEY is not set or still placeholder." -ForegroundColor Red
    Write-Host "  Get your FREE key at https://console.groq.com/" -ForegroundColor Yellow
    Write-Host "  Sign up -> API Keys -> Create Key -> copy the gsk_... key" -ForegroundColor Yellow
    Write-Host "  Then paste it in .env as:  GROQ_API_KEY=gsk_yourkey" -ForegroundColor Yellow
    Start-Process "https://console.groq.com/"
    Start-Process notepad.exe -ArgumentList $envFile
    exit 1
}
Write-Ok "GROQ_API_KEY loaded"

$dbPass = $env:DB_PASSWORD
if ([string]::IsNullOrWhiteSpace($dbPass)) {
    [System.Environment]::SetEnvironmentVariable("DB_PASSWORD","changeme","Process")
    Write-Warn "DB_PASSWORD not set - using default: changeme"
} else { Write-Ok "DB_PASSWORD loaded" }

# =============================================================================
# STEP 6 - Resume check
# =============================================================================
Write-Step "Checking resume"
$rp = Join-Path $PSScriptRoot "src\main\resources\my-resume.txt"
if (Test-Path $rp) {
    $rc = Get-Content $rp -Raw
    if ($rc -match "PASTE YOUR RESUME HERE") {
        Write-Warn "my-resume.txt has placeholder text."
        Write-Info "Option A: Edit src\main\resources\my-resume.txt"
        Write-Info "Option B: Upload PDF via dashboard after app starts"
    } else { Write-Ok "Resume loaded ($($rc.Length) chars)" }
} else { Write-Warn "my-resume.txt not found - upload PDF via dashboard" }

# =============================================================================
# STEP 7 - Companies
# =============================================================================
$cp = Join-Path $PSScriptRoot "src\main\resources\target-companies.txt"
$tk = Get-Content $cp -ErrorAction SilentlyContinue | Where-Object { $_ -notmatch "^\s*#" -and $_.Trim() -ne "" }
if ($tk -and $tk.Count -gt 0) { Write-Ok "target-companies.txt: $($tk.Count) entries" }
else { Write-Warn "No entries in target-companies.txt - add companies before scanning" }

# =============================================================================
# STEP 8 - Start MySQL
# =============================================================================
if (-not $SkipDocker) {
    Write-Step "Starting MySQL 8 via Docker"
    Push-Location $PSScriptRoot
    try {
        if ($RebuildDb) {
            Write-Warn "RebuildDb: removing old volume..."
            docker compose down -v 2>$null
        }
        docker compose up -d
        if ($LASTEXITCODE -ne 0) { Write-Fail "docker compose up failed" }
        Write-Ok "Docker Compose started"
        Write-Host "   Waiting for MySQL" -NoNewline -ForegroundColor Gray
        $ready = $false; $tries = 0
        while (-not $ready -and $tries -lt 30) {
            Start-Sleep 2
            Write-Host "." -NoNewline -ForegroundColor Gray
            $tries++
            docker exec jobbot-mysql mysqladmin ping -u jobbot --password="$($env:DB_PASSWORD)" --silent 2>&1 | Out-Null
            if ($LASTEXITCODE -eq 0) { $ready = $true }
        }
        Write-Host ""
        if ($ready) { Write-Ok "MySQL is ready" }
        else { Write-Warn "MySQL health check timed out - may still be starting" }
    } finally { Pop-Location }
} else { Write-Warn "Skipping Docker. Ensure MySQL on localhost:3306 db=jobbot" }

# =============================================================================
# STEP 9 - Build
# =============================================================================
Write-Step "Building project"
$m2 = "$env:USERPROFILE\.m2\repository\org\springframework\boot"
if (Test-Path $m2) { Write-Ok "Maven cache found - fast build" }
else { Write-Info "First build: downloading dependencies (one-time, may take a few minutes)" }

Push-Location $PSScriptRoot
try {
    & $mvnExe package -DskipTests -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  BUILD FAILED. Run: mvn package  for full error details" -ForegroundColor Red
        exit 1
    }
    Write-Ok "Build successful"
} finally { Pop-Location }

# =============================================================================
# STEP 10 - Tests only mode
# =============================================================================
if ($TestOnly) {
    Write-Step "Running tests"
    Push-Location $PSScriptRoot
    try {
        & $mvnExe test
        if ($LASTEXITCODE -ne 0) { Write-Fail "Tests failed" }
        Write-Ok "All tests passed"
    } finally { Pop-Location }
    exit 0
}

# =============================================================================
# STEP 11 - Launch
# =============================================================================
Write-Step "Launching JobBot"
Write-Host ""
Write-Host "  +------------------------------------------+" -ForegroundColor Green
Write-Host "  |  Dashboard  ->  http://localhost:8080     |" -ForegroundColor Green
Write-Host "  |  Swagger UI ->  http://localhost:8080/swagger-ui.html  |" -ForegroundColor Green
Write-Host "  |  Cost: FREE (Groq AI)                     |" -ForegroundColor Green
Write-Host "  +------------------------------------------+" -ForegroundColor Green
Write-Host ""
Write-Host "  Press Ctrl+C to stop." -ForegroundColor Gray
Write-Host ""
Start-Job -ScriptBlock { Start-Sleep 12; Start-Process "http://localhost:8080" } | Out-Null
Push-Location $PSScriptRoot
try {
    & $mvnExe spring-boot:run "-Dspring-boot.run.jvmArguments=-Xmx512m" "-Dspring.datasource.password=$($env:DB_PASSWORD)" "-Dai.groq.api-key=$($env:GROQ_API_KEY)"
} finally { Pop-Location }

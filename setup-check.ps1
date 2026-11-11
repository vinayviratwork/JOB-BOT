#Requires -Version 5.1
param()
$ok=0; $warn=0; $fail=0
function Check-Ok   { param($l,$m) Write-Host "  [OK]   $l : $m" -ForegroundColor Green;  $script:ok++ }
function Check-Warn { param($l,$m) Write-Host "  [WARN] $l : $m" -ForegroundColor Yellow; $script:warn++ }
function Check-Fail {
    param($l,$m,$f)
    Write-Host "  [FAIL] $l : $m" -ForegroundColor Red
    if ($f) { Write-Host "         FIX: $f" -ForegroundColor Cyan }
    $script:fail++
}
Write-Host ""
Write-Host "JobBot Setup Diagnostics" -ForegroundColor Cyan
Write-Host "----------------------------------------"
try {
    $jv = java -version 2>&1 | Out-String
    if ($jv -match '"(\d+)') {
        $major = [int]$Matches[1]
        if ($major -ge 21) { Check-Ok "Java" "version $major" }
        else { Check-Fail "Java" "version $major need 21+" "Install from https://adoptium.net/ then restart PowerShell" }
    } else { Check-Warn "Java" "version unreadable" }
} catch { Check-Fail "Java" "not found" "Install JDK 21 from https://adoptium.net/ then restart PowerShell" }
try {
    $mv = mvn --version 2>&1 | Out-String
    if ($mv -match "Apache Maven") { Check-Ok "Maven" "found" }
    else { Check-Fail "Maven" "not found" "Run .\install.ps1 it will auto-download Maven" }
} catch { Check-Fail "Maven" "not found" "Run .\install.ps1 it will auto-download Maven" }
try {
    docker --version 2>&1 | Out-Null
    Check-Ok "Docker" "installed"
    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { Check-Ok "Docker engine" "running" }
    else { Check-Fail "Docker engine" "not running" "Start Docker Desktop from Start Menu" }
} catch { Check-Fail "Docker" "not found" "Install from https://www.docker.com/products/docker-desktop/" }
$p = netstat -ano 2>$null | Select-String ":3306 "
if ($p) { Check-Warn "Port 3306" "in use - install.ps1 will stop local MySQL" }
else    { Check-Ok   "Port 3306" "free" }
$ef = Join-Path $PSScriptRoot ".env"
if (Test-Path $ef) {
    $ec = Get-Content $ef -Raw
    if ($ec -match "GROQ_API_KEY=(.+)") {
        $k = $Matches[1].Trim()
        if ($k.Length -lt 20 -or $k -like "gsk_xxx*") { Check-Fail "API_KEY" "placeholder" "Get real key from https://console.groq.com/keys" }
        elseif ($k -match "^gsk_") { Check-Ok "API_KEY" "set correctly" }
        else { Check-Warn "API_KEY" "does not start with gsk_" }
    } else { Check-Fail "API_KEY" "missing" "Add GROQ_API_KEY=gsk_... to .env" }
    if ($ec -match "DB_PASSWORD=(.+)") {
        if ($Matches[1].Trim() -eq "changeme") { Check-Warn "DB_PASSWORD" "changeme - works but consider changing" }
        else { Check-Ok "DB_PASSWORD" "set" }
    } else { Check-Warn "DB_PASSWORD" "missing - default will be used" }
} else { Check-Fail ".env" "not found" "Copy .env.example to .env and fill in your values" }
$rp = Join-Path $PSScriptRoot "src\main\resources\my-resume.txt"
if (Test-Path $rp) {
    $rc = Get-Content $rp -Raw
    if ($rc -match "PASTE YOUR RESUME HERE") { Check-Warn "Resume" "placeholder - upload PDF via dashboard" }
    else { Check-Ok "Resume" "$($rc.Length) chars loaded" }
} else { Check-Warn "Resume" "not found - upload PDF via dashboard after start" }
$cp = Join-Path $PSScriptRoot "src\main\resources\target-companies.txt"
$tk = Get-Content $cp -ErrorAction SilentlyContinue | Where-Object { $_ -notmatch "^\s*#" -and $_.Trim() -ne "" }
if ($tk -and $tk.Count -gt 0) { Check-Ok "Companies" "$($tk.Count) entries" }
else { Check-Warn "Companies" "no entries in target-companies.txt" }
$m2 = "$env:USERPROFILE\.m2\repository\org\springframework\boot"
if (Test-Path $m2) { Check-Ok "Maven cache" "found - fast build" }
else { Check-Warn "Maven cache" "empty - first build downloads 200 MB one time only" }
$pol = Get-ExecutionPolicy -Scope CurrentUser
if ($pol -eq "Restricted") { Check-Fail "ExecutionPolicy" "Restricted" "Set-ExecutionPolicy RemoteSigned -Scope CurrentUser" }
else { Check-Ok "ExecutionPolicy" "$pol" }
Write-Host ""
Write-Host "  Results: $ok passed  $warn warnings  $fail failed"
Write-Host ""
if ($fail -eq 0) { Write-Host "  All good! Run: .\install.ps1" -ForegroundColor Green }
else { Write-Host "  Fix issues above then run: .\install.ps1" -ForegroundColor Red }
Write-Host ""

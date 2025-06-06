# PowerShell script for copying filters to JamesDSP
# Provides more advanced features like progress bar and filtering

param(
    [Parameter(Mandatory=$false)]
    [string]$SourcePath = "D:\FIR-Filter-Maker-for-Equal-Loudness--Loudness\48000",
    
    [Parameter(Mandatory=$false)]
    [string]$Pattern = "*.wav",
    
    [Parameter(Mandatory=$false)]
    [string]$ListeningLevel = "",
    
    [Parameter(Mandatory=$false)]
    [string]$ReferenceLevel = "",
    
    [Parameter(Mandatory=$false)]
    [switch]$ShowProgress = $true
)

$DestPath = "/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "JamesDSP Filter Copy Tool (PowerShell)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host

# Build filter pattern
if ($ListeningLevel -and $ReferenceLevel) {
    $Pattern = "$ListeningLevel-$ReferenceLevel*.wav"
} elseif ($ListeningLevel) {
    $Pattern = "$ListeningLevel-*.wav"
} elseif ($ReferenceLevel) {
    $Pattern = "*-$ReferenceLevel*.wav"
}

# Check source directory
if (-not (Test-Path $SourcePath)) {
    Write-Host "Error: Source directory not found: $SourcePath" -ForegroundColor Red
    exit 1
}

# Get matching files
$files = Get-ChildItem -Path $SourcePath -Filter $Pattern
$totalFiles = $files.Count

if ($totalFiles -eq 0) {
    Write-Host "No files matching pattern '$Pattern' found" -ForegroundColor Yellow
    exit 1
}

Write-Host "Found $totalFiles files matching: $Pattern" -ForegroundColor Green
Write-Host "Source: $SourcePath"
Write-Host "Destination: $DestPath"
Write-Host

# Create destination directory
& C:\adb\adb.exe shell mkdir -p $DestPath 2>$null

# Copy files with progress
$copied = 0
$failed = 0
$startTime = Get-Date

foreach ($file in $files) {
    $percent = [math]::Round(($copied + $failed) / $totalFiles * 100)
    
    if ($ShowProgress) {
        Write-Progress -Activity "Copying filters" -Status "$($file.Name)" -PercentComplete $percent
    } else {
        Write-Host "Copying: $($file.Name)"
    }
    
    $result = & C:\adb\adb.exe push $file.FullName "$DestPath/$($file.Name)" 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        $copied++
    } else {
        $failed++
        Write-Host "  Failed: $($file.Name)" -ForegroundColor Red
    }
}

$endTime = Get-Date
$duration = $endTime - $startTime

if ($ShowProgress) {
    Write-Progress -Activity "Copying filters" -Completed
}

Write-Host
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Copy operation completed!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Total files: $totalFiles"
Write-Host "Copied: $copied" -ForegroundColor Green
Write-Host "Failed: $failed" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Green" })
Write-Host "Duration: $($duration.TotalSeconds) seconds"
Write-Host

# Verify files on device
Write-Host "Verifying files on device..."
$deviceFiles = & C:\adb\adb.exe shell ls $DestPath/*.wav 2>$null | Measure-Object -Line
Write-Host "Files on device: $($deviceFiles.Lines)" -ForegroundColor Cyan
param(
    [ValidateSet('A','B')][string]$Mode = 'A',
    [ValidateSet('Start','Collect')][string]$Action = 'Start',
    [string]$RunDirectory,
    [int]$WarmupFrames = 300,
    [string]$Profile = "$env:APPDATA\ModrinthApp\profiles\prova",
    [string]$Launcher = "$env:LOCALAPPDATA\Modrinth App\Modrinth App.exe"
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Assert-Stopped {
    if (Get-Process -Name 'Modrinth App' -ErrorAction SilentlyContinue) {
        throw 'Close Modrinth completely first.'
    }
    if (Get-Process -Name javaw -ErrorAction SilentlyContinue) {
        throw 'Close Minecraft first.'
    }
}

function Percentile([double[]]$Values, [double]$Quantile) {
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Max(0, [Math]::Ceiling($Quantile * $sorted.Count) - 1)
    return $sorted[$index]
}

if ($Action -eq 'Collect') {
    Assert-Stopped
    if (-not $RunDirectory) { throw 'Specify the run directory printed by Start.' }
    $run = (Resolve-Path -LiteralPath $RunDirectory).Path
    $manifestPath = Join-Path $run 'manifest.json'
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $latest = Join-Path $manifest.profile 'logs\latest.log'
    $csv = Join-Path $manifest.profile 'rt-frame-stats\frame.csv'
    foreach ($required in @($latest, $csv, $manifest.jar)) {
        if (-not (Test-Path -LiteralPath $required)) { throw "Required evidence missing: $required" }
    }
    $started = [datetime]$manifest.startedUtc
    if ((Get-Item -LiteralPath $latest).LastWriteTimeUtc -lt $started -or
        (Get-Item -LiteralPath $csv).LastWriteTimeUtc -lt $started) {
        throw 'Log or frame CSV predates this run.'
    }
    if ((Get-FileHash -LiteralPath $manifest.jar -Algorithm SHA256).Hash -ne $manifest.sha256) {
        throw 'Candidate JAR changed since Start.'
    }
    if (Test-Path -LiteralPath (Join-Path $run 'frame.csv')) { throw 'Run already collected.' }
    Copy-Item -LiteralPath $latest -Destination (Join-Path $run 'minecraft.log')
    Copy-Item -LiteralPath $csv -Destination (Join-Path $run 'frame.csv')
    $rows = @(Import-Csv -LiteralPath $csv)
    if ($rows.Count -le $WarmupFrames + 600) {
        throw "Need more than $($WarmupFrames + 600) profiled frames; found $($rows.Count)."
    }
    $samples = @($rows | Select-Object -Skip $WarmupFrames | ForEach-Object { [double]$_.totalMs })
    $average = ($samples | Measure-Object -Average).Average
    $metrics = [ordered]@{
        mode = $manifest.mode
        sha256 = $manifest.sha256
        totalFrames = $rows.Count
        warmupFramesDiscarded = $WarmupFrames
        sampleFrames = $samples.Count
        averageMs = [Math]::Round($average, 4)
        p95Ms = [Math]::Round((Percentile $samples 0.95), 4)
        p99Ms = [Math]::Round((Percentile $samples 0.99), 4)
    }
    $metrics | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'metrics.json')
    $metrics | Format-List
    Write-Host "Evidence saved to $run"
    exit
}

Assert-Stopped
foreach ($required in @($Launcher, $Profile)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required path missing: $required" }
}
$jars = @(Get-ChildItem -LiteralPath (Join-Path $Profile 'mods') -Filter 'caustica*.jar')
if ($jars.Count -ne 1) { throw 'Expected exactly one active Caustica JAR.' }
$run = Join-Path $repo ('build\rewrite-validation\GATE-8-performance\' + $Mode + '-' +
        [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $run | Out-Null
$enabled = if ($Mode -eq 'B') { 'true' } else { 'false' }
$options = @(
    "-Dengine.renderGraphV2=$enabled",
    "-Dengine.postBarriersV2=$enabled",
    "-Dengine.denoiserBarriersV2=$enabled",
    "-Dengine.upscalerBarriersV2=$enabled",
    "-Dengine.pathTraceBarriersV2=$enabled",
    '-Dcaustica.rt.frameStats=true',
    '-Dcaustica.rt.dlssRr=true',
    '-Dcaustica.rt.denoiser=true',
    '-Dcaustica.rt.fsr=false',
    '-Dcaustica.rt.xess=false',
    '-Dcaustica.rt.nrd=false',
    '-Dcaustica.rt.fg=false'
) -join ' '
@{
    mode = $Mode
    startedUtc = [datetime]::UtcNow.ToString('o')
    profile = $Profile
    jar = $jars[0].FullName
    sha256 = (Get-FileHash -LiteralPath $jars[0].FullName -Algorithm SHA256).Hash
    generatedGraphAndBarriers = $enabled
    validationRequested = $false
    workload = 'DLSS_RR, frame generation off, fixed world and stationary sample'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'manifest.json')
$previous = @{}
$values = @{
    JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS $options"
    VK_INSTANCE_LAYERS = ''
    VK_LAYER_PATH = ''
    VK_LAYER_SETTINGS_PATH = ''
}
try {
    foreach ($key in $values.Keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
    }
    Start-Process -FilePath $Launcher -WindowStyle Hidden
} finally {
    foreach ($key in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process')
    }
}
Write-Host "GATE-8 performance run $Mode prepared: $run"
Write-Host 'Use the fixed world/view. Warm up 30 seconds, then remain stationary for at least 60 seconds.'
Write-Host 'Exit Minecraft and Modrinth, then Collect this directory before starting the other mode.'

param(
    [ValidateSet('Start','Collect')][string]$Action = 'Start',
    [string]$RunDirectory,
    [string]$Profile = "$env:APPDATA\ModrinthApp\profiles\prova",
    [string]$Launcher = "$env:LOCALAPPDATA\Modrinth App\Modrinth App.exe",
    [string]$VulkanSdk = 'C:\VulkanSDK\1.4.357.0'
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

if ($Action -eq 'Collect') {
    Assert-Stopped
    if (-not $RunDirectory) { throw 'Specify the run directory printed by Start.' }
    $run = (Resolve-Path -LiteralPath $RunDirectory).Path
    $manifest = Get-Content -LiteralPath (Join-Path $run 'manifest.json') -Raw | ConvertFrom-Json
    $latest = Join-Path $manifest.profile 'logs\latest.log'
    $validation = Join-Path $run 'validation.log'
    foreach ($required in @($latest, $validation, $manifest.jar)) {
        if (-not (Test-Path -LiteralPath $required)) { throw "Required evidence missing: $required" }
    }
    if ((Get-Item -LiteralPath $latest).LastWriteTimeUtc -lt [datetime]$manifest.startedUtc) {
        throw 'latest.log predates this run.'
    }
    if ((Get-FileHash -LiteralPath $manifest.jar -Algorithm SHA256).Hash -ne $manifest.sha256) {
        throw 'Candidate JAR changed since Start.'
    }
    if (Test-Path -LiteralPath (Join-Path $run 'minecraft.log')) { throw 'Run already collected.' }
    $validationText = Get-Content -LiteralPath $validation -Raw
    if ($validationText -notmatch '(?s)CURRENT-VALIDATION-ENABLED.{0,1000}Synchronization') {
        throw 'Synchronization-validation activation marker missing.'
    }
    $requiredMarkers = @(
        'AER-083 path-trace barriers: path=generated',
        'AER-083 upscaler barriers: path=generated',
        'AER-083 post barriers: path=generated',
        'DLSS-RR feature created'
    )
    foreach ($marker in $requiredMarkers) {
        if (@(Select-String -LiteralPath $latest -SimpleMatch $marker).Count -eq 0) {
            throw "Required runtime marker missing: $marker"
        }
    }
    Copy-Item -LiteralPath $latest -Destination (Join-Path $run 'minecraft.log')
    Select-String -LiteralPath $latest -Pattern 'AER-083 .*barriers:|DLSS-RR feature created' |
        ForEach-Object Line | Set-Content -LiteralPath (Join-Path $run 'runtime-markers.txt')
    Select-String -LiteralPath $validation -Pattern 'VUID-|SYNC-HAZARD' |
        ForEach-Object Line | Set-Content -LiteralPath (Join-Path $run 'validation-findings.txt')
    Write-Host "GATE-8 Vulkan evidence saved to $run."
    exit
}

Assert-Stopped
foreach ($required in @($Launcher, $Profile, (Join-Path $VulkanSdk 'Bin\VkLayer_khronos_validation.json'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required path missing: $required" }
}
$jars = @(Get-ChildItem -LiteralPath (Join-Path $Profile 'mods') -Filter 'caustica*.jar')
if ($jars.Count -ne 1) { throw 'Expected exactly one active Caustica JAR.' }
$run = Join-Path $repo ('build\rewrite-validation\GATE-8-validation\B-' +
        [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $run | Out-Null
$validationLog = (Join-Path $run 'validation.log').Replace('\','/')
@(
    'khronos_validation.validate_sync = true',
    'khronos_validation.syncval_submit_time_validation = true',
    'khronos_validation.debug_action = VK_DBG_LAYER_ACTION_LOG_MSG',
    'khronos_validation.report_flags = info,warn,error',
    'khronos_validation.enable_message_limit = false',
    "khronos_validation.log_filename = $validationLog"
) | Set-Content -LiteralPath (Join-Path $run 'vk_layer_settings.txt') -Encoding ascii
@{
    startedUtc = [datetime]::UtcNow.ToString('o')
    profile = $Profile
    jar = $jars[0].FullName
    sha256 = (Get-FileHash -LiteralPath $jars[0].FullName -Algorithm SHA256).Hash
    graphAndGeneratedBarriers = $true
    validationRequested = $true
    workload = 'DLSS_RR, first frame/reset, stationary, movement, menu, reload, shutdown'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'manifest.json')
$previous = @{}
$values = @{
    JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Dcaustica.rt.dlssRr=true -Dcaustica.rt.denoiser=true -Dcaustica.rt.fsr=false -Dcaustica.rt.xess=false -Dcaustica.rt.nrd=false -Dcaustica.rt.fg=false"
    VK_INSTANCE_LAYERS = 'VK_LAYER_KHRONOS_validation'
    VK_LAYER_PATH = (Join-Path $VulkanSdk 'Bin')
    VK_LAYER_SETTINGS_PATH = $run
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
Write-Host "GATE-8 Vulkan smoke prepared: $run"
Write-Host 'Launch prova. Test first frame/reset, 30 seconds stationary, movement/rotation, menu and world reload.'
Write-Host 'Exit Minecraft and Modrinth before Collect.'

param(
    [ValidateSet('A','B')][string]$Mode = 'A',
    [ValidateSet('Start','Collect')][string]$Action = 'Start',
    [string]$RunDirectory,
    [string]$Profile = "$env:APPDATA\ModrinthApp\profiles\prova",
    [string]$Launcher = "$env:LOCALAPPDATA\Modrinth App\Modrinth App.exe",
    [string]$VulkanSdk = 'C:\VulkanSDK\1.4.357.0'
)
$ErrorActionPreference = 'Stop'
if ($Action -eq 'Start') {
    throw 'Historical A/B harness retired by AER-092: the legacy branch no longer exists. Use gate8-validation-smoke.ps1 for current runtime validation; use a historical checkout for historical A/B.'
}
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

if ($Action -eq 'Collect') {
    if (-not $RunDirectory) { throw 'Specify the run directory printed by Start.' }
    $run = (Resolve-Path -LiteralPath $RunDirectory).Path
    $manifest = Get-Content -LiteralPath (Join-Path $run 'manifest.json') -Raw | ConvertFrom-Json
    $latest = Join-Path $manifest.profile 'logs\latest.log'
    if ((Get-Item -LiteralPath $latest).LastWriteTimeUtc -lt [datetime]$manifest.startedUtc) {
        throw 'latest.log predates this run; no evidence collected.'
    }
    $currentHash = (Get-FileHash -LiteralPath $manifest.jar -Algorithm SHA256).Hash
    if ($currentHash -ne $manifest.sha256) { throw 'Candidate JAR changed since Start.' }
    if (Test-Path -LiteralPath (Join-Path $run 'minecraft.log')) { throw 'Run already collected; evidence will not be overwritten.' }
    Copy-Item -LiteralPath $latest -Destination (Join-Path $run 'minecraft.log')
    $expected = if ($manifest.mode -eq 'B') { 'generated' } else { 'legacy' }
    $markers = @(Select-String -LiteralPath $latest -Pattern "AER-083 post barriers: path=$expected")
    $markers.Line | Set-Content -LiteralPath (Join-Path $run 'path-markers.txt')
    if ($markers.Count -eq 0) { throw 'Expected A/B runtime marker missing. Evidence retained, run NOT QUALIFIED.' }
    if (-not (Test-Path -LiteralPath (Join-Path $run 'validation.log'))) {
        throw 'Validation log missing. Evidence retained, layer activation NOT PROVEN.'
    }
    $validationText = Get-Content -LiteralPath (Join-Path $run 'validation.log') -Raw
    if ($validationText -notmatch '(?s)CURRENT-VALIDATION-ENABLED.{0,1000}Synchronization') {
        throw 'Explicit synchronization-validation activation marker missing. Run NOT QUALIFIED.'
    }
    Write-Host "Evidence saved to $run. NEEDS REVIEW: layer activation, errors, scenarios and A/B comparison."
    exit
}

if (Get-Process -Name 'Modrinth App' -ErrorAction SilentlyContinue) {
    throw 'Close Modrinth completely first so the new launcher inherits the validation environment.'
}
if (Get-Process -Name javaw -ErrorAction SilentlyContinue) { throw 'Close Minecraft before starting an A/B run.' }
foreach ($required in @($Launcher, $Profile, (Join-Path $VulkanSdk 'Bin\VkLayer_khronos_validation.json'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required path missing: $required" }
}
$jars = @(Get-ChildItem -LiteralPath (Join-Path $Profile 'mods') -Filter 'caustica*.jar')
if ($jars.Count -ne 1) { throw 'Expected exactly one active Caustica JAR.' }
$run = Join-Path $repo ('build\rewrite-validation\AER-083-post\' + $Mode + '-' + [guid]::NewGuid().ToString('N'))
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
$generated = if ($Mode -eq 'B') { 'true' } else { 'false' }
@{mode=$Mode; startedUtc=[datetime]::UtcNow.ToString('o'); profile=$Profile;
    jar=$jars[0].FullName; sha256=(Get-FileHash -LiteralPath $jars[0].FullName -Algorithm SHA256).Hash;
    graph='true'; generatedPostBarriers=$generated; validationRequested=$true
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'manifest.json')
$previous = @{}
$values = @{
    JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Dengine.renderGraphV2=true -Dengine.postBarriersV2=$generated";
    VK_INSTANCE_LAYERS = 'VK_LAYER_KHRONOS_validation';
    VK_LAYER_PATH = (Join-Path $VulkanSdk 'Bin');
    VK_LAYER_SETTINGS_PATH = $run
}
try {
    foreach ($key in $values.Keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
    }
    Start-Process -FilePath $Launcher -WindowStyle Hidden
} finally {
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
Write-Host "Run $Mode prepared: $run"
Write-Host 'Launch only prova, perform the documented scenarios, then exit Minecraft and Modrinth.'
Write-Host 'Collect this run before launching the next one. Validation runs are not performance benchmarks.'

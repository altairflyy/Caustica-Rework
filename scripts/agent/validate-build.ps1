$ErrorActionPreference = "Stop"

$ExpectedFailures = @(
    "dev.comfyfluffy.caustica.rt.RtParallaxShaderRegressionTest::sideWallsReplaceTheMappedNormalOnBothHitPaths",
    "dev.comfyfluffy.caustica.rt.RtParallaxShaderRegressionTest::blockSpritesTileWhileEntityAtlasesStopAtTheirIsland",
    "dev.comfyfluffy.caustica.rt.RtWaterWaveShaderRegressionTest::continuationOriginsStayOffTheRestPlaneMesh",
    "dev.comfyfluffy.caustica.rt.RtWaterWaveShaderRegressionTest::animatedWaterIntersectsTheHeightFieldAlongTheViewRay"
)

function Fail-Validation {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message,
        [int]$Code = 1
    )

    Write-Host "[validate-build] FAIL: $Message" -ForegroundColor Red
    exit $Code
}

function Ensure-NgxShim {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $shimPath = Join-Path $RepoRoot "build\native\ngx_shim\release\ngxshim.dll"

    if (Test-Path $shimPath) {
        $shim = Get-Item $shimPath
        Write-Host "[validate-build] NGX shim present: $($shim.Length) bytes"
        return
    }

    Write-Host "[validate-build] NGX shim missing; rebuilding it..."

    if (-not (Get-Command cmake -ErrorAction SilentlyContinue)) {
        Fail-Validation "cmake is not available in PATH"
    }

    if ([string]::IsNullOrWhiteSpace($env:DLSS_SDK)) {
        $candidate = Join-Path (Split-Path $RepoRoot -Parent) "dlss-sdk"

        if (Test-Path $candidate) {
            $env:DLSS_SDK = $candidate
            Write-Host "[validate-build] DLSS_SDK=$candidate"
        }
        else {
            Fail-Validation "DLSS_SDK is unset and no sibling dlss-sdk directory was found"
        }
    }

    if ([string]::IsNullOrWhiteSpace($env:VULKAN_SDK)) {
        $sdk = Get-ChildItem "C:\VulkanSDK" -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            Select-Object -First 1

        if ($null -ne $sdk) {
            $env:VULKAN_SDK = $sdk.FullName
            Write-Host "[validate-build] VULKAN_SDK=$($sdk.FullName)"
        }
    }

    $cmakeBuild = Join-Path $RepoRoot "build\cmake\ngx_shim\release"

    & cmake `
        -S (Join-Path $RepoRoot "native\ngx_shim") `
        -B $cmakeBuild `
        -DCMAKE_BUILD_TYPE=Release

    if ($LASTEXITCODE -ne 0) {
        Fail-Validation "NGX shim CMake configure failed"
    }

    & cmake --build $cmakeBuild --config Release

    if ($LASTEXITCODE -ne 0) {
        Fail-Validation "NGX shim build failed"
    }

    if (-not (Test-Path $shimPath)) {
        Fail-Validation "NGX shim build completed but ngxshim.dll was not produced"
    }

    $shim = Get-Item $shimPath
    Write-Host "[validate-build] NGX shim rebuilt: $($shim.Length) bytes"
}

function Invoke-BaselineAwareTests {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    $results = Join-Path $RepoRoot "build\test-results\test"

    Remove-Item $results -Recurse -Force -ErrorAction SilentlyContinue

    Write-Host "[validate-build] V1: Gradle test + baseline comparison"

    & .\gradlew.bat test --rerun-tasks `
        -PngxShimConfig=release `
        -PngxVendorConfig=rel

    $gradleExit = $LASTEXITCODE

    if (-not (Test-Path $results)) {
        Fail-Validation "Gradle produced no test results (exit code $gradleExit)"
    }

    $xmlFiles = @(Get-ChildItem $results -Filter "TEST-*.xml" -File)

    if ($xmlFiles.Count -eq 0) {
        Fail-Validation "No JUnit XML results were produced (Gradle exit code $gradleExit)"
    }

    $actualFailures = @()
    $characterizationFound = $false
    $characterizationPass = $false

    foreach ($file in $xmlFiles) {
        [xml]$xml = Get-Content $file.FullName -Raw
        $suite = $xml.testsuite

        if ($suite.name -eq "dev.comfyfluffy.caustica.rt.RtRewriteCharacterizationTest") {
            $characterizationFound = $true

            if (
                [int]$suite.tests -eq 7 -and
                [int]$suite.failures -eq 0 -and
                [int]$suite.errors -eq 0 -and
                [int]$suite.skipped -eq 0
            ) {
                $characterizationPass = $true
            }
        }

        foreach ($tc in $suite.testcase) {
            if ($null -ne $tc.failure -or $null -ne $tc.error) {
                $testName = [string]$tc.name
                $testName = $testName -replace '\(\)$', ''

                $actualFailures += "$($tc.classname)::$testName"
            }
        }
    }

    if (-not $characterizationFound) {
        Fail-Validation "RtRewriteCharacterizationTest was not executed"
    }

    if (-not $characterizationPass) {
        Fail-Validation "RtRewriteCharacterizationTest is not 7/7 PASS"
    }

    $expected = @($ExpectedFailures | Sort-Object)
    $actual   = @($actualFailures | Sort-Object)

    $unexpected = @($actual | Where-Object { $_ -notin $expected })
    $missing    = @($expected | Where-Object { $_ -notin $actual })

    if ($unexpected.Count -gt 0) {
        Write-Host "[validate-build] Unexpected failures:" -ForegroundColor Red
        $unexpected | ForEach-Object { Write-Host "  + $_" }
    }

    if ($missing.Count -gt 0) {
        Write-Host "[validate-build] Expected baseline failures missing:" -ForegroundColor Red
        $missing | ForEach-Object { Write-Host "  - $_" }
    }

    if (
        $actual.Count -ne $expected.Count -or
        $unexpected.Count -gt 0 -or
        $missing.Count -gt 0
    ) {
        Fail-Validation "test failure set differs from the frozen 4-test baseline"
    }

    Write-Host "[validate-build] Characterization: 7/7 PASS"
    Write-Host "[validate-build] Baseline failures: exact 4/4 match (BASELINE-EQUIVALENT)"
    Write-Host "[validate-build] Gradle test exit code $gradleExit accepted because only frozen baseline failures remain"
}

$repoRoot = (& git rev-parse --show-toplevel 2>$null)

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
    Write-Error "[validate-build] ERROR: not inside a Git worktree"
    exit 2
}

Push-Location $repoRoot

try {
    Write-Host "[validate-build] V0: git diff --check"

    & git diff --check

    if ($LASTEXITCODE -ne 0) {
        Fail-Validation "git diff --check failed"
    }

    Ensure-NgxShim -RepoRoot $repoRoot

    Invoke-BaselineAwareTests -RepoRoot $repoRoot

    Write-Host "[validate-build] V2: Gradle build (tests already baseline-validated)"

    & .\gradlew.bat build -x test `
        -PngxShimConfig=release `
        -PngxVendorConfig=rel

    if ($LASTEXITCODE -ne 0) {
        Fail-Validation "Gradle build failed"
    }

    Write-Host "[validate-build] PASS" -ForegroundColor Green
}
finally {
    Pop-Location
}

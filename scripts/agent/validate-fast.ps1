$ErrorActionPreference = "Stop"

$ExpectedFailures = @(
    "dev.comfyfluffy.caustica.rt.RtParallaxShaderRegressionTest::sideWallsReplaceTheMappedNormalOnBothHitPaths",
    "dev.comfyfluffy.caustica.rt.RtParallaxShaderRegressionTest::blockSpritesTileWhileEntityAtlasesStopAtTheirIsland",
    "dev.comfyfluffy.caustica.rt.RtParallaxShaderRegressionTest::crossingBudgetFromJavaStaysInsideTheShaderBounds",
    "dev.comfyfluffy.caustica.rt.RtParallaxShaderRegressionTest::columnHeightsAreTexelExactAndStayInsideTheSprite",
    "dev.comfyfluffy.caustica.rt.RtParallaxShaderRegressionTest::everyHeightSampleIsBoundedByTheCrossingBudget",
    "dev.comfyfluffy.caustica.rt.RtParallaxShaderRegressionTest::reliefDepthStillCollapsesWhenPomIsDisabled",
    "dev.comfyfluffy.caustica.rt.RtParallaxShaderRegressionTest::heightFieldIsWalkedAsAColumnGridNotAsDepthLayers",
    "dev.comfyfluffy.caustica.rt.RtWaterWaveShaderRegressionTest::continuationOriginsStayOffTheRestPlaneMesh",
    "dev.comfyfluffy.caustica.rt.RtWaterWaveShaderRegressionTest::animatedWaterIntersectsTheHeightFieldAlongTheViewRay"
)

function Fail {
    param([string]$Message)

    Write-Host "[validate-fast] FAIL: $Message" -ForegroundColor Red
    exit 1
}

$repoRoot = (& git rev-parse --show-toplevel 2>$null)

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
    Write-Error "[validate-fast] ERROR: not inside a Git worktree"
    exit 2
}

Push-Location $repoRoot

try {
    Write-Host "[validate-fast] V0: git diff --check"

    & git diff --check

    if ($LASTEXITCODE -ne 0) {
        Fail "git diff --check failed"
    }

    Write-Host "[validate-fast] V1: Gradle tests + frozen baseline comparison"

    Remove-Item ".\build\test-results\test" `
        -Recurse -Force -ErrorAction SilentlyContinue

    & .\gradlew.bat test `
        -PngxShimConfig=release `
        -PngxVendorConfig=rel

    $gradleExit = $LASTEXITCODE

    $xmlFiles = @(
        Get-ChildItem ".\build\test-results\test\TEST-*.xml" `
            -File -ErrorAction SilentlyContinue
    )

    if ($xmlFiles.Count -eq 0) {
        Fail "Gradle produced no JUnit XML results"
    }

    $actualFailures = @()
    $characterization = $null

    foreach ($file in $xmlFiles) {
        [xml]$xml = Get-Content $file.FullName -Raw
        $suite = $xml.testsuite

        if ($suite.name -eq "dev.comfyfluffy.caustica.rt.RtRewriteCharacterizationTest") {
            $characterization = $suite
        }

        foreach ($tc in $suite.testcase) {
            if ($null -ne $tc.failure -or $null -ne $tc.error) {
                $name = ([string]$tc.name) -replace '\(\)$', ''
                $actualFailures += "$($tc.classname)::$name"
            }
        }
    }

    if ($null -eq $characterization) {
        Fail "RtRewriteCharacterizationTest was not executed"
    }

    if (
        [int]$characterization.tests -ne 7 -or
        [int]$characterization.failures -ne 0 -or
        [int]$characterization.errors -ne 0 -or
        [int]$characterization.skipped -ne 0
    ) {
        Fail "RtRewriteCharacterizationTest is not 7/7 PASS"
    }

    $expected = @($ExpectedFailures | Sort-Object)
    $actual   = @($actualFailures | Sort-Object)

    $unexpected = @(
        $actual | Where-Object { $_ -notin $expected }
    )

    $missing = @(
        $expected | Where-Object { $_ -notin $actual }
    )

    if ($unexpected.Count -gt 0) {
        Write-Host "[validate-fast] Unexpected failures:" -ForegroundColor Red
        $unexpected | ForEach-Object {
            Write-Host "  + $_"
        }
    }

    if ($missing.Count -gt 0) {
        Write-Host "[validate-fast] Missing baseline failures:" -ForegroundColor Red
        $missing | ForEach-Object {
            Write-Host "  - $_"
        }
    }

    if (
        $actual.Count -ne 9 -or
        $unexpected.Count -ne 0 -or
        $missing.Count -ne 0
    ) {
        Fail "failure set differs from frozen baseline"
    }

    Write-Host "[validate-fast] Characterization: 7/7 PASS"
    Write-Host "[validate-fast] Baseline failures: exact 9/9 match"
    Write-Host "[validate-fast] Gradle exit code $gradleExit accepted"
    Write-Host "[validate-fast] PASS" -ForegroundColor Green
}
finally {
    Pop-Location
}

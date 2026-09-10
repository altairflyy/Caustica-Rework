$ErrorActionPreference = "Stop"

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

    Write-Host "[validate-fast] V1: Gradle tests"

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

    if ($actualFailures.Count -gt 0) {
        Write-Host "[validate-fast] Test failures:" -ForegroundColor Red
        $actualFailures | Sort-Object | ForEach-Object {
            Write-Host "  + $_"
        }
    }

    if ($gradleExit -ne 0 -or $actualFailures.Count -ne 0) {
        Fail "Gradle tests did not complete with zero failures"
    }

    Write-Host "[validate-fast] Characterization: 7/7 PASS"
    Write-Host "[validate-fast] Expected failures: 0"
    Write-Host "[validate-fast] Unexpected failures: 0"
    Write-Host "[validate-fast] Gradle exit code $gradleExit"
    Write-Host "[validate-fast] PASS" -ForegroundColor Green
}
finally {
    Pop-Location
}

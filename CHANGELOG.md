# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.1.2]

### Fixed
- Fixed animated water shoreline artifacts by keeping ray-traced water geometry on authoritative BLAS hits and applying waves to surface normals only.

## [0.1.1]

### Fixed
- Fixed animated water shoreline artifacts by keeping ray-traced water geometry on authoritative BLAS hits and applying waves to surface normals only.
- Corrected inverted moon-phase lighting.
- Corrected Frame Generation interpolation/effective generated-frame count when swapchain capacity limits generated frames.
- Corrected R11G11B10 subnormal→normal rounding carry.
- Prevented Halton jitter sequence corruption from signed counter overflow.

### Tests
- Fixed CRLF-sensitive shader regression checks on Windows.
- Expected test failures reduced from 4 to 0.
- Regression coverage added for the four production fixes.
- Current suite is 284 tests with 0 failures and 0 skipped.

## [0.1.0]

Initial public Caustica Rework release.

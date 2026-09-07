# AER-010 Result

## Task

AER-010 — GpuCapabilities read-only snapshot

## Goal

Separate the description of already-calculated GPU/device capabilities from
the Vulkan feature-enabling responsibilities of RtDeviceBringup.

## Changes

Added:

```text
src/main/java/dev/comfyfluffy/caustica/rt/device/GpuCapabilities.java
src/test/java/dev/comfyfluffy/caustica/rt/device/GpuCapabilitiesTest.java
Updated:
src/main/java/dev/comfyfluffy/caustica/rt/RtDeviceBringup.java
RtDeviceBringup.capabilities() now exposes an immutable read-only snapshot of
the legacy capability state.
Ownership
Unchanged.
RtDeviceBringup still owns:
- Vulkan extension selection
- Vulkan feature probing
- Vulkan feature enabling
- SER selection
- OMM gating
- Reflex / present-id gating
- XeSS gating
- device bring-up
- capability logging
GpuCapabilities owns no Vulkan resources and performs no probing or mutation.
Behaviour changes
None intended.
The extraction does not change:
- required Vulkan extensions
- optional Vulkan extensions
- SER gating
- OMM gating
- XeSS gating
- Reflex gating
- feature enablement
- capability log construction
Validation
Targeted test:
GpuCapabilitiesTest
1 passed
0 failed
Full validation:
131 tests
9 failures were reported by the then-current baseline harness; five were
later identified as CRLF-dependent characterization artifacts. The canonical
baseline is now the exact four genuine failures.
0 new failures
RtRewriteCharacterizationTest: 7/7 PASS
Validation scripts (at task time):
validate-fast.ps1: PASS
validate-build.ps1: PASS
Gradle build: PASS

Baseline erratum: no production or shader behavior changed. The canonical
four-failure policy is documented in `docs/rewrite/BASELINE.md`.
Invariants
- GPU-001: unchanged
- GPU-002: unchanged
- GPU-003: unchanged
- GPU-004: unchanged
- TMP-001..004: unchanged
- RST-001..004: unchanged
- LOD-001..007: unchanged
- REC-001: unchanged
- UPS-001..002: unchanged
State
DONE

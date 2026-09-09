# AER-092 result

Removed the unreferenced production `RewriteGates` class and its eleven
development keys. AER-091 already removed all runtime selection call sites;
this cleanup changes no rendering, ownership, math or synchronization behavior.

Removed only four tests of the deleted toggles. All barrier-plan, addressing,
hazard, mask and execution tests remain. `LegacyPathRemovalTest` now also
requires the obsolete class to be absent, without excluding it from its scan.

The current Vulkan smoke no longer sets ineffective development properties.
Five historical A/B harnesses reject Start before side effects: the current
runtime cannot compare two implementations. Collect/Analyze remain available
for historical evidence; original A/B runs require their historical checkout.
Historical result documents were not changed.

Validation:
- Targeted barrier-plan, graph execution and legacy-path tests: PASS.
- All five retired Start guards: PASS (expected explicit rejection).
- `validate-build.ps1`: V0/V1/V2 PASS, 246 tests, exact 4/4 canonical failures,
  characterization 7/7 PASS. Four fewer tests are exclusively deleted-toggle tests.
- Production search: no RewriteGates or engine.*V2 references.
- Full diff review and `git diff --check`: PASS.
- No shader/math, backend algorithm, resource lifetime or waitIdle changes.
- No new runtime qualification claimed by this cleanup; final smoke is separate.

State: DONE. AER-093 READY.

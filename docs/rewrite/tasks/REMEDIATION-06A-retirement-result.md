# REMEDIATION-06A result — frame-tail retirement seam

Status: COMPLETED

## Runtime integration

`FrameTailRetirement` is the narrow resource-owner contract. Its Vulkan implementation registers exactly one `Destroyable` with `VulkanCommandEncoder.queueForDestroy(...)` and never runs the destruction callback eagerly.

`RtContext` creates the implementation from `VulkanDevice.createCommandEncoder()`. That method returns the device-owned persistent encoder, whose existing submit timeline and two-submit destruction queue provide the real completion authority. No new timeline semaphore, frame-index surrogate, composite `GraphicsUse`, or wait-idle path was introduced.

The seam deliberately proves only registration into Blaze3D's established retirement mechanism. GPU completion behavior remains owned by the persistent encoder.

## Validation

- `VulkanFrameTailRetirementTest`: PASS; one registration, zero eager callbacks, one callback when the registered `Destroyable` is invoked.
- `validate-fast.ps1`: PASS; 259 tests, 255 passed, exact 4 canonical failures, 0 unexpected failures, characterization 7/7 PASS.
- `git diff --check`: PASS.

`FINAL` remains `PENDING`. No shader, validator, baseline, roadmap, or canonical-failure change was made.

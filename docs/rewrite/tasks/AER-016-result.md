# AER-016 result

Added the SVGF temporal-resource ownership container with explicit reset and
destruction lifecycle. The existing `RtSvgfDenoiser` dispatch registration,
descriptor layout, shader sources, and parity semantics remain unchanged.

Validation: targeted `SvgfResourcesTest` PASS.

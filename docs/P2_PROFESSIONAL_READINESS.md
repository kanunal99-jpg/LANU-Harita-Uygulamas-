# LANU Harita — P2 Professional Readiness

## Adaptive GPS sampling

The location manager now uses a deterministic sampling policy:

- Moving mode: 1 s target interval, 750 ms minimum interval, 2 m minimum displacement.
- Idle/slow mode: 4 s target interval, 2.5 s minimum interval, 8 m minimum displacement.
- Hysteresis: moving mode starts at 8 km/h and falls back to idle at or below 3 km/h.
- The policy is shared by the fused-location path and the Android system GPS fallback.
- Changing mode reconfigures providers without changing the navigation data model or inventing location fixes.

## Acceptance

Unit tests cover threshold hysteresis and the relative sampling budgets. Full acceptance additionally requires Android lint, unit tests, emulator instrumentation and debug APK CI.

## Remaining P2 field boundary

Automated CI does not replace long-duration physical-device battery, GPS, thermal and background-behavior measurements. Those remain a field acceptance stage.

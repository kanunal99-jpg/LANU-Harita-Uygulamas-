# LANU Harita — Production Readiness

This document records capabilities that are implemented and capabilities that are intentionally unavailable rather than simulated.

## Implemented and verified in CI

- Real GPS required for routing/navigation; no fabricated location fallback.
- GPS freshness/accuracy policy plus physical-jump rejection.
- Valhalla → OSRM → validated offline route-cache routing fallback.
- Provider geometry, metric and ETA sanity validation.
- Geometry-based navigation progress, maneuver progress and remaining ETA.
- Heading-aware route matching with bounded backtracking/hysteresis.
- Off-route detection and controlled reroute generation.
- MapLibre offline map/tile support.
- Truthful live-traffic status; no synthetic traffic values.
- Real weather provider with empty-on-failure behavior; no random weather.
- Search provider fallback and HTTP resource lifecycle protection.
- Safety-camera policy: real camera data only, warnings up to 5 km in 500 m buckets, session deduplication, and overspeed only when a real numeric limit is supplied.
- Deterministic Turkish navigation-voice wording for start, maneuver, safety-camera and arrival messages; the Android Text-to-Speech adapter remains local and network-independent.
- Explicit navigation performance budgets and regression tests.
- Android lint, unit tests, emulator instrumentation and debug APK CI gates.

## Intentionally not fabricated

### Full offline routing

The application currently supports validated cached routes offline. It does **not** claim to calculate arbitrary new routes without network access. A complete offline routing engine requires packaged routing graph/data and a maintained on-device engine; until that data and engine are integrated and tested on the target Android footprint, the application must not present an online route as an offline-calculated route.

### Live sharing

No fake sharing URL or pretend backend is used. Live sharing remains unavailable until a real backend, authentication/session model, location update protocol, expiry and revocation model are implemented and tested. No paid infrastructure is added automatically.

### Multiple live traffic providers

TomTom is the verified live provider currently configured. A second live provider is not invented. The UI remains explicit when live traffic cannot be verified.

### Field battery/performance validation

Automated performance budgets and regression tests are present. Long-duration battery, thermal and real-device route tests still require physical-device execution and are not claimed as completed by emulator CI.

## Quality rule

A feature is considered production-ready only after code review, unit tests, Android instrumentation, APK build, and SHA verification all pass. Missing external capabilities are represented as unavailable rather than simulated.

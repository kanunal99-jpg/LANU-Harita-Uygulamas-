# LANU Harita — Production Readiness

This document records capabilities that are implemented and capabilities that are intentionally unavailable rather than simulated.

## Implemented and verified in CI

- Real GPS required for routing/navigation; no fabricated location fallback.
- GPS freshness/accuracy policy plus physical-jump rejection.
- Valhalla → OSRM → validated offline route-cache routing fallback.
- Provider geometry, metric and ETA sanity validation.
- Geometry-based navigation progress, maneuver progress and remaining ETA.
- Off-route detection and controlled reroute generation.
- MapLibre offline map/tile support.
- Truthful live-traffic status; no synthetic traffic values.
- Real weather provider with empty-on-failure behavior; no random weather.
- Search provider fallback and HTTP resource lifecycle protection.
- Safety-camera warnings use real source data only, a 5 km warning envelope, 500 m TTS buckets and navigation-session reset behavior.
- Radar UI never invents a speed limit when source data is missing.
- Navigation dashboard displays speed in km/h and only evaluates overspeed when a real route speed limit is present.
- Android lint, unit tests, emulator instrumentation and debug APK CI gates.

## Intentionally not fabricated

### Full offline routing

The application currently supports validated cached routes offline. It does **not** claim to calculate arbitrary new routes without network access. A complete offline routing engine requires packaged routing graph/data and a maintained engine; until that is integrated and tested, the application must not present an online route as an offline-calculated route.

### Live sharing

No fake sharing URL or pretend backend is used. Live sharing remains unavailable until a real backend and expiry/revocation model are implemented and tested.

### Multiple live traffic providers

TomTom is the verified live provider currently configured. A second live provider is not invented. The UI remains explicit when live traffic cannot be verified.

## Quality rule

A feature is considered production-ready only after code review, unit tests, Android instrumentation, APK build, and SHA verification all pass. Missing external capabilities are represented as unavailable rather than simulated.

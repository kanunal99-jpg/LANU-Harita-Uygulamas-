# LANU Harita — Consolidated Navigation Completion

This document records the navigation capabilities that are implemented without fabricated data or placeholder services.

## Completed and verified in source/CI

- GPS map matching is distance- and heading-aware, with progress hysteresis to resist parallel-road jumps and backtracking.
- GPS lifecycle cleanup is explicit through `AppLocationManager.close()` from the ViewModel lifecycle.
- Turn-by-turn voice remains data-driven and avoids synthetic lane/speed-limit claims.
- Turkish TTS pronunciation normalizes LANU as a single word and preserves the requested departure/arrival safety messaging.
- Safety-camera warnings use real camera data only, warn from 5 km in 500 m buckets, deduplicate announcements per session and announce verified overspeed only when a real numeric limit is present.
- Live traffic remains truthful; TomTom is the verified live provider and historical/base behavior is used only as an explicitly non-live fallback.
- Weather uses a real provider with failure-to-empty behavior; no random weather is generated.
- Navigation scenarios have deterministic unit/instrumentation coverage for snapping, heading, backtracking, lifecycle and provider-fallback behavior.
- A real live-sharing backend is now deployed on Render with expiring bearer-token sessions, location updates and explicit revocation; the Android client creates, updates and revokes sessions.
- Android lint, unit tests, emulator instrumentation and debug APK CI gates remain mandatory.

## Explicit boundaries

### Full arbitrary offline routing — intentionally excluded from this consolidation

The app can navigate an already cached validated route offline. It does not calculate arbitrary new routes without network access because no packaged routing graph and on-device routing engine are present.

### Live sharing storage boundary

The live-sharing backend currently keeps active sessions in process memory. Sessions expire and are revoked explicitly, but a backend restart invalidates active sessions. No persistent database is falsely claimed as present.

### Second live traffic provider

No second live traffic API is fabricated. TomTom remains the only verified live provider currently configured; adding another real live provider requires a real provider/API configuration rather than a synthetic source.

### Field battery/performance validation

CI proves build and automated behavior only. Long-duration physical-device battery, GPS and thermal measurements still require an actual device run and are not represented as completed from emulator CI.

## Acceptance rule

A feature is accepted only after source-level review plus passing unit/instrumentation/lint/APK CI evidence. Missing external capabilities remain explicit rather than simulated.

# LANU Harita — Consolidated Navigation Completion

This change closes the navigation-quality gaps that can be implemented and verified entirely inside the Android application.

## Completed in this consolidation

- GPS map matching is distance- and heading-aware, with progress hysteresis to resist parallel-road jumps and backtracking.
- GPS lifecycle cleanup is explicit through `AppLocationManager.close()` from the ViewModel lifecycle.
- Turn-by-turn voice remains data-driven and avoids synthetic lane/speed-limit claims.
- Navigation scenarios have deterministic unit coverage for snapping, heading, backtracking and lifecycle-sensitive state.
- Existing route validation, traffic truthfulness and offline route-cache fallbacks remain intact.

## Intentionally not claimed as complete

### Full arbitrary offline routing

The app can navigate an already cached validated route offline. It does not calculate arbitrary new routes without network access because no packaged routing graph and on-device routing engine are present.

### Live sharing

Live sharing remains disabled until a real backend, authenticated share session, expiry/revocation and client polling/streaming path are integrated and tested. No placeholder URL is used.

### Second live traffic provider

No second live traffic API is fabricated. TomTom remains the only verified live provider currently configured.

## Acceptance rule

A feature is accepted only after source-level review plus passing unit/instrumentation/lint/APK CI evidence. Unavailable external capabilities remain explicitly unavailable instead of simulated.

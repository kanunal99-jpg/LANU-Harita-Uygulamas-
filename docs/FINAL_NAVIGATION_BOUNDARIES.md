# Final Navigation Capability Boundaries

## Implemented and testable

- Real/fresh GPS quality filtering.
- Route geometry validation and ETA/speed sanity checks.
- Valhalla -> OSRM routing fallback with validated route-cache fallback.
- Polyline snapping, route-progress calculation and consecutive off-route detection.
- Turkish native TTS navigation announcements and lifecycle-safe voice reset.
- Lane guidance is shown only when lane data is actually present on the maneuver.
- Unit, lint and Android instrumentation quality gates.

## Deliberately not simulated

- Arbitrary full offline routing is not claimed until a real offline routing engine and regional graph data are bundled and tested.
- Live sharing is not claimed until a real authenticated backend is integrated and tested.
- A second live traffic provider is not invented without a real, permitted data source.
- Traffic, ETA, speed limits, lane data and weather are never fabricated to make the UI look complete.

## Release rule

A capability is considered production-ready only after its implementation, regression tests, Android smoke coverage where applicable, and CI result are all available in GitHub records.

#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "QUALITY_GATE_ERROR: $1" >&2
  exit 1
}

test -s ANAYASA.md || fail "ANAYASA.md eksik veya boş"
grep -q 'Her anlamlı geliştirmede APK yayın zorunluluğu' ANAYASA.md || fail "anlamlı geliştirmelerde APK yayın kuralı Anayasa'da eksik"
test -s README.md || fail "README.md eksik veya boş"
test -s docs/PRODUCTION_READINESS.md || fail "production readiness belgesi eksik"

grep -q 'ANA SERVİS.*ALTERNATİF.*GERÇEK FALLBACK' ANAYASA.md || fail "kritik zincir standardı Anayasa'da bulunamadı"
grep -q 'versionName = "1.1.12"' app/build.gradle.kts || fail "beklenen Android sürümü 1.1.12 değil"
grep -q 'versionCode = 14' app/build.gradle.kts || fail "beklenen Android versionCode 14 değil"
grep -q 'android:allowBackup="false"' app/src/main/AndroidManifest.xml || fail "uygulama backup güvenlik kuralı kapalı değil"
grep -q 'android:foregroundServiceType="location"' app/src/main/AndroidManifest.xml || fail "aktif navigasyon location foreground service bildirimi eksik"
grep -q 'android.permission.FOREGROUND_SERVICE_LOCATION' app/src/main/AndroidManifest.xml || fail "foreground location permission eksik"
test -s app/src/main/java/com/example/haritalar/navigation/NavigationForegroundService.kt || fail "navigation foreground service sınıfı eksik"
test -s app/src/main/java/com/example/haritalar/navigation/LocationSamplingPolicy.kt || fail "adaptif GPS sampling policy eksik"
test -s app/src/main/java/com/example/haritalar/navigation/DestinationSnapPolicy.kt || fail "geniş alan hedeflerini rota ucuna güvenli snap policy eksik"
test -s app/src/main/java/com/example/haritalar/navigation/PoiSearchCenterPolicy.kt || fail "POI viewport arama merkezi policy eksik"
test -s app/src/test/java/com/example/haritalar/navigation/PoiSearchCenterPolicyTest.kt || fail "POI viewport regresyon testi eksik"
test -s app/src/test/java/com/example/haritalar/data/search/PostalCodeSearchTest.kt || fail "posta kodu regresyon testi eksik"
test -s app/src/test/java/com/example/haritalar/ui/MainViewModelSearchStateTest.kt || fail "arama/rota state regresyon testi eksik"
grep -q 'ENTER_MOVING_SPEED_KMH = 8.0f' app/src/main/java/com/example/haritalar/navigation/LocationSamplingPolicy.kt || fail "adaptif GPS moving eşiği korunmuyor"
grep -q 'IDLE_MIN_DISTANCE_METERS = 0.0f' app/src/main/java/com/example/haritalar/navigation/LocationSamplingPolicy.kt || fail "sabit cihaz GPS tazeliği hareket eşiğine takılıyor"
test ! -e app/src/main/java/com/example/DrivingBottomDashboard.kt || fail "duplicate sürüş dashboard'u yeniden eklendi"
grep -q 'NAVIGATION_PREFETCH_RADIUS_METERS = 8_000.0' app/src/main/java/com/example/haritalar/data/network/SafetyCameraAreaPolicy.kt || fail "radar son-5-km navigasyon prefetch buffer korunmuyor"
grep -q 'timeout-minutes: 45' .github/workflows/android-apk.yml || fail "APK pipeline timeout sınırı eksik"
grep -q 'actions/setup-java@v6.0.1' .github/workflows/android-apk.yml || fail "APK pipeline güncel Java action sürümünü kullanmıyor"
grep -q '"node": "24.x"' server/package.json || fail "live-share production Node sürümü 24.x ile sabitlenmemiş"
grep -q "node-version: '24'" .github/workflows/live-share-server-test.yml || fail "server CI production Node 24 ile hizalı değil"
test -s .github/workflows/production-live-share-smoke.yml || fail "production live-share smoke workflow eksik"

if grep -R --line-number --include='*.kt' ' km/s' app/src/main; then
  fail "hız biriminde km/s kullanımı bulundu; UI km/h olmalı"
fi

if grep -R --line-number -E 'PASTE_YOUR_|1\.2\.3\.4' app/src/main server/src; then
  fail "üretim kaynaklarında placeholder servis/anahtar bulundu"
fi

if grep -R --line-number -E 'YAPAY ZEKA TAHMİNİ|TrafficTrendPredictor' app/src/main; then
  fail "kaynağı olmayan sentetik trafik tahmini üretim kodunda bulundu"
fi

if grep -i -E 'gemini|firebase[._-]?ai' metadata.json .env.example app/build.gradle.kts; then
  fail "kullanılmayan AI yeteneği ürün/build metadata'sında tekrar göründü"
fi

test -s app/src/main/java/com/example/haritalar/navigation/PoiViewportPolicy.kt || fail "premium POI viewport/density policy eksik"
test -s app/src/test/java/com/example/haritalar/navigation/PoiViewportPolicyTest.kt || fail "premium POI viewport regresyon testi eksik"

grep -q 'Warm Android emulator SDK with retry' .github/workflows/android-apk.yml || fail "APK emulator SDK retry koruması eksik"
grep -q 'Warm Android emulator SDK with retry' .github/workflows/pr-android-test.yml || fail "PR emulator SDK retry koruması eksik"

test -s app/src/main/java/com/example/haritalar/navigation/LanuBriefPolicy.kt || fail "LANU Brief policy eksik"
test -s app/src/main/java/com/example/haritalar/ui/LanuBriefCard.kt || fail "LANU Brief UI eksik"
test -s app/src/test/java/com/example/haritalar/navigation/LanuBriefPolicyTest.kt || fail "LANU Brief veri dürüstlüğü testi eksik"
test -s app/src/main/java/com/example/haritalar/navigation/RoadIntelligencePolicy.kt || fail "Road Intelligence öncelik policy eksik"
test -s app/src/main/java/com/example/haritalar/ui/RoadIntelligenceStrip.kt || fail "Road Intelligence sürüş şeridi eksik"
test -s app/src/test/java/com/example/haritalar/navigation/RoadIntelligencePolicyTest.kt || fail "Road Intelligence regresyon testi eksik"
grep -q "ROAD_CLOSURE" app/src/main/java/com/example/haritalar/navigation/RoadIntelligencePolicy.kt || fail "doğrulanmış yol kapanışı Road Intelligence modelinde eksik"
grep -q "roadClosure" app/src/test/java/com/example/haritalar/navigation/RoadIntelligencePolicyTest.kt || fail "yol kapanışı doğruluk testi eksik"
grep -q "fromCache" app/src/main/java/com/example/haritalar/model/NavigationModels.kt || fail "trafik cache provenance modeli eksik"
grep -q "cachedSegments_neverClaimLiveTraffic" app/src/test/java/com/example/haritalar/data/traffic/TrafficRouteCostModelTest.kt || fail "cached trafik canlı doğrulama regresyon testi eksik"
grep -q "cachedClosureNeverBecomesP0" app/src/test/java/com/example/haritalar/navigation/RoadIntelligencePolicyTest.kt || fail "cached kapanış P0 regresyon testi eksik"
grep -q "ROAD_CLOSURE" app/src/main/java/com/example/haritalar/navigation/LanuBriefPolicy.kt || fail "LANU Brief yol kapanışı özeti eksik"
grep -q "verifiedRoadClosureAppearsAsCriticalPreDriveWarning" app/src/test/java/com/example/haritalar/navigation/LanuBriefPolicyTest.kt || fail "LANU Brief yol kapanışı regresyon testi eksik"
test -s app/src/main/java/com/example/haritalar/navigation/RouteWeatherForecastPolicy.kt || fail "ETA hava rota örnekleme policy eksik"
test -s app/src/test/java/com/example/haritalar/navigation/RouteWeatherForecastPolicyTest.kt || fail "ETA hava rota örnekleme testi eksik"
test -s app/src/main/java/com/example/haritalar/navigation/WeatherRequestGuard.kt || fail "stale hava istek guard eksik"
test -s app/src/test/java/com/example/haritalar/navigation/WeatherRequestGuardTest.kt || fail "stale hava istek guard testi eksik"
grep -q "hourly=weather_code,precipitation,rain,showers,snowfall" app/src/main/java/com/example/haritalar/data/weather/WeatherRepository.kt || fail "Open-Meteo saatlik tahmin isteği eksik"
grep -q "timeformat=unixtime" app/src/main/java/com/example/haritalar/data/weather/WeatherRepository.kt || fail "hava tahmin zaman formatı sabit değil"
grep -q "forecast_hours=" app/src/main/java/com/example/haritalar/data/weather/WeatherRepository.kt || fail "rota süresine göre forecast horizon eksik"
grep -q "WeatherRequestGuard.shouldApply" app/src/main/java/com/example/haritalar/ui/MainViewModel.kt || fail "geç hava cevabı yeni rotayı ezebilir"
grep -q "fetchWeatherForRoute(newRoute)" app/src/main/java/com/example/haritalar/ui/MainViewModel.kt || fail "reroute sonrası hava yenileme eksik"
grep -q "arrivalForecastShowsRoutePositionEtaAndSource" app/src/test/java/com/example/haritalar/navigation/LanuBriefPolicyTest.kt || fail "LANU Brief ETA hava testi eksik"
grep -q "currentWeatherFallbackIsExplicitlyPartial" app/src/test/java/com/example/haritalar/navigation/LanuBriefPolicyTest.kt || fail "hava fallback provenance testi eksik"
test -s app/src/main/java/com/example/haritalar/navigation/SafetyCameraVoicePolicy.kt || fail "radar ses policy eksik"
test -s app/src/test/java/com/example/haritalar/navigation/SafetyCameraVoicePolicyTest.kt || fail "radar ses regresyon testi eksik"
grep -q 'MAX_WARNING_DISTANCE_METERS = 5_000.0' app/src/main/java/com/example/haritalar/navigation/SafetyCameraWarningPolicy.kt || fail "radar sürüş uyarısı 5 km ile sınırlı değil"
grep -q '(500..5_000 step 500)' app/src/main/java/com/example/haritalar/navigation/SafetyCameraWarningPolicy.kt || fail "radar 500 metre ses kademesi eksik"
grep -q 'ROUTE_PREFETCH_SPACING_METERS = 18_000.0' app/src/main/java/com/example/haritalar/data/network/SafetyCameraAreaPolicy.kt || fail "rota öncesi tam güzergâh radar taraması eksik"
grep -q 'camerasAlongRoute' app/src/main/java/com/example/haritalar/navigation/SafetyCameraRouteFilterPolicy.kt || fail "radar rota kilometresi hesabı eksik"
grep -q 'prefetchForRoute' app/src/main/java/com/example/haritalar/ui/SafetyCameraLayerViewModel.kt || fail "rota öncesi radar prefetch eksik"
grep -q 'updatePreDriveSafetyCameraData' app/src/main/java/com/example/haritalar/ui/MainViewModel.kt || fail "rota öncesi radar ses özeti akışı eksik"
grep -q 'lanu:nearby_road' app/src/main/java/com/example/haritalar/data/network/SafetyCameraService.kt || fail "radar yol bağlamı zenginleştirmesi eksik"
grep -q 'lanu:nearby_place' app/src/main/java/com/example/haritalar/data/network/SafetyCameraService.kt || fail "radar yer bağlamı zenginleştirmesi eksik"

echo "LANU constitution quality gate: PASS"


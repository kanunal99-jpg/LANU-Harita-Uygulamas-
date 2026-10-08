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
grep -q 'versionName = "1.1.22"' app/build.gradle.kts || fail "beklenen Android sürümü 1.1.22 değil"
grep -q 'versionCode = 24' app/build.gradle.kts || fail "beklenen Android versionCode 24 değil"
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

test -s app/src/main/java/com/example/haritalar/model/RoadFeatureModels.kt || fail "road feature veri modeli eksik"
test -s app/src/main/java/com/example/haritalar/navigation/RoadFeatureRoutePolicy.kt || fail "road feature rota koridor policy eksik"
test -s app/src/main/java/com/example/haritalar/data/network/RoadFeatureService.kt || fail "road feature Overpass servisi eksik"
test -s app/src/main/java/com/example/haritalar/data/cache/RoadFeatureCache.kt || fail "road feature last-known-good cache eksik"
test -s app/src/main/java/com/example/haritalar/data/repository/RoadFeatureRepository.kt || fail "road feature dayanıklı repository zinciri eksik"
test -s app/src/test/java/com/example/haritalar/data/network/RoadFeatureServiceTest.kt || fail "road feature parser/query testi eksik"
test -s app/src/test/java/com/example/haritalar/navigation/RoadFeatureRoutePolicyTest.kt || fail "road feature rota filtresi testi eksik"
grep -q 'ROAD_FEATURE' app/src/main/java/com/example/haritalar/navigation/RoadIntelligencePolicy.kt || fail "road feature Road Intelligence entegrasyonu eksik"
grep -q 'ROAD_FEATURE' app/src/main/java/com/example/haritalar/navigation/LanuBriefPolicy.kt || fail "road feature LANU Brief entegrasyonu eksik"
grep -q 'fetchRoadFeaturesForRoute(newRoute)' app/src/main/java/com/example/haritalar/ui/MainViewModel.kt || fail "reroute sonrası road feature yenileme eksik"
grep -q 'RoadFeatureDataState.CACHED' app/src/main/java/com/example/haritalar/navigation/LanuBriefPolicy.kt || fail "road feature cache provenance LANU Brief'te eksik"
grep -q 'MAX_WARNING_DISTANCE_METERS = 5_000.0' app/src/main/java/com/example/haritalar/navigation/SafetyCameraWarningPolicy.kt || fail "1.1.12 radar son-5-km invariantı geriledi"
test -s app/src/main/java/com/example/haritalar/navigation/RadarBriefReadinessPolicy.kt || fail "radar rota readiness policy eksik"
test -s app/src/test/java/com/example/haritalar/navigation/RadarBriefReadinessPolicyTest.kt || fail "radar rota readiness regresyon testi eksik"
grep -q 'completedRoutePrefetchRouteId' app/src/main/java/com/example/haritalar/ui/SafetyCameraLayerViewModel.kt || fail "radar prefetch sonucu seçili routeId ile bağlı değil"
grep -q 'cameraRouteScanComplete' app/src/main/java/com/example/haritalar/navigation/LanuBriefPolicy.kt || fail "LANU Brief radar tarama durumu eksik"
grep -q 'cameraItemShowsScanningUntilSelectedRouteScanCompletes' app/src/test/java/com/example/haritalar/navigation/LanuBriefPolicyTest.kt || fail "LANU Brief radar tarama durum testi eksik"
if grep -q 'Özet tamamlanınca navigasyonu başlatın' app/src/main/java/com/example/haritalar/ui/MainViewModel.kt; then
  fail "radar özeti navigasyonu tekrar bloke ediyor"
fi

test -s app/src/main/java/com/example/haritalar/model/RouteCriticalPoiModels.kt || fail "rota kritik POI modeli eksik"
test -s app/src/main/java/com/example/haritalar/navigation/RouteCriticalPoiPolicy.kt || fail "rota kritik POI koridor policy eksik"
test -s app/src/test/java/com/example/haritalar/navigation/RouteCriticalPoiPolicyTest.kt || fail "rota kritik POI regresyon testi eksik"
grep -q 'fetchPoisAroundResult' app/src/main/java/com/example/haritalar/data/network/PoiNetworkService.kt || fail "POI provider success/error provenance ayrımı eksik"
grep -q 'isValidOverpassPayload' app/src/main/java/com/example/haritalar/data/network/PoiNetworkService.kt || fail "bozuk POI payload güvenli fallback doğrulaması eksik"
grep -q 'fetchCriticalPoisForRoute' app/src/main/java/com/example/haritalar/data/repository/NavigationRepository.kt || fail "tam rota kritik hizmet prefetch eksik"
grep -q 'fetchCriticalPoisForRoute(newRoute)' app/src/main/java/com/example/haritalar/ui/MainViewModel.kt || fail "reroute sonrası kritik hizmet yenileme eksik"
grep -q 'CRITICAL_SERVICES' app/src/main/java/com/example/haritalar/navigation/LanuBriefPolicy.kt || fail "kritik hizmet LANU Brief entegrasyonu eksik"
grep -q 'verifiedCriticalServicesShowCountsRouteKmAndCorridorDistance' app/src/test/java/com/example/haritalar/navigation/LanuBriefPolicyTest.kt || fail "kritik hizmet Brief detay testi eksik"
grep -q 'unavailableCriticalServiceProviderNeverPretendsZeroServicesIsVerified' app/src/test/java/com/example/haritalar/navigation/LanuBriefPolicyTest.kt || fail "kritik hizmet safe-unknown testi eksik"
grep -q 'routeSamplingKeepsStartAndDestinationAcrossLongRoutes' app/src/test/java/com/example/haritalar/navigation/RouteCriticalPoiPolicyTest.kt || fail "kritik hizmet hedef örnekleme testi eksik"

test -s docs/LANU_PREMIUM_MASTER_SPEC_2026_10_08.md || fail "premium master spec eksik"
grep -q 'SafetyCameraType' app/src/main/java/com/example/haritalar/model/SafetyCameraModels.kt || fail "kamera denetim sınıflandırması eksik"
grep -q 'classifyCameraType' app/src/main/java/com/example/haritalar/data/network/SafetyCameraService.kt || fail "OSM kamera sınıflandırma parserı eksik"
grep -q 'RED_LIGHT' app/src/main/java/com/example/haritalar/model/SafetyCameraModels.kt || fail "kırmızı ışık kamera sınıfı eksik"
grep -q 'AVERAGE_SPEED_CONTROL_POINT' app/src/main/java/com/example/haritalar/model/SafetyCameraModels.kt || fail "ortalama hız denetim noktası sınıfı eksik"
grep -q 'startNavigationInternal(route, latestLocation)' app/src/main/java/com/example/haritalar/ui/MainViewModel.kt || fail "hedef kartı navigasyon tek dokunuş akışı eksik"
test -s app/src/main/java/com/example/haritalar/navigation/SafetyCameraRouteProgressPolicy.kt || fail "kademeli radar tarama policy eksik"
test -s app/src/test/java/com/example/haritalar/navigation/SafetyCameraRouteProgressPolicyTest.kt || fail "kademeli radar tarama regresyon testi eksik"
grep -q 'shouldPublishPartial(index, centers.size)' app/src/main/java/com/example/haritalar/ui/SafetyCameraLayerViewModel.kt || fail "rota radar kısmi sonuç yayınlaması eksik"
grep -q 'partialRouteScanShowsFoundCameraWithoutClaimingFullCoverage' app/src/test/java/com/example/haritalar/navigation/LanuBriefPolicyTest.kt || fail "kısmi tarama veri dürüstlüğü regresyon testi eksik"
test -s app/src/main/java/com/example/haritalar/data/search/TurkishDistrictDirectory.kt || fail "Türkiye tam ilçe veri dizini eksik"
test -s app/src/test/java/com/example/haritalar/data/search/TurkishDistrictDirectoryTest.kt || fail "Türkiye ilçe eşleşme regresyon testi eksik"
grep -q 'TurkishDistrictDirectory.findDistrict' app/src/main/java/com/example/haritalar/data/search/TurkishAddressHelper.kt || fail "ebeveyn il-ilçe doğrulaması arama motorunda kullanılmıyor"
test -s app/src/main/java/com/example/haritalar/navigation/RouteCameraCoverage.kt || fail "radar coverage/provenance modeli eksik"
test -s app/src/test/java/com/example/haritalar/navigation/RouteCameraCoverageTest.kt || fail "radar coverage/provenance testleri eksik"
grep -q 'routeCameraCoverage.fullyVerified' app/src/main/java/com/example/MainActivity.kt || fail "LANU Brief kamera tam kapsama kontrolü eksik"
grep -q 'cameraRouteScanDegraded' app/src/main/java/com/example/haritalar/navigation/LanuBriefPolicy.kt || fail "kamera kaynak başarısızlığı durum mesajı eksik"
grep -q 'coverageDegraded = isCoverageDegraded' app/src/main/java/com/example/haritalar/ui/MainViewModel.kt || fail "kamera eksik kapsama sesli uyarı etiketi eksik"
test -s app/src/main/java/com/example/haritalar/data/cache/SafetyCameraCacheIndexPolicy.kt || fail "çok bölgeli radar önbellek indeksi eksik"
test -s app/src/test/java/com/example/haritalar/data/cache/SafetyCameraCacheIndexPolicyTest.kt || fail "çok bölgeli radar önbellek testleri eksik"
grep -q 'withContext(Dispatchers.IO)' app/src/main/java/com/example/haritalar/data/repository/SafetyCameraRepository.kt || fail "radar cache I/O ana iş parçacığından ayrılmamış"
grep -q 'KEY_LEGACY_PAYLOAD' app/src/main/java/com/example/haritalar/data/cache/SafetyCameraCache.kt || fail "eski radar cache migrasyon fallback eksik"
test -s app/src/test/java/com/example/haritalar/data/network/SafetyCameraMirrorFallbackTest.kt || fail "Overpass alternatif mirror entegrasyon testleri eksik"
grep -q 'MAX_CACHE_BYTES = 3_000_000' app/src/main/java/com/example/haritalar/data/cache/SafetyCameraCacheIndexPolicy.kt || fail "radar cache boyut sınırı eksik"
grep -q 'apk-signing-info.txt' .github/workflows/android-apk.yml || fail "APK signing sertifika raporu eksik"
echo "LANU constitution quality gate: PASS"


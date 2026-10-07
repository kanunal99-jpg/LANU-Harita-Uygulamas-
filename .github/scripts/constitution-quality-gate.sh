#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "QUALITY_GATE_ERROR: $1" >&2
  exit 1
}

test -s ANAYASA.md || fail "ANAYASA.md eksik veya boş"
test -s README.md || fail "README.md eksik veya boş"
test -s docs/PRODUCTION_READINESS.md || fail "production readiness belgesi eksik"

grep -q 'ANA SERVİS.*ALTERNATİF.*GERÇEK FALLBACK' ANAYASA.md || fail "kritik zincir standardı Anayasa'da bulunamadı"
grep -q 'versionName = "1.1.6"' app/build.gradle.kts || fail "beklenen Android sürümü 1.1.6 değil"
grep -q 'versionCode = 8' app/build.gradle.kts || fail "beklenen Android versionCode 8 değil"
grep -q 'android:allowBackup="false"' app/src/main/AndroidManifest.xml || fail "uygulama backup güvenlik kuralı kapalı değil"
grep -q 'android:foregroundServiceType="location"' app/src/main/AndroidManifest.xml || fail "aktif navigasyon location foreground service bildirimi eksik"
grep -q 'android.permission.FOREGROUND_SERVICE_LOCATION' app/src/main/AndroidManifest.xml || fail "foreground location permission eksik"
test -s app/src/main/java/com/example/haritalar/navigation/NavigationForegroundService.kt || fail "navigation foreground service sınıfı eksik"
test -s app/src/main/java/com/example/haritalar/navigation/LocationSamplingPolicy.kt || fail "adaptif GPS sampling policy eksik"
test -s app/src/main/java/com/example/haritalar/navigation/DestinationSnapPolicy.kt || fail "geniş alan hedeflerini rota ucuna güvenli snap policy eksik"
test -s app/src/main/java/com/example/haritalar/navigation/PoiSearchCenterPolicy.kt || fail "POI viewport arama merkezi policy eksik"
test -s app/src/test/java/com/example/haritalar/navigation/PoiSearchCenterPolicyTest.kt || fail "POI viewport regresyon testi eksik"
test -s app/src/main/java/com/example/haritalar/data/search/TurkeyAdministrativeDirectory.kt || fail "81 il / 973 ilçe idari dizini eksik"
test -s app/src/test/java/com/example/haritalar/data/search/TurkeyAdministrativeDirectoryTest.kt || fail "Türkiye idari veri regresyon testi eksik"
test -s app/src/main/java/com/example/haritalar/data/repository/RouteTruthPolicy.kt || fail "rota doğruluk policy eksik"
test -s app/src/test/java/com/example/haritalar/data/repository/RouteTruthPolicyTest.kt || fail "rota doğruluk regresyon testi eksik"
test -s app/src/test/java/com/example/haritalar/data/search/SearchResultDeduplicatorTest.kt || fail "arama dedup regresyon testi eksik"
test -s app/src/test/java/com/example/haritalar/navigation/NavigationRefreshPolicyTest.kt || fail "reroute trafik lifecycle testi eksik"
test -s app/src/test/java/com/example/haritalar/navigation/PoiSearchAreaPolicyTest.kt || fail "POI kapsama alanı testi eksik"
grep -q 'DISTRICT_COUNT = 973' app/src/main/java/com/example/haritalar/data/search/TurkeyAdministrativeDirectory.kt || fail "Türkiye ilçe sayısı 973 olarak kilitli değil"
grep -q '1.1.6 (versionCode 8)' README.md || fail "README sürümü build sürümüyle eşleşmiyor"
test -s app/src/test/java/com/example/haritalar/data/search/PostalCodeSearchTest.kt || fail "posta kodu regresyon testi eksik"
test -s app/src/test/java/com/example/haritalar/ui/MainViewModelSearchStateTest.kt || fail "arama/rota state regresyon testi eksik"
grep -q 'ENTER_MOVING_SPEED_KMH = 8.0f' app/src/main/java/com/example/haritalar/navigation/LocationSamplingPolicy.kt || fail "adaptif GPS moving eşiği korunmuyor"
test ! -e app/src/main/java/com/example/DrivingBottomDashboard.kt || fail "duplicate sürüş dashboard'u yeniden eklendi"
grep -q 'NAVIGATION_PREFETCH_RADIUS_METERS = 12_000.0' app/src/main/java/com/example/haritalar/data/network/SafetyCameraAreaPolicy.kt || fail "radar navigasyon prefetch zarfı korunmuyor"
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

echo "LANU constitution quality gate: PASS"

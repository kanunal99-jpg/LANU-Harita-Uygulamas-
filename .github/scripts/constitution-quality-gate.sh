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
grep -q 'versionName = "1.1.0"' app/build.gradle.kts || fail "beklenen Android sürümü 1.1.0 değil"
grep -q 'android:allowBackup="false"' app/src/main/AndroidManifest.xml || fail "uygulama backup güvenlik kuralı kapalı değil"
grep -q 'android:foregroundServiceType="location"' app/src/main/AndroidManifest.xml || fail "aktif navigasyon location foreground service bildirimi eksik"
grep -q 'android.permission.FOREGROUND_SERVICE_LOCATION' app/src/main/AndroidManifest.xml || fail "foreground location permission eksik"
test -s app/src/main/java/com/example/haritalar/navigation/NavigationForegroundService.kt || fail "navigation foreground service sınıfı eksik"
test ! -e app/src/main/java/com/example/DrivingBottomDashboard.kt || fail "duplicate sürüş dashboard'u yeniden eklendi"
grep -q 'NAVIGATION_PREFETCH_RADIUS_METERS = 6_500.0' app/src/main/java/com/example/haritalar/data/network/SafetyCameraAreaPolicy.kt || fail "radar navigasyon prefetch zarfı korunmuyor"

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

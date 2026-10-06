# LANU Harita

LANU Harita, Android için geliştirilen Türkçe navigasyon uygulamasıdır. Proje gerçek GPS, alternatif rota, MapLibre harita, trafik doğrulama, POI/adres arama, trafik ışıkları, sabit hız kamerası uyarıları, hava durumu, çevrimdışı harita/cache ve canlı konum paylaşımı bileşenlerini içerir.

## Ürün doğruluğu

Bu repoda ürün gerçeği **ANAYASA.md** ile yönetilir. Temel kural: kaynakta doğrulanmayan bilgi gerçekmiş gibi gösterilmez.

### Sabit hız kamerası zinciri

- OpenStreetMap `highway=speed_camera` verisi
- Overpass ana endpoint + mirror endpoint’ler
- navigasyonda görünür viewport’tan bağımsız 6.5 km ön-yükleme
- aktif rota ve ileri yön koridor filtresi
- 24 saatlik, kapsama alanı denetlenen persistent last-known-good cache
- 5 km uyarı zarfı / 500 m TTS kademeleri
- kaynakta hız limiti yoksa hız limiti uydurulmaz

### Rota zinciri

Valhalla → OSRM → doğrulanmış offline route cache.

> Tamamen yeni bir rotayı internet olmadan hesaplayan bağımsız offline routing motoru henüz ürün iddiası değildir.

## Build

Gerekenler:

- JDK 17
- Android SDK / API 36
- Gradle 9.3.1 (CI tarafından kurulur)

Yerel debug build:

```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Opsiyonel canlı trafik için:

```properties
TOMTOM_API_KEY=...
```

Gerçek anahtarı repoya commit etmeyin.

## CI / Release

- PR: lint + unit test + emulator instrumentation + APK build
- main: aynı kalite kapıları + APK SHA-256 + `latest` GitHub Release güncellemesi
- indirilebilir dosya adı: `app-debug.apk`

Yayınlar:
https://github.com/kanunal99-jpg/LANU-Harita-Uygulamas-/releases

## Dokümantasyon

- `ANAYASA.md` — bağlayıcı proje kuralları
- `docs/PRODUCTION_READINESS.md` — üretim sınırları
- `docs/END_TO_END_AUDIT_2026-10-06.md` — son uçtan uca denetim ve yapılan düzeltmeler

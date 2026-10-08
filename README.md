# LANU Harita

LANU Harita, Android için geliştirilen Türkçe navigasyon uygulamasıdır. Proje gerçek GPS, alternatif rota, MapLibre harita, trafik doğrulama, POI/adres arama, trafik ışıkları, sabit hız kamerası uyarıları, hava durumu, çevrimdışı harita/cache, Android foreground navigasyon ve canlı konum paylaşımı bileşenlerini içerir.

**Güncel uygulama sürümü:** 1.1.21 (versionCode 23)

## Ürün doğruluğu

Bu repoda ürün gerçeği **ANAYASA.md** ile yönetilir. Temel kural: kaynakta doğrulanmayan bilgi gerçekmiş gibi gösterilmez.

### Konum / navigasyon

- Gerçek GPS zorunlu; sahte production konum fallback’i yok.
- Konum kalitesi freshness/accuracy/fiziksel sıçrama filtresinden geçer.
- Adaptif GPS sampling: sürüşte daha sık, idle durumda daha seyrek örnekleme; hysteresis ile gereksiz provider yeniden başlatması azaltılır.
- Aktif navigasyonda Android location foreground service kullanılır.

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

### Live Share

- Süreli ve iptal edilebilir token
- bounded retry
- payload boyut/doğrulama kontrolleri
- local server end-to-end smoke
- main sonrası gerçek Render endpoint’inde exact-commit production smoke

> Session store halen process memory’dir; restart/deploy sonrası kalıcılık henüz ürün iddiası değildir.

## Build

Gerekenler:

- JDK 17
- Android SDK / API 36
- Gradle 9.3.1 (CI tarafından kurulur)
- Live-share server: Node.js 24.x

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

- PR: Anayasa gate + lint + unit test + emulator instrumentation + APK build
- Server değişiklikleri: syntax + invalid payload + create/update/read/revoke local smoke
- main: aynı Android kalite kapıları + APK SHA-256 + `latest` GitHub Release güncellemesi
- Android APK tamamlandıktan sonra: doğru Render commit’ini bekleyen gerçek production live-share smoke
- CI job’ları bounded timeout kullanır.
- indirilebilir dosya adı: `app-debug.apk`

Yayınlar:
https://github.com/kanunal99-jpg/LANU-Harita-Uygulamas-/releases

## Dokümantasyon

- `ANAYASA.md` — bağlayıcı proje kuralları
- `docs/PRODUCTION_READINESS.md` — üretim yetenekleri ve dürüst sınırlar
- `docs/P2_PROFESSIONAL_READINESS.md` — adaptif GPS / P2 kabul kriterleri
- `docs/END_TO_END_AUDIT_2026-10-06.md` — uçtan uca denetim ve düzeltmeler

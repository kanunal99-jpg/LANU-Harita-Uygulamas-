# LANU Harita — Production Readiness

Bu belge üretimde gerçekten desteklenen yetenekleri ve bilinçli ürün sınırlarını kaydeder. Kaynakta doğrulanmayan bir özellik “var” sayılmaz.

## Uygulanmış / CI ile doğrulanması gereken yetenekler

- Gerçek GPS zorunluluğu; uydurma konum fallback’i yok.
- GPS freshness/accuracy ve fiziksel sıçrama filtresi.
- Valhalla → OSRM → doğrulanmış offline route-cache rota zinciri.
- Provider geometri/mesafe/ETA sanity kontrolü.
- Geometry tabanlı navigasyon ilerlemesi, manevra ve ETA.
- Off-route tespiti ve kontrollü reroute.
- MapLibre harita ve offline map/tile cache.
- Arama provider fallback zinciri.
- Canlı trafik yoksa sentetik trafik üretmeden temel ETA’yı koruyan güvenli durum.
- Gerçek hava durumu provider’ı; hata halinde uydurma değer yok.
- Sabit hız kamerası: Overpass primary/mirrors → 24 saatlik persistent last-known-good cache.
- Navigasyonda viewport’tan bağımsız 6.5 km radar prefetch; 5 km uyarı zarfı.
- Aktif rota/ileri yön filtresi, 500 m TTS kademeleri ve session reset.
- Radar UI: mesafe + kaynak + yalnızca doğrulanmışsa hız limiti.
- Sürüş hızı ve yolculuk ortalama hızı km/h.
- Gerçek live-share backend; süreli token + revoke; PUT/DELETE için bounded retry.
- Aktif navigasyonda Android location foreground service; rota bittiğinde güvenli stop yaşam döngüsü.
- Android lint, unit test, emulator instrumentation, debug APK ve SHA-256 release kapıları.

## Bilinçli olarak uydurulmayan yetenekler

### Tam bağımsız offline routing

Mevcut sistem offline harita ve doğrulanmış rota cache’i destekler. Cache dışında yeni ve keyfi bir rotayı internet olmadan hesaplayan paketlenmiş routing graph/engine henüz yoktur. Bu yüzden uygulama bunu varmış gibi gösteremez.

### İkinci bağımsız canlı trafik sağlayıcısı

Doğrulanmış canlı trafik provider’ı şu an TomTom’dur. Kısa süreli cache ve güvenli “doğrulanamadı” durumu vardır; bağımsız ikinci canlı provider uydurulmaz.

### Live-share kalıcılığı

Sunucu token/TTL/revoke uygular fakat session store process memory’dedir. Sunucu restart’ı aktif paylaşımı sonlandırabilir. Kalıcı DB için ayrıca altyapı ve maliyet kararı gerekir.

## Güvenlik

- HTTP cleartext kapalı.
- Uygulama backup’ı kapalı; shared preferences/database/file/external data backup ve device transfer dışında.
- Gerçek API key/token repoya commit edilmez.
- Live-share tokenları hash’lenerek saklanır, süreli ve iptal edilebilirdir.

## Kalite kuralı

Bir yetenek ancak code review, Anayasa quality gate, unit test, lint, Android instrumentation, APK build ve ilgili smoke doğrulaması başarılıysa production-ready kabul edilir. Eksik dış yetenekler unavailable/degraded olarak gösterilir; simüle edilmez.

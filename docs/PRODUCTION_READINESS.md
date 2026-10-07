# LANU Harita — Production Readiness

Bu belge üretimde gerçekten desteklenen yetenekleri ve bilinçli ürün sınırlarını kaydeder. Kaynakta doğrulanmayan bir özellik “var” sayılmaz.

## Uygulanmış / CI ile doğrulanan yetenekler

- Türkiye idari arama referansı yerelde 81 il / 973 ilçe içerir; ilçe ayrıştırması sınırlı örnek listesine bağlı değildir.
- İstanbul'un 39 ilçesi Avrupa/Anadolu yakası olarak ayrı doğrulama tablosunda tutulur.
- Arama deduplication aynı isimli uzak işletmeleri korur; yalnızca aynı fiziksel yer olduğuna dair konum+isim kanıtı varsa birleştirir.
- Rota UI'sı ücret/feribot bilgisi bilinmiyorsa bunu "yok" gibi sunmaz; OSRM alternatiflerine cevap sırasından sahte "En Kısa/Ücretsiz" anlamı yüklenmez.
- Valhalla kısıtlı rota isteği ancak provider cevabı kısıtı doğruluyorsa "Ücretsiz/Feribotsuz" etiketi taşır.
- Reroute sonrası hava verisi yeni rota için yeniden alınır; eski async hava cevabı yeni rotayı ezemez.
- Periyodik trafik worker'ı reroute state'inde yaşamaya devam eder ancak eski rota üzerinde refresh yapmaz.
- POI kapsama yarıçapı serbest harita gezintisinde görünür alanla birlikte 4–20 km arasında uyarlanır.
- GitHub Release pipeline'ı güvenli signing secret'ları sağlandığında production-signed release APK üretmeyi destekler; secret yoksa debug artifact açıkça debug olarak kalır.
- Gerçek GPS zorunluluğu; uydurma konum fallback’i yok.
- GPS freshness/accuracy ve fiziksel sıçrama filtresi.
- Adaptif GPS sampling: hareket halinde 1 sn / 2 m, idle durumda 4 sn / 8 m; 8 km/h giriş ve 3 km/h çıkış hysteresis’i.
- Fused location ana yol; Android sistem GPS fallback’i aynı sampling politikasıyla çalışır.
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
- Live-share backend: süreli token + revoke + bounded retry.
- Live-share POST/PUT payload doğrulaması, 32 KiB request body limiti ve hatalı JSON için güvenli 400 yanıtı.
- Render health cevabında çalışan commit revision’ı yayınlanır.
- Main sonrası production smoke, tam olarak beklenen Render commit’i canlı olmadan başarılı sayılmaz; create → update → read → viewer → revoke zincirini gerçek endpoint üzerinde doğrular.
- Aktif navigasyonda Android location foreground service; rota bittiğinde güvenli stop yaşam döngüsü.
- Android lint, unit test, emulator instrumentation, debug APK, SHA-256 ve GitHub Release kapıları.
- CI job’larında bounded timeout ve güncel GitHub Action major sürümleri.
- Live-share runtime ve CI Node.js 24.x üzerinde hizalıdır; Render/CI runtime drift’i engellenir.

## Bilinçli olarak uydurulmayan yetenekler

### Tam bağımsız offline routing

Mevcut sistem offline harita ve doğrulanmış rota cache’i destekler. Cache dışında yeni ve keyfi bir rotayı internet olmadan hesaplayan paketlenmiş routing graph/engine henüz yoktur. Bu yüzden uygulama bunu varmış gibi gösteremez.

### İkinci bağımsız canlı trafik sağlayıcısı

Doğrulanmış canlı trafik provider’ı şu an TomTom’dur. Kısa süreli cache ve güvenli “doğrulanamadı” durumu vardır; bağımsız ikinci canlı provider uydurulmaz.

### Live-share kalıcılığı

Sunucu token/TTL/revoke uygular fakat session store şu an process memory’dedir. Sunucu restart/deploy aktif paylaşımı sonlandırabilir. Ücretsiz Render Key Value disk kalıcılığı sağlamaz; ücretsiz Postgres kaynağı mevcut olsa da bağlantı secret’ı bu otomasyon yüzeyinden güvenli biçimde alınamadığı için kod içinde uydurma bağlantı dizesi kullanılmaz. Kalıcı store ancak gerçek bağlantı secret’ı güvenli şekilde servis environment’ına bağlandığında production-ready sayılır.

### Fiziksel cihaz saha kabulü

Emulator CI; uzun süreli gerçek cihaz batarya, termal davranış, OEM background kısıtları ve gerçek sürüş GPS davranışının yerini tutmaz. Bu maddeler fiziksel cihaz saha kabulüdür.

## Güvenlik

- HTTP cleartext kapalı.
- Uygulama backup’ı kapalı; shared preferences/database/file/external data backup ve device transfer dışında.
- Gerçek API key/token repoya commit edilmez.
- Live-share tokenları hash’lenerek saklanır, süreli ve iptal edilebilirdir.
- Hatalı/çok büyük JSON payload’ları sınırlı ve kontrollü hata yanıtı alır.

## Kalite kuralı

Bir yetenek ancak code review, Anayasa quality gate, unit test, lint, Android instrumentation, APK build ve ilgili smoke doğrulaması başarılıysa production-ready kabul edilir. Production live-share için smoke testi ayrıca doğru Render commit SHA’sını doğrular. Eksik dış yetenekler unavailable/degraded olarak gösterilir; simüle edilmez.

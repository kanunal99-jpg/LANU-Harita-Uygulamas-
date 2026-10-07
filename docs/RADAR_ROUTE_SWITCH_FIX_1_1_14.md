# LANU 1.1.14 — Alternatif Rota Radar Özeti ve Navigasyon Başlatma Düzeltmesi

## Düzeltilen hata

1.1.12'de alternatif rota seçildiğinde eski rotanın radar verisi kısa süreliğine yeni rota için hazır kabul edilebiliyordu.
Bu durumda yeni rota için sesli özet atlanabiliyor ve "Navigasyonu Başlat" radar özeti tamamlanmadığı için kilitlenebiliyordu.

## Yeni davranış

- Her radar taraması seçili `routeId` ile bağlanır.
- Eski rota taraması yeni alternatif için asla hazır kabul edilmez.
- Alternatif rota seçildiğinde önceki sesli özet durdurulur ve yeni rota özeti yeniden hazırlanır.
- Radar özeti navigasyonu bloke etmez.
- Kullanıcı "Navigasyonu Başlat"a bastığında GPS/rota hazırsa navigasyon başlar.
- Tam radar taraması daha sonra biterse özet, aynı seçili rota için yalnız bir kez teslim edilir.

## Performans

- Sesli özet OSM'de zaten bulunan yol/yer bağlamıyla anında kuyruğa alınır.
- Nominatim reverse-geocode eksik yerler için yalnız arka planda ve rate-limit ile çalışır.
- Adres çözümleme artık rota seçimi veya navigasyon başlangıcını bekletmez.

## LANU Brief doğruluğu

Tarama sürerken:
- "Rota radar/kamera taraması sürüyor"

Tarama tamamlandı ve OSM'de kamera bulunamadıysa:
- "OSM tam rota taramasında sabit kamera bulunamadı"
- Ayrıca bunun gerçek dünyada kesinlikle kamera olmadığı anlamına gelmediği açıkça belirtilir.

## Test kapıları

- stale route radar sonucu reddedilir;
- yalnız seçili rota tamamlanmışsa briefing hazır kabul edilir;
- briefing ROUTE_SELECTION veya NAVIGATING durumunda teslim edilebilir;
- radar özeti navigasyonu bloke eden eski metin kalite kapısında yasaktır;
- LANU Brief tarama sürüyor / boş tamamlandı regresyon testleri;
- lint + unit + emulator smoke + APK + SHA-256 + Release.

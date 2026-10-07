# LANU 1.1.11 — ETA-zamanlı rota hava riski

## Amaç

LANU'nun rota havasını yalnız “şu anda o noktada ne oluyor?” mantığıyla değil, sürücünün o rota noktasına yaklaşık varış zamanındaki hava tahminiyle değerlendirmesi.

## Akış

1. Seçili rota gerçek geometrik mesafeye göre en fazla 6 örnek noktaya bölünür.
2. Her örnek için rota başlangıcından mesafe ve tahmini ulaşma süresi hesaplanır.
3. Open-Meteo'dan saatlik weather_code, precipitation, rain, showers ve snowfall alınır.
4. Her rota örneği için ETA saatine en yakın forecast seçilir.
5. LANU Brief ve canlı Road Intelligence:
   - rota kilometresi,
   - yaklaşık kaç dakika sonra,
   - hava riski,
   - forecast / fallback kaynağı
   bilgisini birlikte gösterir.

## Veri dürüstlüğü

- Saatlik forecast alınabiliyorsa veri modu `ARRIVAL_FORECAST` olur.
- Saatlik forecast kullanılamaz fakat provider mevcut hava döndürürse yalnız `CURRENT_FALLBACK` olarak kullanılır.
- Current fallback, LANU Brief'te `PARTIAL` olarak etiketlenir.
- Provider tamamen başarısızsa hava uydurulmaz.
- Forecast saatleri Unix epoch / GMT üzerinden eşleştirilir; yerel saat dilimi dönüşümüyle yanlış indeks seçilmez.

## Async güvenliği

Her hava isteği generation + routeId ile bağlanır.

- Yeni rota seçildiğinde eski hava job'u iptal edilir.
- Geç gelen eski rota cevabı yeni rotanın `routeWeather` state'ini ezemez.
- Rota/arama temizlendiğinde hava generation invalid edilir.
- Navigasyon durdurulduğunda aktif hava isteği iptal edilir.

## UX örneği

“22.4 km • yaklaşık 26 dk sonra • sis”

Kaynak:
“Open-Meteo saatlik tahmin”

Saatlik veri kullanılamadıysa:
“Open-Meteo mevcut hava (fallback)”

## Test kapıları

- fiziksel mesafeye göre rota örnekleme,
- başlangıç/bitiş örneklerinin korunması,
- monoton mesafe ve ETA,
- en yakın forecast epoch seçimi,
- forecast horizon,
- stale/wrong-route response reddi,
- Brief rota km + ETA + source,
- current fallback = PARTIAL,
- Road Intelligence km + ETA + source,
- lint + unit + Android emulator smoke + APK + SHA-256 + Release.

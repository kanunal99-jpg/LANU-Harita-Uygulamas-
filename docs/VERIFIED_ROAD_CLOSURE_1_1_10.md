# LANU 1.1.10 — Doğrulanmış yol kapanışı zekâsı

## Amaç

Canlı trafik sağlayıcısının seçili rota için doğruladığı yol kapanışını sürüş kokpitinde kritik P0 olay olarak göstermek.

## Davranış

- Yalnızca `TrafficStatus.verified == true` olduğunda `roadClosure=true` segmentleri olay üretir.
- Kullanıcı konumu ve segment koordinatı varsa en yakın kapanışın gerçek mesafesi hesaplanır.
- Koordinat yoksa mesafe uydurulmaz; yalnızca “Seçili rota üzerinde doğrulanmış kapanış” gösterilir.
- Birden fazla kapanış varsa toplam kapanış sayısı başlıkta gösterilir.
- Kaynak adı ve varsa son canlı trafik kontrol zamanı korunur.
- Kapanış P0 önceliklidir; kamera/hız P1 ve yoğun trafik P2 kuralları bozulmaz.

## Güvenli fallback

- Doğrulanmamış trafik veya statik/fallback durum kapanış uyarısı üretmez.
- Segmentte `roadClosure=true` görülse bile trafik oturumu doğrulanmamışsa bu bilgi sürücüye kesin kapanış olarak sunulmaz.
- Yeni ücretli veri sağlayıcı eklenmez.

## Test

- doğrulanmış kapanış P0 olay üretir;
- gerçek koordinattan mesafe hesaplanır;
- doğrulanmamış kapanış segmenti uyarı üretmez;
- mevcut kamera, hava ve trafik öncelik regresyonları korunur;
- lint + unit + Android cold-start smoke + APK + SHA-256 + Release zorunludur.


## Cache provenance

- Trafik segmenti artık `fromCache` provenance alanını taşır.
- Cache’den dönen segment canlı trafik olarak doğrulanmaz.
- Cache trafik harita sürekliliği için korunabilir ancak ETA’ya canlı gecikme olarak eklenmez.
- Cache’deki `roadClosure=true` değeri P0 canlı kapanış uyarısı üretmez.

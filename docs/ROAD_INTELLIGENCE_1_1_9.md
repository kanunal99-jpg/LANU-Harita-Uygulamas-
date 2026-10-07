# LANU 1.1.9 — Road Intelligence sürüş şeridi

## Amaç

Canlı sürüş sırasında aynı anda birden fazla kamera, hava ve trafik kartının üst üste binmesini engellemek ve sürücüye en önemli olayı tek bakışta göstermek.

## Yeni davranış

LANU artık canlı sürüşte kaynak-backed olayları ortak bir öncelik policy'sinden geçirir:

- P0: kritik hava olayı (ör. fırtına)
- P1: kamera / hız güvenliği, kar veya sis
- P2: yağmur ve doğrulanmış ağır trafik
- P3: bilgi seviyesi

En yüksek öncelikli olay tek kompakt şeritte gösterilir. Başka aktif olaylar varsa +N sayacı görünür.

## Veri dürüstlüğü

- Doğrulanmamış trafik olay olarak gösterilmez.
- Açık hava gereksiz uyarı üretmez.
- Kamera bilgisi yalnız mevcut OSM-backed kamera uyarısından gelir.
- Trafik kaynağı ve varsa son kontrol zamanı görünür.
- Hava kaynağı Open-Meteo olarak açıkça belirtilir.

## UX

Önceki radar ve hava kartları kod tabanında fallback olarak korunur ancak ana canlı sürüş yüzeyinde merkezi Road Intelligence şeridi kullanılır. Böylece kritik manevra kartı ile yol uyarıları arasındaki görsel kalabalık azalır.

## Test

- kritik fırtına, kamera ve yoğun trafikten önce gelir;
- kamera P1 olarak korunur;
- doğrulanmamış trafik hiç olay üretmez;
- açık hava gereksiz olay üretmez;
- lint + unit + Android smoke + APK + SHA-256 + Release kapıları zorunludur.

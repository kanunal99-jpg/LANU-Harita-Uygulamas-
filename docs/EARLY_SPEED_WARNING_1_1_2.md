# LANU 1.1.2 — Erken Hız Kamerası Uyarısı

## Amaç

Sürücünün doğrulanmış sabit hız kamerası noktalarına son anda değil, sürüş hızına uygun güvenli bir mesafeden haberdar edilmesi.

## Davranış

- < 70 km/h: ilk uyarı zarfı 5 km
- 70–89 km/h: 6,5 km
- 90–109 km/h: 8 km
- 110+ km/h: 10 km
- Veri ön yükleme zarfı: 12 km
- Ses/titreşim tekrarları: 250 m, 500 m, 1 km, 2 km, 3 km, 5 km, 6,5 km, 8 km, 10 km kilometre taşları
- Doğrulanmış hız limiti varsa limit aşımında ayrıca uyarı
- Hız limiti kaynakta yoksa sistem hız limiti üretmez

## Kaynak ve sınırlar

Sistem OpenStreetMap üzerinde `highway=speed_camera` olarak işaretlenmiş sabit kamera noktalarını kullanır. Birincil Overpass servisi başarısız olduğunda tanımlı aynalar, ardından persistent last-known-good cache devreye girer.

Bu özellik RF/radar sinyali algılamaz, mobil polis kontrolü tespit etmez ve doğrulanmamış ihbarı sabit kamera gibi göstermez.

## Test kapısı

- `SafetyCameraWarningPolicyTest`: hız-adaptif 5/6,5/8/10 km eşikleri, kilometre taşları, ETA ve hız limiti doğruluğu
- `SafetyCameraAreaPolicyTest`: 12 km ön yükleme zarfı ve refresh eşiği
- Android CI: lint + JVM unit tests + instrumentation + APK build + SHA-256 + GitHub Release

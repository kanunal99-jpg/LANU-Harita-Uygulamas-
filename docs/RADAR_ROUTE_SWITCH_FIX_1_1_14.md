# LANU 1.1.14 — Alternatif Rota Radar Özeti ve Navigasyon Başlatma Hotfix

## Düzeltilen hatalar

- Alternatif rota seçildiğinde önceki rotanın radar tarama sonucu yeni rota için hazır kabul edilebiliyordu.
- Bu nedenle ikinci/üçüncü rota için sesli radar özeti atlanabiliyordu.
- Radar özeti tamamlanmadan “Navigasyonu Başlat” engellenebiliyordu.
- LANU Brief tam rota radar taraması sürerken “kamera görünmüyor” diyebiliyordu.

## Yeni davranış

- Her tamamlanan radar taraması seçili `routeId` ile bağlanır.
- Eski rota sonucu yeni alternatifte kullanılamaz.
- Rota değişince önceki radar TTS kuyruğu durdurulur.
- Yeni rota tamamlanınca sesli özet yalnız o rota için yeniden üretilir.
- Radar taraması navigasyon başlatmayı asla bloke etmez.
- Kullanıcı erken başlatırsa tarama tamamlandığında aynı seçili rota için özet bir kez teslim edilebilir.
- Nominatim adres çözümleme sesli özetin kritik yolunda değildir; yalnız arka planda rate-limit ile zenginleştirme yapar.

## LANU Brief

Tarama sürerken:
- “Rota radar/kamera taraması sürüyor”

Tam güzergâh taraması tamamlandığında:
- bulunan bilinen OSM sabit kameraları rota üzerinde sayılır;
- hiç bulunamazsa “OSM tam rota taramasında sabit kamera bulunamadı” denir;
- bunun sahada kesinlikle kamera olmadığı anlamına gelmediği açıkça belirtilir.

## Korunan özellikler

1.1.13 Road Feature Intelligence:
- hız tümseği / traffic calming,
- okul alanı,
- hemzemin geçit,
- OSM hazard kayıtları,
- Road Intelligence entegrasyonu,
- cache provenance

değiştirilmeden korunur.

## Test / yayın kapıları

- stale route readiness reddi,
- selected-route scan readiness,
- ROUTE_SELECTION + NAVIGATING teslim durumu,
- LANU Brief scan progress,
- completed-empty truthfulness,
- navigasyon blok metni kalite kapısında yasak,
- lint,
- unit,
- Android emulator smoke,
- APK,
- SHA-256,
- latest GitHub Release.

# LANU Harita — P1 Professional Readiness

## P1 kapsamında tamamlanan omurga

- Gerçek canlı paylaşım istemcisi ve backend protokolü: bearer-token oturumu, TTL, konum güncelleme, görüntüleme ve revoke akışı.
- Canlı paylaşım servisi için gerçek HTTP smoke testi: health, session oluşturma, update, read ve revoke sonrası 401 doğrulaması.
- Türkçe navigasyon ses politikası tek bir deterministik katmanda toplandı.
- Başlangıç, emniyet kemeri, radar, overspeed ve varış anonsları tek sözcük olarak `LANU` telaffuzuna göre normalize edilir.
- Navigasyon performans bütçeleri açık sabitler ve unit testlerle korunur.
- Radar uyarıları oturum bazlı bucket deduplikasyonu ve reset davranışı ile yönetilir.
- Gerçek hız limiti yoksa overspeed değerlendirmesi yapılmaz.

## Canlı paylaşım üretim sınırı

Backend şu aşamada dependency-free ephemeral session store kullanır. Tokenlar hashlenir, TTL uygulanır ve revoke edilir; ancak servis yeniden başlatılırsa aktif oturumlar kaybolur. Bu nedenle kalıcı production-grade session persistence henüz tamamlanmış kabul edilmez.

## Offline routing üretim sınırı

Uygulama hâlihazırda doğrulanmış rota önbelleğini offline fallback olarak kullanır ve gerçek on-device routing için `OfflineRoutingEngine` boundary'sine sahiptir.

Tamamen cihaz üstünde yeni rota üretimi için Valhalla Mobile gibi gerçek bir native engine ve önceden parse edilmiş bölgesel Valhalla tileset gerekir. Bu veri paketlenmeden sentetik veya düz çizgi rota üretmek yasaktır.

## Trafik

TomTom mevcut doğrulanmış canlı sağlayıcıdır. Sağlayıcı başarısızlığında temel ETA korunur ve canlı veri doğrulanmadı olarak gösterilir. İkinci bağımsız canlı sağlayıcı henüz doğrulanmış değildir.

## Saha doğrulaması

CI; lint, unit test, emulator smoke ve APK üretimini doğrular. Uzun süreli gerçek cihaz/batarya/GPS saha testi ayrı bir kabul kriteridir.

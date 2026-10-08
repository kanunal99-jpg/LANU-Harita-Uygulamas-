# LANU 1.1.19 — Radar kapsama doğruluğu ve güvenli TTS

## Kök neden

`SafetyCameraLayerViewModel.prefetchForRoute` ağ sağlayıcısı/aynalar başarısız olduğunda `Error` sonucunu boş fallback olarak birleştirip yine `completedRoutePrefetchRouteId` atıyordu. UI, bu sonucu başarılı **tam rota taraması** kabul edebiliyor, kullanıcıya taran(a)mayan kesimler için yanıltıcı sıfır kamera özeti gösterebiliyordu.

## Uygulama

- `RouteCameraCoverage` her merkez için doğrulanmış canlı, son bilinen önbellek ve başarısız istek sayılarını tutar.
- Döngünün sona ermesi artık tüm yolun **canlı olarak doğrulandığı** anlamına gelmez. `fullyVerified` ancak tüm merkezler güncel başarılı provider cevabına sahipse doğrudur.
- `LanuBriefPolicy` tarama sürerken **kısmi**, başarısız veya önbelleğe düşen merkezlerle bittiğinde **doğrulanamadı/kısmi**, tam canlı başarıda ise kaynak sınırlamasını belirten mesaj verir.
- Sesli rota özeti tarama kapsaması kısmi ise bu belirsizliği yüksek sesle söyler ve bilinen kameraları **tam sayı** gibi duyurmaz.
- Eski rota/generation sonucu yeni rotaya aktarılmaz. Yardımcı radar verisinin eksikliği navigasyonu bloke etmez.
- Provider istisnası tüm kalan merkezleri `failed` sayar; UI sonsuza kadar 'tarama sürüyor' durumunda kalmaz.

## Regresyon testleri

- Her segment canlı doğrulanmışken `fullyVerified` olumlu.
- Bir servis hatasında / cache-only merkezde `fullyVerified` olumsuz.
- Kısmi kamera brifingi boş ise “hiç kamera yok” iddiası yapılamaz.
- Kısmi dolu brifingde toplam sayının bilinmediği belirtilir.
- TTS boş/dolu kısmi tarama için kaynak belirsizliğini söyler.

## Süreç

Lint → unit → Android emulator instrumentation → APK build → SHA-256 → PR merge → Release asset → production smoke. Son kontroller geçmeden ürün tamamlandı/yayınlandı denmez.

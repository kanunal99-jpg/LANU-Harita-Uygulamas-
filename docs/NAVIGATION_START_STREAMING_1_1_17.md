# LANU 1.1.17 — Tek dokunuşla navigasyon ve kademeli kamera taraması

## Hata ve düzeltme

Hedef önizleme kartındaki **Navigasyonu Başlat** düğmesi, rotayı hesaplayıp **ROUTE_SELECTION** durumunda bırakıyordu. Kullanıcının aynı niyeti ikinci kez onaylaması gerekiyordu.

1.1.17'de hedef kartı için rota hesaplandığında geçerli canlı GPS mevcutsa doğrudan `startNavigationInternal` çağrılır. Rota bulunmazsa veya GPS kalitesi yetersizse güvenli durum ve açıklayıcı mesaj korunur. **Rotaları Gör** işlemi, alternatif rota önizlemesini eskisi gibi açar. Radar, hava, POI ve diğer yardımcı veri istekleri navigasyon başlangıcını bekletmez.

`stopNavigation` artık rota generation kimliğini geçersiz kılar; geç dönen rota sonuçları kapanmış oturumu geri açamaz.

## Uzun güzergâh kamerası

Eskiden tam rota taraması bitmeden hiçbir kamera sonucu görünmüyordu. Artık ilk başarılı tarama merkezi işlendiğinde ve her altı merkezde bir kısmi kamera verisi ekrana aktarılır. Tam rota taramasının `completedRoutePrefetchRouteId` kimliği hâlâ yalnız tüm merkezler bittikten sonra atanır.

LANU Brief kısmi sonuçta bulunan kaynak destekli noktaları **şu ana kadar** ifadesiyle gösterir. Tarama devam ederken boş liste, sahada veya tüm rotada kamera olmadığı şeklinde yorumlanmaz.

## Güvenlik ve sınırlar

- Tek dokunuş yalnızca gerçek rota ve kullanılabilir GPS olduğunda navigasyonu başlatır.
- Alternatif rota seçimi ve radar brifingleri routeId/generation guard kullanmaya devam eder.
- Harici API yanıtı kesin değilse “doğrulandı” denmez.
- Canlı polis/jandarma/seyyar radar paylaşımı eklenmedi.

## Test ve yayın kapıları

- `LanuBriefPolicyTest.partialRouteScanShowsFoundCameraWithoutClaimingFullCoverage`
- `SafetyCameraRouteProgressPolicyTest` (erken, periyodik ve son merkez)
- Android lint + unit + emulator instrumentation + APK + SHA-256 + Release, CI başarılıysa.

Bu belge Android emülatör/donanım doğrulamasının yerine geçmez.

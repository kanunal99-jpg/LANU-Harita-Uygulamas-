# LANU 1.1.5 — POI, Trafik Işıkları ve GPS Kurtarma Düzeltmeleri

## Fotoğrafta doğrulanan problemler

- Benzinlik seçili olmasına rağmen eski/alakasız `POI` işareti haritada kalıyordu.
- POI araması taze GPS'e bağımlıydı; GPS eskiyse seçili kategori mavi görünmesine rağmen yeni sonuç yüklenmiyordu.
- Trafik ışıkları yüksek zoomda yüklenip uzaklaştırma sonrası state'te kaldığı için şehir ölçeğinde üst üste yığılıyordu.
- Trafik ışıkları farklı viewportlardan birleştirilerek zamanla birikiyordu.
- Gri konum işareti görüldüğünde uygulama aktif olarak yeni GPS fix'i istemiyordu.
- Haritadaki POI işaretine dokunmak gerçek POI hedef kartını açmıyordu.
- Tüm POI tipleri aynı genel `POI` balonuyla gösteriliyordu.

## Düzeltmeler

1. **POI arama merkezi**
   - Serbest harita gezintisinde görünür viewport merkezi kullanılır.
   - Takip modunda taze GPS tercih edilir; GPS yoksa viewport merkezine fallback yapılır.
   - POI araması artık taze GPS olmadan da çalışabilir.

2. **POI state güvenliği**
   - Kategori değişince eski POI listesi anında temizlenir.
   - Eşzamanlı eski POI istekleri generation kontrolüyle yeni sonucu ezemez.
   - Harita merkezi yaklaşık 3,5 km değişince seçili kategori kontrollü biçimde yenilenir.
   - POI katmanı kapatılınca eski sonuçlar temizlenir.

3. **POI görselleri ve etkileşim**
   - Genel mor `POI` balonu yerine kategoriye özel marker kullanılır.
   - Benzinlik, eczane, hastane, market, otopark, ATM, kafe, restoran ve şarj noktaları farklı markerlarla gösterilir.
   - POI markerına dokunulduğunda hedef önizleme kartı açılır.
   - Marker çakışmalarında MapLibre collision handling kullanılır.

4. **Trafik ışığı kalabalığı**
   - Zoom 14 altına inildiğinde mevcut trafik ışıkları state'ten temizlenir.
   - Geç kalan eski ağ cevabı generation kontrolü nedeniyle tekrar haritaya yazılamaz.
   - Her sorgu yalnızca güncel viewport içindeki sinyallerle state'i değiştirir; eski viewport sonuçları artık birleştirilmez.
   - UI katmanı ayrıca zoom 14 altında trafik ışığı layer'ını kapatır.
   - Marker overlap kapatılarak aynı bölgede ikon yığılması azaltılır.

5. **GPS recovery**
   - Fused Location sürekli akışına ek olarak aktif `getCurrentLocation` isteği yapılır.
   - 8 saniye içinde kullanılabilir güncel fix gelmezse sistem NETWORK/GPS provider fallback'i devreye girer.
   - Haritadaki yeniden merkezleme düğmesi gri/eski konumda aktif olarak taze GPS fix'i ister.

## Regresyon testleri

- `PoiSearchCenterPolicyTest`
- `TrafficSignalIntegrationTest.testVisibleTrafficSignals_areClearedWhenZoomedOutAndClippedToViewport`
- Mevcut GPS, navigation, search, lint, Android instrumentation ve APK build kapıları.

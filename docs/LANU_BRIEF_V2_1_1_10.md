# LANU 1.1.10 — Brief v2 rota veri kapsamı

## Amaç

LANU Brief'in rota öncesi güvenlik ve ihtiyaç verilerini yalnızca görünür harita alanından değil, seçili rota koridorundan doğrulaması.

## Kritik POI rota kapsamı

LANU seçili rota boyunca mesafe tabanlı örnek noktaları oluşturur ve tek bir sınırlı Overpass isteği ile akaryakıt, hastane, eczane ve elektrikli araç şarj noktalarını tarar.

Dönen noktalar 1,5 km rota koridoru filtresinden geçirilir. Rota örnek aralığı 9 km sınırını aşmıyorsa kapsama VERIFIED, uzun rota örnek üst sınırına takılıyorsa PARTIAL olur.

Veri zinciri: Overpass ana endpoint → Overpass aynaları → rota merkezi çevresinde gerçek arama sağlayıcısı fallback → güvenli UNAVAILABLE.

Kaynak zinciri başarısız olduğunda sıfır POI sonucu gerçekmiş gibi gösterilmez.

## Rota kamera kapsamı

Seçili rota için görünür viewport'tan bağımsız kamera örneklemesi yapılır. Örnek merkezlerinin etrafında mevcut 12 km safety-camera alan politikası kullanılır.

- örneklerin tümü doğrulanır ve rota örnek aralığı sınır içindeyse VERIFIED,
- eksik örnek veya çok uzun rota varsa PARTIAL,
- hiçbir örnek doğrulanamazsa UNAVAILABLE.

Kalıcı last-known-good kamera cache kullanılmışsa kaynak bilgisi bunu açıkça belirtir.

LANU Brief kamera satırı rota koridorundaki doğrulanmış sabit kamera sayısını, rota boyunca en yakın kameranın yaklaşık ilerleme mesafesini, başarılı/toplam örnek sayısını, maksimum örnek aralığını ve kaynak/kapsama durumunu gösterir.

## Yarış durumu koruması

Alternatif rota hızla değiştirildiğinde eski rotanın hava veya kritik POI sonucu yeni rotaya yazılmaz. Reroute sonrası yeni rota için hava ve POI verileri tekrar yüklenir.

## Test ve yayın

1.1.10 için zorunlu kapılar: constitution gate, route sampling unit testleri, route POI query/parser testleri, route corridor filtre testleri, route camera coverage testleri, LANU Brief veri dürüstlüğü testleri, Android lint, unit tests, emulator smoke, APK build, SHA-256 ve GitHub Release asset doğrulaması.
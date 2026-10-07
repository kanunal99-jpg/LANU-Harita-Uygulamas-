# LANU 1.1.14 — Rota Kritik Hizmet Zekâsı

## Amaç

Sürücü navigasyona başlamadan önce seçili rota çevresinde gerçekten erişilebilir kritik hizmetleri görsün:

- benzinlik,
- hastane,
- eczane,
- elektrikli araç şarj istasyonu.

LANU yalnızca toplam sayı göstermez; ilk bulunan hizmetin rota üzerinde yaklaşık kaçıncı kilometrede olduğunu ve POI'nin rota çizgisine kuş uçuşu uzaklığını da gösterir.

## Veri akışı

Rota 16 km aralıklarla örneklenir. **Başlangıç ve hedef her zaman ayrı sorgu merkezi olarak korunur.** Her örnek çevresinde yaklaşık 9 km'lik alan sorgulanır.

**OSM Overpass primary → Overpass mirrors → Nominatim/Photon arama fallback → safe unknown**

Provider cevabının boş olması ile provider'ın hata vermesi artık birbirinden ayrılır.

- Başarılı sorgu + 0 sonuç = gerçekten “yüklü veride yok”.
- Tüm kaynaklar başarısız = “doğrulanamadı”.
- Bazı örnekler fallback kullanıyorsa = kısmi kapsam.

## Rota eşleme

Yalnız şu kategoriler kritik hizmet kapsamındadır:

- FUEL
- HOSPITAL
- PHARMACY
- CHARGING_STATION

POI, rota koridoruna en fazla 2.5 km uzaklıktaysa eşlenir.

Her eşleşmede:

- rota başlangıcından ilerleme mesafesi,
- en yakın rota segmentine kuş uçuşu uzaklık,
- kategori,
- isim/adres

korunur.

**Not:** rota koridoruna uzaklık gerçek araç sapma/yol mesafesi değildir. UI bu değeri “rotadan ~X m” olarak gösterir; sürüş mesafesi gibi sunmaz.

## LANU Brief

Yeni “Kritik Hizmetler” satırı örneği:

> 12 kritik hizmet noktası rota çevresinde  
> Benzinlik 4 • ilk 7.2 km • rotadan ~180 m | Hastane 2 • ilk 18.4 km • rotadan ~620 m | Eczane 5 ... | Şarj 1 ...

Kaynak ve veri kapsam durumu satırda görünür.

## Async güvenliği

Kritik hizmet isteği routeId + generation mantığıyla korunur.

- rota değişince eski istek iptal edilir,
- geç gelen eski rota cevabı yeni rotayı ezemez,
- reroute sonrası yeni rota için tekrar sorgulanır,
- navigasyon/arama temizlenince state temizlenir.

## Test

- rota örnekleme başlangıç ve hedefi korur,
- yalnız kritik kategoriler kabul edilir,
- rota dışı POI elenir,
- duplicate POI iki kez sayılmaz,
- LANU Brief rota km + koridor uzaklığı gösterir,
- provider başarısızsa “0 hizmet” diye doğrulanmış bilgi üretilmez,
- lint + unit + Android emulator smoke + APK build + SHA-256 + Release zorunludur.
- Release içindeki APK ve checksum asset'lerinin 1.1.14 main commit'inden üretildiği ve doğrudan indirilebilir olduğu ayrıca doğrulanır.

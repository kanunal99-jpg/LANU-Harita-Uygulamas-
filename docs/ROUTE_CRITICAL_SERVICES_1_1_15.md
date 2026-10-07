# LANU 1.1.15 — Rota Kritik Hizmet Zekâsı

## Amaç

Seçili rota boyunca gerçek kaynaklardan benzinlik, hastane, eczane ve EV şarj istasyonlarını sürüş öncesi LANU Brief'e eklemek.

## Veri akışı

- Rota yaklaşık 16 km aralıklarla örneklenir.
- Başlangıç ve hedef **her zaman** sorgu merkezi olarak korunur.
- Her merkez çevresinde yaklaşık 9 km POI sorgusu yapılır.
- Yalnız rota çizgisine en fazla 2.5 km yakın kritik hizmetler tutulur.
- Duplicate OSM sonuçları iki kez sayılmaz.

**Overpass primary → Overpass mirrors → Nominatim/Photon fallback → safe unknown**

HTTP 200 olsa bile bozuk/eksik Overpass payload başarılı boş sonuç sayılmaz; mirror/fallback zincirine geçilir.

## LANU Brief

Her kategori için:
- toplam eşleşme sayısı,
- ilk noktanın rota başlangıcından yaklaşık kilometresi,
- noktanın en yakın rota segmentine kuş uçuşu uzaklığı

gösterilir.

Örnek:
> Benzinlik 4 • ilk 7.2 km • rotadan ~180 m

“rotadan ~X m” **araçla sapma/yol mesafesi değildir**; düz rota geometrisine kuş uçuşu yakınlıktır.

## Veri dürüstlüğü

- Provider başarılı + 0 sonuç → yüklü kaynakta yok.
- Provider/fallback başarısız → doğrulanamadı.
- Bazı rota merkezleri fallback/eksik → kısmi kapsam.
- Kaynak başarısızken “0 hizmet var” denmez.

## Async güvenliği

- routeId + generation guard.
- Rota değişince eski job iptal edilir.
- Geç cevap yeni rotayı ezemez.
- Reroute sonrası kritik hizmetler yeni rota için yeniden yüklenir.
- Arama/navigasyon temizliğinde state sıfırlanır.

## 1.1.14 radar hotfix korunur

Bu sürüm güncel 1.1.14 main üzerine kuruludur. Seçili rota radar prefetch readiness, alternatif rota radar özeti ve navigasyonu bloklamama davranışları korunur.

## Kabul

- unit test
- lint
- Android emulator instrumentation/smoke
- debug APK build
- SHA-256
- latest GitHub Release asset doğrulaması
- APK'nın 1.1.15 main commit'inden üretildiğinin kontrolü

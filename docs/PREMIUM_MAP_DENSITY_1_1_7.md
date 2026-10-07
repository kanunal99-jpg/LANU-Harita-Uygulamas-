# LANU 1.1.7 — Premium Map Density

## Amaç

Ana haritanın şehir ölçeğinde bilgi vermeye devam ederken işaret kalabalığına dönüşmesini engellemek.

## POI davranışı

- Seçili bir kategori (Benzinlik, Eczane, Hastane vb.) **zoom 10.5+** seviyesinde aranabilir.
- Tüm POI katmanı **zoom 13+** seviyesinde açılır.
- Arama yarıçapı sabit 8 km değildir; görünür viewport'a göre **4–20 km** arasında hesaplanır.
- Harita hareket ettikçe yenileme eşiği zoom seviyesine göre değişir.
- Uzak zoom'da gerçek POI listesi mekânsal olarak seyreltilir.
- Yakın zoom'da daha fazla gerçek POI görünür.
- Sahte cluster, hayali POI veya uydurma işletme üretilmez.
- Marker tıklama yalnızca gerçekten ekranda render edilen POI'ler üzerinde çalışır.

## Marker yoğunluğu

Yaklaşık görünür POI üst sınırı:

- şehir/geniş alan: 18–26
- orta zoom: 36–50
- yakın zoom: 70–90
- çok yakın zoom: 120

Mekânsal minimum mesafe zoom arttıkça azalır; böylece detay kontrollü biçimde açılır.

## Trafik ışıkları

Trafik ışıkları yüksek yoğunluklu kavşak verisidir. Bireysel trafik lambaları artık **zoom 15+** sokak seviyesinde gösterilir. Uzak zoom'da sorgu yapılmaz ve eski marker tutulmaz.

## Dayanıklılık

POI veri zinciri değişmez:

**Overpass primary → Overpass mirrors → kategori arama fallback → güvenli boş durum**

Yeni density policy yalnızca hangi gerçek noktaların haritada gösterileceğini kontrol eder; veri doğruluğunu değiştirmez.

## Test

- kategori ve genel POI minimum zoom davranışı
- viewport arama yarıçapı sınırları
- yoğun marker listesinde seyrekleştirme
- yüksek zoom'da ayrıntının artması
- mevcut POI / trafik ışığı / Android smoke testleri

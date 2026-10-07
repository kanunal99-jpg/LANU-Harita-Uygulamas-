# LANU 1.1.12 — Rota Öncesi Radar Özeti + Son 5 km Sesli Kademeli Uyarı

## İstenen davranış

### Rota öncesi
Navigasyon başlamadan önce seçili güzergâhın tamamı radar/hız kamerası açısından taranır.

Her doğrulanmış sabit kamera için:
- rotanın başlangıcından yaklaşık kilometre konumu,
- OSM üzerinden yakın yol adı,
- varsa yakın isimli tesis/işletme/nokta,
- varsa doğrulanmış hız sınırı
sesli olarak söylenir.

Örnek:
- “Rotanın 20. kilometresinde, D100 üzerinde, X Tesisi yakınında sabit hız kamerası var.”
- “Rotanın 30. kilometresinde, Kuzey Marmara Otoyolu üzerinde sabit hız kamerası var.”

OSM yol/yer bağlamı bulunamazsa mevcut Nominatim ters adres çözümleme fallback'i kullanılır.
O da başarısızsa konum uydurulmaz; koordinat tabanlı güvenli fallback kullanılır.

### Sürüş sırasında
Uzun mesafe anons yapılmaz.

Sesli radar uyarıları yalnızca son 5 km içinde başlar:
- 5.0 km
- 4.5 km
- 4.0 km
- 3.5 km
- 3.0 km
- 2.5 km
- 2.0 km
- 1.5 km
- 1.0 km
- 0.5 km

Her kademe aynı kamera için yalnız bir kez anons edilir.

## Veri kapsamı

Bu sürüm OpenStreetMap üzerinde doğrulanmış sabit hız kamerası noktalarını kullanır.
Kaynağı doğrulanmayan mobil radar/polis kontrol noktaları kesin gerçek olarak gösterilmez.

## Tam güzergâh taraması

Harita görünümündeki yakın kameralarla yetinilmez.
Rota geometrisi boyunca örtüşen prefetch merkezleri oluşturulur ve rota başlamadan önce tüm güzergâh taranır.

Canlı sürüşte ayrı, daha küçük yakın-araç prefetch alanı korunur; böylece son 5 km uyarıları güncel kalır.

## Ses sırası

Rota radar özeti kuyruğa alınmışsa navigasyon başlangıç sesi özeti kesmez.
Radar katmanı açıkken rota özeti hazırlanmadan navigasyon başlatılmaz.

## Doğruluk

- kamera mesafesi sürüşte kuş uçuşu değil rota üzerinde kalan mesafeden hesaplanır;
- rota öncesi kilometre, rotanın başlangıcından kamera projeksiyonuna kadar olan mesafedir;
- yol/tesis adı yalnız kaynakta varsa söylenir;
- hız sınırı yalnız kaynakta varsa söylenir;
- konum bilgisi uydurulmaz.

## Test kapıları

- sürüş uyarısı 5 km dışına çıkamaz;
- 500 m kademeler korunur;
- rota kilometre sıralaması doğrulanır;
- uzun rota prefetch merkezleri testi;
- OSM yakın yol/yer zenginleştirme testi;
- rota-km + yer ses metni testi;
- lint + unit + Android emulator smoke + APK + SHA-256 + Release.

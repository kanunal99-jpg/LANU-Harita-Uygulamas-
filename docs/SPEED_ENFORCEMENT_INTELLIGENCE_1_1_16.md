# LANU 1.1.16 — Speed Enforcement Intelligence

## Amaç

LANU artık bütün OSM denetim noktalarını tek bir “sabit hız kamerası” etiketi altında toplamaz.

Kaynak etiketleri izin verdiğinde şu sınıflar ayrılır:

- Sabit hız kamerası
- Kırmızı ışık kamerası
- Hız + kırmızı ışık kombine kamera
- Ortalama hız denetimi noktası

Ortalama hız **koridoru** bu sürümde noktasal kamera gibi uydurulmaz. Bu sürüm yalnız kaynakta görülen enforcement noktasını doğru tipler; koridor başlangıç/bitiş modeli ayrı geliştirme olarak ele alınacaktır.

## OpenStreetMap sorgusu

Overpass sorgusu artık yalnız `highway=speed_camera` değil, kaynakta açıkça bulunan `enforcement=maxspeed / traffic_signals / average_speed / section_control` etiketlerini de tarar.

Her sonuç:
- gerçek OSM node koordinatı,
- varsa maxspeed,
- direction,
- operator,
- ref/reference,
- yakın yol/yer bağlamı
ile tutulur.

## LANU Brief

Tam rota taraması bittiğinde özet artık yalnız “X sabit kamera” demez.

Örnek:
- 2 sabit hız kamerası
- 1 kırmızı ışık kamerası
- 1 hız + kırmızı ışık kamerası

“Kaynakta kayıt bulunamadı” sonucu yine sahada kesinlikle kamera olmadığı anlamına gelmez.

## Sesli sürüş uyarısı

Mevcut son 5 km / 500 m eşik sistemi korunur.

Uyarı kamera tipini söyler:
- “... sabit hız kamerası var.”
- “... kırmızı ışık kamerası var.”
- “... hız ve kırmızı ışık kamerası var.”
- “... ortalama hız denetimi noktası var.”

Doğrulanmış hız limiti yoksa limit uydurulmaz.

## Cache

Persistent last-known-good cache artık kamera tipini de saklar.
Eski cache kaydında tip alanı yoksa güvenli geriye uyumluluk için yalnız mevcut eski OSM speed-camera davranışı FIXED_SPEED olarak okunur.

## Güvenlik / veri dürüstlüğü

Canlı polis/jandarma/seyyar radar konumunun toplulukla paylaşılması bu sürümde yoktur.
LANU denetim verisini kaynakta bulunandan daha kesin göstermeyecektir.

## Test kapıları

- sabit hız kamera sınıfı
- kırmızı ışık kamera sınıfı
- kombine kamera sınıfı
- average-speed enforcement point sınıfı
- highway=speed_camera olmadan enforcement node parse
- typed sesli uyarı
- typed LANU Brief özeti
- lint + unit + Android emulator smoke + APK + SHA-256 + Release

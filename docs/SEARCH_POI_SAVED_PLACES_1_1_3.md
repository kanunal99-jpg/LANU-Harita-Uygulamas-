# LANU 1.1.3 — Kayıtlı Yerler, POI ve İşletme Araması

## Kullanıcı özellikleri

- Ev, İş ve Özel türünde adres kaydetme.
- Toplam 500 kayıt kapasitesi.
- Ev ve İş kayıtları tekildir; yeni kayıt eski Ev/İş kaydının yerini alır.
- Aynı başlık ve koordinatın yanlışlıkla tekrar kaydedilmesi engellenir.
- Kayıtlar arama panelinde görünür, seçilebilir ve silinebilir.

## POI zinciri

POI sorgusu artık yalnızca OSM node kayıtlarıyla sınırlı değildir.

**Overpass primary → Overpass mirrors → Nominatim/Photon kategori fallback → güvenli boş durum**

- node / way / relation (`nwr`) desteklenir.
- way/relation için Overpass `center` koordinatı kullanılır.
- 8 km birincil POI zarfı ve kategori fallback'i bulunur.
- Eczane, hastane, market/toptan, restoran, kafe, yakıt, ATM, otopark ve şarj istasyonları desteklenir.

## İşletme araması

Adres geocoding zincirine işletme niyeti katmanı eklenmiştir:

**Nominatim → Photon → OSM işletme araması → cache**

“toptan”, “donuk”, “üretici”, “fabrika”, “bayi”, “depo” gibi ifadeler işletme araması olarak değerlendirilir. OSM işletme fallback'i kullanıcı konumu olduğunda sınırlı bir 30 km alanda çalışır; ülke çapında pahalı/limitsiz sorgu yapılmaz.

## Global Donuk Gıda doğrulaması

Global Donuk Gıda için resmi web sitesinde yayımlanan adres arama eş adı olarak tanımlanmıştır:

**Osmangazi Mahallesi, Melikşah Sokak, No:33, Sancaktepe, İstanbul**

Uygulama koordinatı sabit veya hayali olarak üretmez. Resmi adres, normal geocoding zinciriyle çözülür ve yalnızca adres bağlamı doğrulanırsa sonuç “LANU Doğrulanmış” olarak sunulur.

## Arayüz

- Koyu temada 2D/3D seçili kart kontrastı düzeltildi.
- Hız kamerası işaretçisi hedef benzeri kırmızı daire yerine belirgin kamera piktogramına dönüştürüldü.
- Katman ekranında kırmızı kamera simgesinin anlamı açıkça yazılır.
- Trafik lejandı dar ekranlarda taşmayacak şekilde düzenlendi.
- Arama alanı işletme aramasını açıkça ifade eder ve tema rengi kullanır.

## Test kapısı

- 500 kayıt kapasitesi ve Ev/İş replace politikası.
- Global Donuk Gıda eş adı ve doğru doğrulanmış sonucun yanlış isim eşleşmesini geçmesi.
- “toptan donuk” işletme niyeti ve sorgu varyasyonları.
- POI `nwr` sorgusu ve way-center eczane parse testi.
- Mevcut adres, navigasyon ve kamera testlerinin tamamı.
- Android lint, JVM test, instrumentation/smoke, APK build ve SHA-256.

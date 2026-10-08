# LANU 1.1.18 — Türkiye idari veri doğruluğu

## Durum ve kaynak

- 81 il; 973 ilçe, her ilçenin ebeveyn il kaydıyla eşlenmesi.
- Ekim 2025 tarihli İçişleri Bakanlığı verilerinden derlenen, CC0 1.0 lisanslı açık kaynak veri: https://github.com/sabrigunes/turkey-administrative-units-dataset
- İlçe sayıları kaynak anlık görüntüsünden gelir; **8 Ekim 2026 tarihli canlı resmî kayıt olarak sunulamaz.**
- İstanbul'un 39 ilçesi (25 Avrupa / 14 Anadolu) İBB'nin yaka bazlı ayrımıyla denetlenir: https://ibb.istanbul/ibb/belediye-hakkinda/yetki-alani/

## Düzeltme

`TurkishAddressHelper` artık yalnızca 54 popüler ilçeyi temel alan adres ayrıştırıcısından daha kapsamlı bir ebeveyn-il indeksine geçer.

İl bilinirse ilçe yalnız o il içindeki resmî listeden seçilir. İl verilmemişse aynı ismi farklı illerde taşıyan `Merkez` gibi ilçeler **otomatik atanmaz**. Tanınmayan artık sözcükler 'ilçe' diye tahmin edilmez; geocoding araması için metin olarak tutulur. İl adının Türkçe harfler içermesi durumunda eşleşen karakter aralığı doğru temizlenir.

`TurkishDistrictDirectory.istanbulSideOf` yaka sınıflandırmasını tüketen arayüz ve arama bölümleri için hazırlanmıştır.

## Sınırlar

Bu aşama güncel mahalle/cadde/sokakların tamamını kapsamaz, geocoder servisinin 2026 canlı doğruluğunu garanti etmez, otomatik yaka filtreli ekran oluşturmaz. Kaynak yıllık kontrol, idari değişiklik differ ve gerçek cihaz testleri ile tamamlanacaktır. Bu nedenle il/ilçe veri altyapısı ile tüm adres kapsaması aynı iddia değildir.

## Testler

81 il, 973 ilçe, 39 İstanbul ilçesi, 25/14 ayrım, ebeveyn ilişki, belirsiz Merkez, yanlış il-ilçe, Türkçe karakter ve bilinmeyen adres testleri.

Release yalnız lint + unit + emulator instrumentation + APK + SHA-256 + GitHub Release kontrolleri geçince yapılacaktır.

# LANU 1.1.20 — Çok bölgeli gerçek radar fallback

## Kök neden

Önceki `SafetyCameraCache`, farklı Overpass bbox bölgelerinin sonuçlarını her kayıt sırasında tek bir `last_known_good` anahtarına yazıyordu. İstanbul→Ağrı gibi çok merkezli taramalarda son bbox dışındaki alanlar uygulama yeniden açıldığında önbellekten okunamıyordu. Büyük JSON serileştirmesi ayrıca kullanıcı arayüzünde çalışabiliyordu.

## Uygulama

- `SafetyCameraCacheIndexPolicy` ile **48** coğrafi alan ve **24 saat TTL** sınırı; aynı bbox güncellenirken yeni kopya oluşmuyor, en eski alanlar atılıyor.
- Her alan ayrı SharedPreferences payload'ında saklanıyor; metadata index'i bozulmuş alan diğer alanları silmiyor. Eski `last_known_good` biçimi **okuma fallback** olarak korunuyor.
- Önce önbellekteki eşleşen en yeni alan, o kayıtta veri yoksa diğer alanlar ve legacy fallback deneniyor.
- `SafetyCameraRepository` JSON okuma/yazmayı `Dispatchers.IO` üzerinde yapıyor.
- Önbellek kaydından bulunan veri **canlı** veri olarak gösterilmiyor. `SafetyCameraFetchResult.Success(fromCache=true)` işareti ve 1.1.19 sürümünün kısmi kaynak kapsaması koruması aynı kalıyor.
- Boş/eskimiş alan veya eşleşmeyen merkez yanlış biçimde sıfır kamera doğrulaması oluşturmaz.

## Kabul/regresyon

- Birbirinden uzak iki bölgede bağımsız index kayıtları
- Aynı bbox tekrar kaydında deduplikasyon
- 48 bölge üstünde oldest eviction
- 24 saat TTL, gelecek timestamp, farklı coğrafya ve geçersiz bbox filtreleri
- Mevcut Android lint/unit/emulator smoke ve production APK release/indirme checksum süreçleri

## Açık sınırlar

- 48 bölge sınırı nedeniyle çok uzun rotanın tamamı her koşulda önbellekte tutulmayabilir.
- Önbellek başlığındaki kapsama yalnız bbox merkezine yakın kaynaklı eski kayıtları ifade eder; tam bbox canlı doğrulaması değildir.
- Harita üzerindeki gerçek radar varlığını veya güncelliğini OSM tek başına garanti edemez; yeni dış servis maliyeti yoktur.

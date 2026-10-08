# LANU 1.1.22 — Kaynağa sadık hız limiti ve güvenli sürüş alarmı

## Faz 2 / Araştır → Kök neden

OSM `maxspeed` metni her zaman tek, şu anda uygulanabilir ve yasal olarak doğrulanmış bir hız limiti değildir. Örnekler: `50;70` (birden fazla değer), `50 @ (Mo-Fr 07:00-19:00)` (koşul), `TR:urban` (kod), `50-70` (aralık). Önceki `parseSpeedLimitKmh` ifadesi içindeki **ilk sayıyı** çekip sesli uyarıda doğrulanmış gibi okuyordu. Ayrıca NaN/Infinity mesafe veya hız değeri etkileşiminde yanlış kova/ETA veya aşırı hız algısı riski vardı.

## Düzelt → Uygula

- `parseSpeedLimitKmh` artık yalnız **tek, koşulsuz sayısal hız** ve opsiyonel `km/h`, `kmh`, `kph`, `mph` birimlerini kabul eder. Belirsiz, koşullu, araç türü veya birden fazla değer içeren metinler `null` olarak kalır.
- Kaynağı kesinleştirilmeyen sayılarla **overspeed uyarısı oluşturulmaz**.
- Sürüş sesinde `Doğrulanmış hız sınırı` yerine `Kamera kaynağında belirtilen hız sınırı`; rota sesinde `Kaynakta belirtilen hız sınırı` kullanılır.
- Kamera detayındaki ham `maxspeed` değeri `OSM hız etiketi` olarak işaretlenir; ülke mevzuatı/işaret doğrulaması iddia edilmez.
- NaN/Infinity mesafeler, yanlış kamera uyarısı ve ETA üretmeyecek şekilde ele alınır. Geçersiz hız ile overspeed üretilmez.
- Regresyonlar: `50;70`, `50 @ ...`, `TR:urban`, `50 mph`, `50.0 km/h`, eksik/bozuk/sınır dışı veriler ve sesli metinde koşullu limitin yokluğu.

## Kısıtlar

Bu sürüm **ülke mevzuatına göre tüm yol kesimlerinin yasal hız sınırını çözümleyen bir motor değildir**. İşaret levhaları ve güvenli sürüş önceliklidir. Ortalama hız koridorları için gerçek **başlangıç/bitiş + geçerli limit + mesafe + zaman + kaynak** modeli ayrıca doğrulanmadan ortalama hız hesaplanmış gibi gösterilmez. OSM veri kapsama/güncellik sınırlı olabilir.

## Test, yayın, doğrulama

- [ ] Android PR lint/unit/emulator smoke
- [ ] P1 Professional Gate (server+Android)
- [ ] `v1.1.22` kalıcı APK + SHA-256 + signing info
- [ ] İndirme, checksum, certificate fingerprint ve production smoke
- [ ] Fiziksel cihaz sürüm üstüne kurulum (GitHub stable signing secret hâlâ bağımsız #55)

Tüm kontroller yeşil olana kadar tamamlandı sayılmaz. Ücretli servis veya yeni anahtar eklenmez.

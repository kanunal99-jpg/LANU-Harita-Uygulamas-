# LANU 1.1.21 — Radar fallback entegrasyonu, cache kotası, APK imza kimliği

## Araştırma ve kök neden

1. 1.1.20'de HTTP 200 içindeki Overpass hata yanıtları engellendi, ancak **birinci kaynak hata → ikinci kaynak başarı** zinciri gerçek `SafetyCameraService` nesnesi üzerinden otomatik HTTP entegrasyon testiyle henüz güvence altına alınmamıştı.
2. 48 coğrafi bölgeli önbellek sayıya ve 24 saate göre sınırlanıyordu, **toplam disk byte kotası** yoktu.
3. Main CI her çalışmada yeni bir debug keystore ürettiğinden GitHub Release'de indirilen iki farklı sürüm aynı Android imzasını taşımayabiliyor. APK SHA-256 doğru olması sertifikaların aynı olduğunu kanıtlamaz.

## Uygulama

- `SafetyCameraMirrorFallbackTest` gerçek request/fallback kod yolunu OkHttp in-memory interceptor ile test eder. Başarı, `endpointUsed`, 200/remark, 503, bütün kaynakların bozuk dönmesi ve geçersiz bbox kapsanır. **Gerçek internet/ücretli API kullanılmaz**.
- İptal edilmiş radar taraması `CancellationException` istisnasını yutarak diğer aynalara geçmez.
- Disk cache: en fazla **48 bölge**, **24 saat**, **3.000.000 byte toplam**, **200.000 byte tek alan**. Büyük payload saklanmaz; navigasyon kesilmez. Eski v2 indeksindeki `bytes` bilgisi olmayan alanlar diskteki payload boyutuyla okunur.
- GitHub Releases için uygulamanın **gerçekte imzalandığı** sertifikanın SHA-256 değeri `apk-signing-info.txt` olarak oluşturulur. Yayından indirilen APK'nin sertifikası bu dosyayla yeniden karşılaştırılır.
- `LANU_DEBUG_KEYSTORE_BASE64` repository Actions secret yapılandırılmışsa main derlemeler aynı keystore'u kullanabilir. Yoksa mevcut ephemeral debug imzası korunur, CI açık uyarı verir ve rapor `source=ephemeral-debug-unsafe-upgrade` gösterir.

## Kalıcı debug sertifikası yapılandırması (kullanıcının açık işlemi gerekiyor)

Sabit keystore **yeni, bir kez** güvenli makinede oluşturulmalı ve şifreli GitHub Actions secret `LANU_DEBUG_KEYSTORE_BASE64` içine kaydedilmelidir. Oluşturulan dosya/anahtar **repo'ya commit edilmemeli, sohbetle gönderilmemeli**; birden fazla cihazla paylaşılmamalı. Bu keystore, mevcut Gradle `debugConfig` imzasıyla uyumlu biçimde **`androiddebugkey`** takma adı ve **`android`** (debug için) parola bilgisi taşımalıdır. İlk yapılandırmadan sonra **en az iki ardışık sürümün cihaz üstüne güncelleme testi** yapılmalıdır.

**Önemli:** Daha önce farklı rastgele debug sertifikasıyla kurulmuş APK'lar, yeni sabit imzaya otomatik yükseltilemez. Android yükleme imzasını kabul etmediğinde uygulama kaldırma veri kaybına neden olabilir. Kullanıcı verisi için doğrulanmış güvenli dışa aktar/geri yükleme yapılmadan kaldırma önerilmez. Bu adım tamamlanana kadar #55 AÇIK kalır. Debug APK üretim imzalı Play Store yayını değildir.

## Test ve yayın kriterleri

- [ ] Android PR lint / JVM unit / emulator instrumentation
- [ ] P1 Professional Gate server / Android
- [ ] Ana dal Android APK workflow ve `v1.1.21` sürüm APK
- [ ] SHA-256 + indirilen APK sertifika fingerprint eşleşmesi
- [ ] `latest` APK ve production smoke
- [ ] Sabit Actions secret kurulumu ve iki sürüm üstüne güncelleme (**ayrı kullanıcı eylemi**, henüz tamamlanmadı)
- [ ] Uzun rota gerçek fiziksel cihaz performans testi (**açık**)

## Kaynak/kısıtlar

Üçüncü taraf yeni ücretli API, servis veya maliyet eklenmedi. Sahadaki OSM veri güncelliği garanti edilmez; kaynak hatası ile sıfır kamera ayrımı devam eder. Dört fazlı proje anayasası geçerlidir.

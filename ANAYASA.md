# LANU Harita — Proje Anayasası

**Durum:** Zorunlu  
**Yürürlük tarihi:** 2026-10-06  
**Kapsam:** Android uygulaması, canlı paylaşım sunucusu, CI/CD, GitHub Releases ve gelecekte eklenecek tüm servisler.

Bu belge LANU Harita için bağlayıcı mühendislik ve ürün doğruluğu kurallarını tanımlar. Kod, arayüz, dokümantasyon ve yayın süreçleri bu kurallarla çelişemez.

## 1. Temel ilke: doğru olmayan özellik gösterilmez

- Uygulama gerçek kaynaktan doğrulanmayan trafik, hız limiti, radar, hava durumu, konum veya rota bilgisini gerçekmiş gibi üretemez.
- Kaynak bulunamadığında güvenli davranış **uydurmak değil, bilinmiyor / doğrulanamadı / kullanılamıyor** durumuna geçmektir.
- Demo ve simülasyon özellikleri açıkça “test/simülasyon” olarak etiketlenmelidir.
- UI metni, kodun gerçekten yaptığı şeyle aynı olmalıdır.

## 2. Kritik zincir standardı

Her kritik zincir şu sırayı hedefler:

**ANA SERVİS → ALTERNATİF → GERÇEK FALLBACK → HATA YÖNETİMİ → GÜVENLİ VARSAYILAN → LOG/İZLEME → SMOKE TEST**

Bağımsız ve güvenilir bir alternatif yoksa sahte bir alternatif eklenmez. Bu durumda eksik halka dokümante edilir ve özellik güvenli biçimde degrade olur.

Bu kural özellikle şunlar için geçerlidir:

- rota hesaplama,
- harita stili / tile,
- adres ve POI arama,
- trafik,
- sabit hız kamerası / radar uyarıları,
- hava durumu,
- canlı paylaşım,
- TTS / sesli yönlendirme,
- konum,
- backend ↔ Android,
- build / deploy / release.

## 3. Sabit hız kamerası / radar kuralları

- Kaynak verisi olmayan kamera noktası üretilemez.
- Hız limiti yalnızca kaynakta varsa gösterilir; varsayılan hız limiti atanmaz.
- Navigasyon uyarısı görünür harita alanına bağlı olamaz; 5 km uyarı zarfı için veri daha geniş bir alandan önceden yüklenmelidir.
- Aktif rota varken uyarılar rota koridoruna ve mümkün olduğunca aracın ilerisine filtrelenir.
- Birincil ağ isteği başarısız olursa yapılandırılmış aynalar denenir; tüm ağ kaynakları başarısızsa yalnızca yaşı ve kapsama alanı denetlenmiş last-known-good cache kullanılabilir.
- Cache kullanılıyorsa UI kaynağı bunu açıkça belirtir.
- Mobil radar, polis kontrolü veya doğrulanmamış kullanıcı ihbarı “sabit hız kamerası” gibi gösterilemez.
- Uygulama radar tespit/jamming donanımı gibi davranmaz; yalnızca izin verilen navigasyon veri kaynaklarından konumsal uyarı üretir.

## 4. Navigasyon doğruluğu

- Gerçek navigasyon gerçek GPS gerektirir.
- GPS kalite/freshness kontrolü olmadan konum kritik kararlarda kullanılamaz.
- Rota geometrisi, mesafe ve ETA provider dönüşünde doğrulanmalıdır.
- Off-route tespiti kontrollü reroute üretmelidir; sonsuz reroute döngüsü yasaktır.
- Hız birimi Türkiye arayüzünde **km/h** olmalıdır.
- Hız aşımı yalnızca doğrulanmış hız limiti mevcutsa hesaplanır.
- Tam çevrimdışı rota hesaplama motoru yoksa “offline routing” iddiası yapılamaz; yalnızca gerçekten desteklenen cache/offline harita özelliği gösterilir.

## 5. Trafik ve hava durumu

- Canlı trafik yalnızca başarılı gerçek provider yanıtında “canlı” sayılır.
- Provider yoksa temel rota ETA’sı korunur ve durum “canlı trafik doğrulanamadı” olarak gösterilir.
- Trafik yokken rastgele yoğunluk, hız veya gecikme oluşturulamaz.
- Hava durumu provider hatasında hayali hava durumu üretilmez.

## 6. Arayüz standardı

- Kritik sürüş bilgisi tek bakışta okunabilir olmalıdır: sonraki manevra, mesafe, ETA, hız, doğrulanmış hız limiti, trafik durumu, kritik uyarı.
- Hata/fallback durumu teknik gerçeği saklamadan kullanıcı dostu biçimde gösterilmelidir.
- Renk tek başına durum aktarmamalı; metin/ikon desteği bulunmalıdır.
- Radar uyarısı mesafe, kaynak ve varsa doğrulanmış hız limitini göstermelidir.
- Küçük ekran, edge-to-edge, status/navigation bar ve erişilebilir contentDescription dikkate alınmalıdır.

## 7. Güvenlik ve gizlilik

- Gerçek API anahtarları, tokenlar, keystore parolaları ve kişisel konum verisi repoya commit edilemez.
- Paylaşım tokenları güçlü rastgele üretilmeli, süreli olmalı ve iptal edilebilmelidir.
- Uygulamanın konum geçmişi, özel provider anahtarı ve kullanıcı verisi cloud backup / device transfer ile sızdırılmamalıdır.
- HTTP cleartext kapalı kalır; istisna için belgelenmiş güvenlik kararı gerekir.
- Loglar secret/token içermemelidir.

## 8. Maliyet kuralı

- Öncelik ücretsiz/açık kaynak/local çözümler, ardından uygun free-tier çözümlerdir.
- Yeni ücretli servis, API veya kalıcı maliyet riski kullanıcı/ürün sahibinin açık onayı olmadan eklenemez.
- Ücretsiz alternatif olmadığı için güvenilirlik kuralı gevşetilemez; özellik güvenli biçimde “kullanılamıyor” durumuna geçer.

## 9. Test ve kalite kapısı

Bir özellik “bitti” sayılmadan önce, ilgili olduğu ölçüde:

1. unit test,
2. lint/static check,
3. Android instrumentation/smoke,
4. build,
5. davranış doğrulaması,
6. release artifact SHA-256 doğrulaması

başarılı olmalıdır.

CI, Anayasa dosyasının varlığını ve tanımlı kritik doğruluk kontrollerini de doğrular.

## 10. Git ve değişiklik yönetimi

- Üretim değişiklikleri branch/PR üzerinden yapılır.
- Başarısız CI ile PR merge edilmez.
- Büyük davranış değişikliği dokümante edilir.
- Ana dal mümkün olduğunda korumalı ve gerekli kontroller zorunlu olmalıdır.
- “Dokümantasyon tamam” ifadesi başarısız veya eksik çalışan kodun yerine geçemez.

## 11. APK yayın standardı

- Her önemli, yayınlanabilir Android sürümünde APK CI ile build edilir.
- APK yalnızca workflow artifact olarak kalmaz; **GitHub Releases / Yayınlar** altında cihazdan doğrudan indirilebilir olmalıdır.
- Release APK’sı için SHA-256 üretilir ve build/test zinciri başarılı olmadan yayın yapılmaz.
- Yayının hedef commit’i ve APK’nın gerçekten erişilebilir olduğu doğrulanır.

## 12. Tamamlanma tanımı

Bir geliştirme işi şu döngü tamamlanmadan kapanmaz:

**Araştır → kök nedeni bul → düzelt → uygula → build → test → doğrula → deploy/yayınla → production smoke → kanıtla**

Dış servis, erişim veya platform sınırı nedeniyle tamamlanamayan bir madde varsa “tamamlandı” denmez; sınır ve güvenli davranış açıkça kayda alınır.

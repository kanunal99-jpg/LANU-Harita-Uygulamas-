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
- Navigasyon uyarısı görünür harita alanına bağlı olamaz; sürüş hızına göre 5–10 km erken uyarı zarfı kullanılabilir ve veri en büyük uyarı zarfından daha geniş bir alandan önceden yüklenmelidir.
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

## 11.1 Her anlamlı geliştirmede APK yayın zorunluluğu

LANU'da kullanıcı tarafından cihaz üzerinde kontrol edilmesi anlamlı olan her **dişe dokunur geliştirme, özellik, davranış değişikliği veya önemli hata düzeltmesi** ayrı bir yayınlanabilir Android sürümü olarak ele alınır.

- İlgili değişiklik test kapılarını geçmeden yayınlanamaz.
- Sürüm numarası / versionCode artırılır.
- APK CI ile yeniden build edilir.
- APK yalnızca workflow artifact olarak bırakılmaz.
- İlgili APK **GitHub Releases / Yayınlar** altında cihazdan doğrudan indirilebilir biçimde bulunmalıdır.
- Release asset'in gerçekten yüklendiği, indirilebilir olduğu ve doğru commit/sürümden üretildiği doğrulanır.
- SHA-256 dosyası ilgili APK ile birlikte yayınlanır.
- Bir özellik “tamamlandı” denmeden önce kullanıcıya indirilebilir APK bağlantısı verilir.
- Dokümantasyon-only veya kullanıcı davranışını hiç değiştirmeyen küçük metin değişiklikleri zorunlu APK yayını kapsamı dışında tutulabilir; ancak kullanıcı deneyimini, veri akışını, performansı, UI'ı, navigasyonu, aramayı, uyarıları, POI'yi, kamerayı, GPS'i, hava/trafik bilgisini veya backend davranışını etkileyen değişiklikler kapsam içindedir.

Bu kural LANU'nun mevcut ve gelecekteki tüm Android geliştirmelerinde varsayılan olarak uygulanır.

## 12. Tamamlanma tanımı

Bir geliştirme işi şu döngü tamamlanmadan kapanmaz:

**Araştır → kök nedeni bul → düzelt → uygula → build → test → doğrula → deploy/yayınla → production smoke → kanıtla**

Dış servis, erişim veya platform sınırı nedeniyle tamamlanamayan bir madde varsa “tamamlandı” denmez; sınır ve güvenli davranış açıkça kayda alınır.


## 13. Ürün vizyonu: LANU Premium North Star

LANU Harita'nın hedefi yalnızca çalışan bir navigasyon uygulaması olmak değildir. Ürün; **arama + rota + canlı sürüş + güvenlik + yol zekâsı + hava durumu + kamera/EDS + POI + çevrimdışı çalışma + kullanıcı raporları + araç entegrasyonu** yeteneklerini tek sürüş deneyiminde birleştiren premium bir sürüş platformu olarak geliştirilir.

### 13.1 Rakip üstü ürün ilkesi

Her ana özellik geliştirilirken şu soru sorulur:

> “Bu özellik Google Maps, Yandex Navigator, Waze ve radar/kamera odaklı uygulamalarda nasıl çalışıyor; LANU bunu daha anlaşılır, daha ayrıntılı, daha güvenilir ve daha az sürücü etkileşimiyle nasıl yapar?”

Rakiplerde bulunan kritik kullanıcı değerleri LANU'da eksik bırakılmaz; ancak lisans, kaynak doğruluğu, güvenlik veya yasal sınırlar nedeniyle yapılamayan işlevler sahte biçimde taklit edilmez.

### 13.2 Tek tam sürüm ilkesi

LANU içinde yapay Free / Plus / Pro / Premium özellik kilitleri oluşturulmaz. Teknik olarak mevcut, maliyeti onaylanmış ve güvenli şekilde sunulabilen ürün özellikleri kullanıcıya tek tam deneyim içinde açılır.

Harici veri sağlayıcı ücretleri, platform şartları veya yasal kısıtlar bu ilkeden bağımsızdır; kullanıcı onayı olmadan ücret doğuran servis eklenemez.

### 13.3 Sürüş öncesi brifing

Rota başlatılmadan önce mümkün olduğunda tek ekranda şu bilgiler özetlenir:

- toplam mesafe, ETA ve alternatif rotalar,
- doğrulanmış trafik yoğunluğu ve tahmini gecikme,
- rota üzerindeki yol çalışması, kaza, kapanış, su baskını ve benzeri olaylar,
- rota üzerindeki sabit hız kameraları / ortalama hız koridorları / doğrulanmış hız limiti değişimleri,
- yağmur, kar, sis, kuvvetli rüzgâr, buzlanma ve görüş riski,
- ücretli yol / feribot / tünel / köprü bilgisi,
- yakıt / şarj / dinlenme / hastane / eczane gibi kritik POI'ler,
- yol tipi, keskin viraj, okul bölgesi, hemzemin geçit, şerit daralması gibi sürüş özellikleri,
- internet/GPS/veri kaynağı eksiklikleri ve hangi verilerin doğrulanamadığı.

### 13.4 Canlı sürüş kokpiti

Navigasyon sırasında temel sürüş bilgisi tek bakışta okunur ve kullanıcıyı menülere göndermeden çalışır:

- sonraki manevra + şerit yönlendirme,
- mevcut hız + doğrulanmış hız limiti,
- kalan mesafe + ETA + gecikme,
- yaklaşan kritik olay / yol özelliği / hava olayı,
- hız kamerası / koridor uyarısı,
- daha iyi rota bulundu uyarısı,
- rota dışına çıkma ve güvenli reroute,
- GPS / internet / veri kalitesi göstergesi,
- sürüş sırasında minimum dokunma ve sesli kontrol önceliği.

### 13.5 Bilgi derinliği

Kullanıcı herhangi bir olay, POI, kamera, hava uyarısı veya rota öğesine dokunduğunda mümkün olduğunca şu ayrıntılara ulaşabilmelidir:

- nedir,
- nerede,
- rotadan kaç km ileride,
- hangi yönde,
- ne kadar süredir aktif,
- kaynak,
- veri güncelliği,
- güven seviyesi,
- sürüşe etkisi,
- mümkün alternatif / öneri.

### 13.6 Premium arayüz standardı

LANU'nun arayüzü yalnızca “çalışıyor” seviyesinde kabul edilmez. Her ana ekran:

- koyu ve açık temada yüksek kontrastlı,
- tek elle kullanılabilir,
- küçük ekranlarda taşmasız,
- sürüş sırasında okunabilir,
- tutarlı ikonografi ve tipografiye sahip,
- gereksiz teknik provider isimlerini ana akıştan gizleyen,
- kritik bilgi hiyerarşisi net,
- animasyonları akıcı fakat dikkat dağıtmayan,
- erişilebilirlik içerik açıklamaları ve büyük dokunma hedefleri olan

premium ürün standardını karşılamalıdır.

### 13.7 Yol zekâsı kapsamı

LANU uzun vadede aşağıdaki kategorileri tek sistemde desteklemeyi hedefler:

- canlı trafik ve gecikme,
- kaza / yol çalışması / yol kapanışı / şerit kapanışı,
- yolda nesne / su baskını / düşük görüş / kar-buz,
- hız limiti değişimi,
- sabit hız kamerası ve doğrulanabilir koridor hız bilgisi,
- keskin viraj / hız tümseği / okul bölgesi,
- demiryolu geçidi / dar köprü / şerit bitişi / birleşme,
- yol yüzeyi ve hava kaynaklı risk,
- kullanıcı bildirimi + doğrulama + yaşlandırma sistemi,
- rota üzeri ve sürüş dışı yakın çevre uyarıları.

### 13.8 Çevrimdışı ve dayanıklılık hedefi

İnternet kesildiğinde uygulama mümkün olan en fazla işlevi sürdürür:

- indirilebilir harita,
- cached / offline rota fallback,
- daha önce doğrulanmış hız kamerası ve kritik POI cache'i,
- son bilinen yol/hız limit bilgisi yalnızca yaşı ve kapsamı doğrulanabiliyorsa,
- ağ geri geldiğinde otomatik senkronizasyon.

Çevrimdışı destek olmayan veri “varmış” gibi gösterilmez.

### 13.9 Kabul ölçütü

Yeni özellik yalnızca ekrana çizildiği için tamamlanmış sayılmaz. Tamamlanma için:

**veri doğruluğu + UX + sürüş güvenliği + fallback + performans + test + gerçek cihaz doğrulaması + release kanıtı**

birlikte değerlendirilir.


## 14. Bağlayıcı Premium Master Specification

`docs/LANU_PREMIUM_MASTER_SPEC_2026_10_08.md` bu Anayasa'nın bağlayıcı ürün kapsamı ekidir.

Bu ek özellikle:
- navigasyon yardımcı veri servislerinin navigasyon başlangıcını bloke etmemesini,
- kamera/hız denetimi sınıflarının kaynaktan dürüst biçimde ayrıştırılmasını,
- son 5 km / 500 m kamera ses eşiklerini,
- ETA-zamanlı rota hava zekâsını,
- merkezi Road Intelligence öncelik motorunu,
- rota geneli kritik hizmetleri,
- streaming uzun-rota analizini,
- kaynak/güncellik/güven görünürlüğünü,
- her anlamlı geliştirmede doğrulanmış APK Release kuralını

zorunlu ürün davranışı olarak tanımlar.

Canlı polis/jandarma/seyyar radar konumunu kullanıcılar arasında paylaşarak denetimden kaçınmayı kolaylaştıran bir özellik bu kapsamın parçası değildir. Topluluk altyapısı yol güvenliği ve doğrulanabilir yol olayları için kullanılabilir.

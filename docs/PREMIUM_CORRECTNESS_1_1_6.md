# LANU 1.1.6 — Premium Correctness Foundation

Bu sürümde hedef yeni özellik sayısını artırmak değil, kullanıcıya gösterilen her kritik
bilginin kaynağını ve yaşam döngüsünü doğru hale getirmektir.

## Kapatılan kritik açıklar

- 81 il / 973 ilçe yerel idari dizini.
- İstanbul 39 ilçe Avrupa / Anadolu yakası doğrulama tablosu.
- Aynı isimli fakat farklı konumdaki işletmeleri silen arama dedup hatası.
- OSRM alternatiflerine cevap sırasından "En Kısa / Ücretsiz" etiketi verme hatası.
- Ücret bilgisinin yokluğu ile "ücretsiz" bilgisinin birbirine karıştırılması.
- Valhalla'da talep edilen toll-free/no-ferry kısıtının cevapta doğrulanmadan UI'a taşınması.
- Reroute sonrası eski hava bilgisinin kalması.
- Reroute anında trafik refresh worker'ının sessizce sona erebilmesi.
- Sabit 8 km POI kapsamının geniş harita görünümünde yetersiz kalması.
- Arama üst alanında kesilen POI kısayolları; tüm 9 POI kategorisi artık erişilebilir.

## Ürün doğruluğu ilkesi

LANU bir rota özelliğini talep etmiş olmakla o özelliğin gerçekleştiğini aynı şey saymaz.
Örneğin bir provider toll-free rota talebine cevap verdiğinde, cevapta ücret bilgisinin
gerçekten bulunmadığı doğrulanmadan rota "Ücretsiz" olarak etiketlenmez.

## Dağıtım

CI debug APK'yı her başarılı main build'de yayınlamaya devam eder. Güvenli Android signing
secret'ları GitHub Actions'a bağlandığında aynı pipeline production-signed `app-release.apk`
üretip aynı Release'e ekler. Private signing key kaynak koda konmaz.

## Bilinçli kalan dış bağımlılık sınırları

- İkinci bağımsız gerçek canlı trafik sağlayıcısı, gerçek ve izinli bir kaynak bağlanmadan uydurulmaz.
- Türkiye'nin tamamı için yeni rotayı internetsiz hesaplayan routing graph, gerçek bölgesel graph paketi olmadan varmış gibi gösterilmez.
- Live-share deploy/restart kalıcılığı, gerçek durable store bağlanmadan tamamlandı sayılmaz.
- Emulator CI gerçek araç GPS/OEM/batarya saha kabulünün yerine geçmez.

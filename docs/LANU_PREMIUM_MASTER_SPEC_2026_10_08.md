# LANU Premium / Pro / Pro+ Master Product Specification — 2026-10-08

Bu belge LANU için bağlayıcı ürün kapsamıdır. ANAYASA.md ile birlikte yorumlanır. Çelişki halinde veri dürüstlüğü, güvenlik, maliyet onayı ve gerçek kaynak doğrulaması kuralları önceliklidir.

## 1. Harita ve navigasyon
Profesyonel 2D/3D, otomatik gece/gündüz, kuzey/sürüş/perspektif görünümü, akıcı kamera, yola snap, mümkün olduğunda şerit yönlendirme, anlaşılır kavşak/dönüş önizleme, Türkçe sesli navigasyon, alternatif/en hızlı/en kısa/ücretsiz rotalar, ücretli yol/feribot/otoyol/toprak yol tercihleri, reroute ve rotadan sapma algılama.
Yardımcı radar/hava/POI servisi navigasyon başlangıcını bloke edemez.
Her alternatif rota mümkün olduğunda süre, km, ETA, ücret, trafik, kamera, yol/hava riski, kapanış ve kritik POI özeti taşır.

## 2. LANU Brief
Navigasyon öncesi seçili rota baştan sona analiz edilir. Trafik/gecikme, kapanış/kaza/çalışma, yol tehlikesi, hız-kırmızı ışık kameraları, ortalama hız denetimi, hız limitleri, rota-zamanlı yağmur/kar/sis/fırtına/buzlanma, okul/tümsek/hemzemin geçit, ücret/feribot ve kritik POI verileri kaynak/güncellik/güven durumu ile gösterilir.
Kullanıcı “Bu yolculukta beni ne bekliyor?” sorusuna tek ekranda cevap almalıdır.

## 3. Hız denetimi veri sınıfları
LANU yalnız tek tip OSM speed_camera noktasına bağlı kalmaz. Kaynak doğrulanabildiğinde:
- sabit hız kamerası,
- EDS / hız denetimi,
- kırmızı ışık kamerası,
- hız + kırmızı ışık kombine denetimi,
- ortalama hız/section-control işaretleri ve koridorları,
- doğrulanmış hız limiti
ayrı sınıflar olarak tutulur.
Kesin olmayan veri kesin olarak etiketlenmez.

Canlı polis/jandarma/seyyar radar konumunu kullanıcılar arasında paylaşarak denetimden kaçınmayı kolaylaştıran özellikler ürün kapsamına alınmaz. Topluluk sistemi yol güvenliği ve yol olayı bildirimleri için kullanılabilir.

## 4. Rota öncesi kamera özeti
Seçili güzergâhın tamamı asenkron taranır. Rota kilometresi, kaynakta varsa yol/cadde/tesis/landmark ve doğrulanmış limit sesli/görsel özetlenir. Adres zenginleştirme navigasyon başlangıcını bloke edemez. Alternatif rota seçildiğinde eski özet iptal edilir ve routeId/generation guard ile yeni rota yeniden taranır.

## 5. Sürüş içi kamera uyarısı
Sesli kamera uyarısı yalnız son 5 km içinde başlar ve 5.0, 4.5, 4.0, 3.5, 3.0, 2.5, 2.0, 1.5, 1.0, 0.5 km eşiklerinde her kamera/eşik için yalnız bir kez verilir.
Mesafe kuş uçuşu değil rota üzerinde kalan mesafedir.
Doğrulanmış limit mevcutsa söylenir; sürücü limit üzerindeyse daha güçlü hız azaltma mesajı üretilir.

## 6. Ortalama hız koridoru
Kaynak doğrulanabiliyorsa başlangıç/bitiş, uzunluk, limit, geçen süre, kalan mesafe ve tahmini ortalama hız ayrı modelde gösterilir. Koridor bilgisi noktasal sabit kamera gibi temsil edilmez.

## 7. Hava zekâsı
Rota havası, kullanıcının rota örnek noktasına ulaşacağı ETA ile forecast saatini eşleştirir. Forecast yoksa current fallback açıkça “mevcut hava — fallback” olarak etiketlenir. Eski rota cevabı generation/routeId guard ile yeni rotayı ezemez.

## 8. Trafik ve yol olayları
Canlı trafik, yoğunluk, gecikme, kaza, kapanış, şerit kapanışı, yol çalışması, yolda cisim, sel, buzlanma, sis, heyelan, çukur ve diğer doğrulanabilir yol olayları desteklenir. Cache hiçbir zaman “canlı” olarak gösterilmez.

## 9. Road Intelligence
Tüm sürüş olayları merkezi öncelik motorundan geçer:
P0 acil → P1 güvenlik → P2 dikkat → P3 bilgi.
Aynı anda çok sayıda kart bindirmek yerine sürücüye en önemli olay önce sunulur.

## 10. Topluluk yol olayı bildirimleri
Kullanıcı; kaza, yol çalışması, yol kapalı, çukur, yolda cisim, sel, yoğun trafik, tehlike, bozuk trafik ışığı, yeni/sökülmüş sabit kamera gibi yol güvenliği odaklı olayları bildirebilir.
Her kayıt zaman, konum, yön, doğrulama/ret, güven skoru ve TTL taşır. Spam/sahte bildirim koruması gerekir.

## 11. POI ve kritik hizmetler
Benzinlik/LPG/EV şarj, hastane/acil servis, eczane, polis/jandarma hizmet noktaları, otopark, dinlenme, market, restoran, tuvalet, ATM, lastikçi, servis, çekici, otel, AVM gibi kategoriler desteklenir. Rota bağlamında kaç km sonra olduğu hesaplanır.

## 12. Türkiye idari doğruluğu
81 il, ilçeler, mahalle/cadde/sokak/POI araması; İstanbul Avrupa/Anadolu ilçe grupları; yanlış il-ilçe eşleşmelerine karşı otomatik testler.

## 13. Performans
Ana UI etkileşim hedefi <300 ms hissidir. Ağ işi UI thread'i bloke etmez. Lazy loading, cache, paralel fetch, cancellation, debounce, prefetch, stale-response guard, timeout/retry/fallback uygulanır.
1.300+ km rota analizi streaming/parçalı yapılır; ilk 100–200 km hızlı sonuç verir, kalan rota arka planda tamamlanır.

## 14. Premium kurumsal UI
Profesyonel, modern, temiz, sürücü dostu, büyük dokunma hedefleri, güçlü bilgi hiyerarşisi, taşmasız küçük ekran desteği, gündüz/gece okunabilirlik, minimum kart yığını, maksimum harita görünürlüğü.

## 15. Offline ve dayanıklılık
Mümkün olduğunca harita/rota/POI/kamera/trafik last-known-good cache sağlanır. Eski cache “canlı” olarak etiketlenmez. Ağ geri geldiğinde güvenli yenileme yapılır.

## 16. Araç entegrasyonu
Android Auto, Bluetooth araç bağlantısı, direksiyon kontrolleri, sesli kontrol, araç ekranı, OBD-II ve HUD entegrasyonları gelecek mimarisinde değerlendirilecek.

## 17. Katmanlı dayanıklılık
Her kritik özellik:
ANA SERVİS → ALTERNATİF → GERÇEK FALLBACK → HATA YÖNETİMİ → GÜVENLİ VARSAYILAN → LOG/İZLEME → SMOKE TEST.
Routing, traffic, camera/enforcement, weather, POI, geocoding, database, storage, auth, backend, APK↔backend ve production bu kapsamdadır.

## 18. Veri dürüstlüğü
“Bulunamadı” ile “yok” aynı değildir. Kaynak doğrulanamadığında açıkça doğrulanamadı denir. Her önemli veride kaynak, güncellik, doğrulama durumu ve güven seviyesi mümkün olduğunda tutulur.

## 19. Maliyet kontrolü
Ücretsiz/açık kaynak → local → free tier → mevcut ücretsiz kota → yalnız açık kullanıcı onayıyla ücretli servis.

## 20. Test ve release
Her anlamlı değişiklik: araştır → kök neden → kod → lint → unit → integration → Android emulator/device smoke → build → SHA-256 → main merge → GitHub Release APK → indirme doğrulaması → production smoke.

## 21. Çalışma talimatı
Kullanıcıdan sürekli “devam” beklenmez. Başarısız adımda log/kök neden/düzelt/test döngüsü sürdürülür.

## 22. Son ürün hedefi
LANU gerçek kullanıcıya verilebilir; premium, hızlı, güvenilir, dayanıklı, detaylı, kaynak dürüstlüğü yüksek bir navigasyon ve sürüş asistanı olmalıdır.

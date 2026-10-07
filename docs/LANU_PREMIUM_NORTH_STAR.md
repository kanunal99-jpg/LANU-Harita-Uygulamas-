# LANU Premium North Star — Ürün Yol Haritası

## Ürün hedefi

LANU, tek bir navigasyon ekranından çok daha fazlasıdır. Hedef; Google Maps'in trafik/arama gücü, Yandex'in sürücü odaklı navigasyonu, Waze'in topluluk ve yol olayı zekâsı, radar/kamera odaklı uygulamaların erken uyarı yetenekleri ve hava/yol risk katmanlarını tek bir tutarlı ürün deneyiminde birleştiren **tam özellikli premium sürüş platformu** oluşturmaktır.

LANU içinde yapay özellik kilitleri bulunmaz. Teknik, mali ve yasal olarak sunulabilen tüm özellikler tek tam üründe erişilebilir olur.

## Ana ürün yüzeyleri

| Yüzey | Hedef |
|---|---|
| Ana Harita | Arama, hızlı POI, canlı trafik, hava, yol olayları, kamera, park/yakıt/şarj, kayıtlı yerler |
| Sürüş Öncesi Brifing | Rota riskleri, hava, trafik, yol olayları, kameralar, ücretler, kritik POI, tahmini gecikme |
| Rota Karşılaştırma | En hızlı / kısa / ekonomik / düşük risk / düşük ücret / EV uyumlu rotaları ayrıntılı kıyaslama |
| Canlı Sürüş Kokpiti | Manevra, şerit, hız, limit, ETA, gecikme, yaklaşan tehlike, kamera, hava ve reroute |
| Yol Olayları | Kaza, çalışma, kapanış, şerit kapanışı, nesne, sel, görüş, kar/buz, bozuk yol |
| Yol Özellikleri | Viraj, tümsek, okul, hemzemin geçit, dar köprü, birleşme, şerit bitişi |
| Kamera & Hız | Sabit kamera, ortalama hız koridoru, doğrulanmış hız limiti, dinamik erken uyarı |
| Hava & Yol Riski | Yağmur, kar, sis, rüzgâr, buzlanma, görüş ve rota etkisi |
| POI & İşletme | Güçlü işletme araması, kategori, açık/kapalı bilgisi, yakıt/şarj/sağlık/dinlenme |
| Kayıtlı Yerler | Ev, İş, özel adresler, koleksiyonlar, son aramalar ve geçmiş |
| Çevrimdışı | Harita indirme, cache rota/kamera/POI, kontrollü degrade |
| Araç Entegrasyonu | Android Auto hedefi, Bluetooth otomatik sürüş modu, araç ekranı uyumu |
| Paylaşım | Canlı konum/ETA paylaşımı, güvenli süreli linkler |
| Ayarlar | Tüm uyarıların mesafe, ses, titreşim, öncelik ve sessiz mod kontrolleri |

## Yol zekâsı motoru

Her rota öğesi ortak bir “Road Intelligence Event” modeline dönüştürülür:

- tür
- koordinat / rota kilometresi
- yön
- başlangıç/bitiş zamanı
- önem seviyesi
- güven skoru
- kaynak
- son güncelleme
- kullanıcı doğrulaması
- sürüş etkisi
- önerilen davranış

Bu model trafik, hava, kamera, yol olayı ve yol özelliği kaynaklarının UI'da aynı tutarlı dille gösterilmesini sağlar.

## Sürüş öncesi “LANU Brief”

Kullanıcı **Navigasyonu Başlat** demeden önce otomatik brifing:

1. “Rota 64 km / 1 sa 12 dk.”
2. “+11 dk trafik gecikmesi.”
3. “3 sabit kamera, 1 ortalama hız koridoru.”
4. “22. km'de yoğun yağmur, görüş orta.”
5. “31. km'de yol çalışması; sağ şerit kapalı.”
6. “2 ücretli geçiş.”
7. “Rotada 4 benzinlik, 2 hastane, 6 eczane.”
8. “Veri eksikleri: canlı trafik X bölümünde doğrulanamadı.”

Kullanıcı her satıra dokunarak haritadaki konumunu ve detay kaynağını görebilir.

## Uyarı öncelik sistemi

- **P0 — Acil:** yol kapanışı, ciddi kaza, ters yön, kritik hava/sel riski.
- **P1 — Güvenlik:** hız limiti aşımı, kamera/koridor, keskin viraj, okul, hemzemin geçit.
- **P2 — Akış:** trafik, şerit kapanışı, yol çalışması, daha hızlı rota.
- **P3 — Bilgi:** yakıt, park, dinlenme, POI, hava değişimi.

Aynı anda birden çok olay varsa sesli uyarılar önem seviyesine göre birleştirilir; sürücü ses bombardımanına tutulmaz.

## Veri güvenilirliği

Her veri türü için:

**Primary → Alternative → Last-known-good cache → Safe unknown**

Kullanıcı detay ekranında kaynak ve güncelliği görebilir. “Canlı”, “doğrulanmış”, “topluluk bildirimi”, “cache” ve “bilinmiyor” durumları birbirine karıştırılmaz.

## Premium UX kriterleri

- Ana görev 1–2 dokunuşta.
- Arama sonucu, rota seçimi ve canlı sürüş aynı anda üst üste binmez.
- Kritik CTA hiçbir ekran boyutunda kesilmez.
- Uzak zoom'da yoğun marker kümeleri clustering / zoom gating ile sadeleşir.
- Yakın zoom'da ayrıntı artar.
- Koyu/açık tema kontrastı WCAG odaklıdır.
- Teknik provider adları yalnızca bilgi/detay sayfasında görünür.
- Her marker'ın anlamı anlaşılır ikonla gösterilir.
- Gerçek zamanlı veri yoksa UI bunu açıkça söyler.

## Geliştirme sırası

### Faz A — Temel kalite ve premium harita
Arama/POI doğruluğu, marker clustering, zoom kuralları, GPS güvenilirliği, kayıtlı yerler, arayüz tutarlılığı, performans.

### Faz B — Sürüş kokpiti
Şerit yönlendirme, hız/limit HUD, yaklaşan olay şeridi, dinamik kamera/hız uyarısı, reroute, ses/titreşim öncelik motoru.

### Faz C — LANU Brief
Sürüş öncesi trafik + kamera + hava + yol olayı + POI risk özeti ve ayrıntı ekranları.

### Faz D — Yol olayları ve topluluk
Kullanıcı bildirimi, onay/ret, yaşlandırma, güven skoru, moderasyon ve spam koruması.

### Faz E — Hava/yol risk motoru
Yağmur, kar, sis, buzlanma, kuvvetli rüzgâr, su baskını ve rota etkisi.

### Faz F — Çevrimdışı ve araç
Offline map paketleri, kontrollü offline routing/cache, Android Auto uyumlu sürüş ekranı, Bluetooth otomatik sürüş modu.

## “Bitti” kriteri

LANU'nun hedefi rakiplerdeki özellik adlarını kopyalamak değil; **aynı sürüş problemini daha az etkileşimle, daha fazla açıklıkla ve daha güvenilir veriyle çözmek**tir.

Her faz için gerçek cihaz ekran görüntüsü, CI kanıtı, performans ölçümü ve kaynak/fallback doğrulaması gereklidir.

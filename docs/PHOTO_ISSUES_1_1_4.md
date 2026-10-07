# LANU 1.1.4 — Fotoğraf Bazlı Arama / Rota / GPS Düzeltmeleri

## Düzeltilen problemler

1. **Arama ve eski rota aynı anda görünmüyor**
   - Arama alanı odaklandığında önceki hedef ve rota seçimi geçersizleştirilir.
   - Yeni sorgu eski rota üretiminin sonucunu UI'a geri yazamaz.
   - Arama sonucu seçildiğinde arama paneli kapatılır; hedef kartı tek aktif bağlam olur.
   - Son arama seçimi artık tekrar arama tetikleyerek hedef state'ini silmez.

2. **GPS konumu ile navigasyon uygunluğu ayrıştırıldı**
   - Mavi canlı nokta yalnızca taze/doğru GPS için kullanılır.
   - Eski veya navigasyon için yetersiz konum gri gösterilir.
   - Hata mesajı artık nedenini söyler: konum yok / eski / doğruluk yetersiz.
   - GPS hata snackbar'ında **Konumu Yenile** eylemi vardır.
   - Snackbar rota ve hedef kartının üstüne binmeyecek bottom inset kullanır.

3. **5 haneli Türkiye posta kodu araması**
   - `34953` gibi tek başına 5 haneli sorgu bina numarası sayılmaz.
   - Nominatim structured `postalcode` sorgusu kullanılır ve `countrycodes=tr` ile sınırlandırılır.
   - Tam posta kodu eşleşmeleri sıralamada yüksek öncelik alır.

4. **Mahalle / ilçe merkezinin park veya boş alana düşmesi**
   - Mahalle/ilçe/şehir gibi geniş alan sonuçlarında geocoder centroid'i ilk gösterim için korunur.
   - Rota sağlayıcısı bir sürülebilir endpoint döndürdüğünde hedef pini bu endpoint'e taşınır.
   - POI ve kesin adresler bu politika tarafından taşınmaz.
   - 2,5 km'den uzak şüpheli endpoint kabul edilmez.

5. **Rota kartları**
   - Seçili kart Material Theme `primaryContainer/onPrimaryContainer` kontrastını kullanır.
   - Seçilen rota otomatik olarak görünür alana scroll edilir.
   - Navigasyon CTA tek satırdır: **Navigasyonu Başlat**.
   - Dar ekranlarda buton metni kesilmez.

6. **Hedef işareti**
   - Kamera işaretleriyle karışan kırmızı daire kaldırıldı.
   - Hedef artık mor pin simgesi ile gösterilir.
   - Sabit hız kamerası kırmızı kamera simgesi olarak kalır.

## Testler

- `NavigationLocationPolicyTest`: missing / stale / inaccurate / ready GPS durumları.
- `PostalCodeSearchTest`: 34953 parse, structured query ve ranking.
- `DestinationSnapPolicyTest`: geniş alan hedef snap sınırları.
- `MainViewModelSearchStateTest`: search → destination → search state ayrımı.
- Mevcut unit, lint, Android instrumentation/smoke ve APK build kapıları korunur.

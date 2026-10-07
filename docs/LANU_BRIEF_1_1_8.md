# LANU 1.1.8 — LANU Brief v1

## Amaç

Rota seçimi ile navigasyon başlangıcı arasına kaynak-backed bir sürüş öncesi kontrol katmanı eklemek.

## Gösterilen bilgiler

LANU Brief seçili rota için:

- toplam mesafe ve tahmini süre,
- doğrulanmış canlı trafik gecikmesi veya “doğrulanamadı” durumu,
- Open-Meteo rota örneklerinden yağmur / kar / sis / fırtına özeti,
- yüklü OpenStreetMap sabit kamera verisinin rota koridoru eşleşmesi,
- ücretli geçiş,
- feribot,
- veri kalitesi özeti

gösterir.

## Veri dürüstlüğü

Brief bir tahmin motoru değildir. Kaynak yoksa bilgi üretmez.

- Trafik doğrulanamazsa temel rota süresi korunur ve “Canlı trafik doğrulanamadı” gösterilir.
- Hava isteği sonuç vermezse hava uydurulmaz.
- Kamera verisi uzun rotada tam rota kapsamı garanti edilmiyorsa sonuç “Kısmi kapsama” olarak etiketlenir.
- Ücret / feribot bilgisi yalnızca rota sağlayıcısının bayraklarından gelir.
- Her detay satırında kaynak ve veri durumu bulunur.

## UI

Rota alternatifleri ekranında “LANU Brief” kartı görünür.

Kapalı durumda en önemli üç başlık gösterilir. “Detay” ile:

- ayrıntı,
- kaynak,
- doğrulanmış / kısmi / doğrulanamadı durumu,
- toplam veri kalite özeti

açılır.

## Hava akışı

Birincil rota seçildiğinde hava verisi otomatik yüklenir. Kullanıcının rotaya tekrar dokunması gerekmez. Yeni rota hesaplanırken eski rotanın hava verisi temizlenir.

## Sonraki genişletmeler

LANU Brief v2 hedefleri:

- kritik POI sayıları ve rota üzeri sapma mesafesi,
- rota genelinde tam kamera kapsama yüzdesi,
- yol çalışması / kapanış / olay motoru,
- yağışın rota kilometresi ve tahmini varış zamanı ile eşleştirilmesi.

## Test kapısı

- eksik trafik/hava verisinin uydurulmaması,
- doğrulanmış trafik gecikmesi,
- hava risk özeti,
- uzun rota kamera bilgisinin “kısmi” işaretlenmesi,
- lint + unit + Android smoke + APK build + Release + SHA-256.

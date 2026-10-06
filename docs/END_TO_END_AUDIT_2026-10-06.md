# LANU Harita — Uçtan Uca Denetim

**Tarih:** 2026-10-06  
**Kapsam:** Android kaynak kodu, hız kamerası/radar veri zinciri, navigasyon, trafik, harita UI, canlı paylaşım, güvenlik, test/CI ve APK yayın zinciri.

## Yönetici özeti

Denetimde uygulamanın önemli temel bileşenlerinin mevcut olduğu, fakat bazı ürün iddiaları ile gerçek çalışma zinciri arasında boşluklar bulunduğu görüldü. En kritik bulgular radar uyarısının yalnızca görünür harita viewport’undan veri alması, sürüş ekranında km/h verisinin km/s olarak yazılması, bitmiş bir hardening PR’ının main’e alınmamış olması, bağlayıcı proje Anayasasının bulunmaması, sample backup kuralları ve kullanılmayan Firebase AI bağımlılıklarıydı.

Bu çalışma bu maddeleri kod seviyesinde düzeltir ve CI ile tekrar oluşmalarına karşı koruma ekler.

## Tamamlanan düzeltmeler

### P0/P1 — Sabit hız kamerası / radar

- Overpass primary + mirror yapısı korunarak repository katmanı eklendi.
- Ağ kaynakları tamamen başarısız olduğunda 24 saatlik persistent last-known-good fallback eklendi.
- Cache verisi UI’da `OpenStreetMap (önbellek)` olarak ayırt edilir.
- 5 km uyarının dar navigasyon viewport’u yüzünden veri görememesi düzeltildi.
- Navigasyon sırasında 6.5 km çevre bağımsız olarak prefetch edilir; 1.2 km anlamlı hareket sonrası yenilenir.
- Sık GPS update’lerinin aktif prefetch’i sürekli iptal etmesini önleyen request-starvation düzeltildi.
- Uyarı adayları aktif rota koridoruna ve kullanıcının yaklaşık ilerisine filtrelenir.
- Radar kartı mesafe, kaynak, doğrulanmış hız limiti ve overspeed durumunu gösterir.
- Hız limiti kaynağı yoksa değer uydurulmaz.
- Saf geometri ve rota filtreleme unit testleri eklendi.

### P0/P1 — UI ve doğruluk

- Sürüş hız göstergesi `km/s` → `km/h` düzeltildi.
- Yolculuk özeti ortalama hız birimi `km/h` düzeltildi.
- Trafik canlı değilken “statik profil” gibi belirsiz bir etiket yerine gerçek provider durum mesajı gösterilir.
- Radar katmanı açıklaması gerçek kapsamı (OSM sabit hız kameraları) belirtecek şekilde netleştirildi.

### P1 — Mimari / bakım

- Açık ve CI’dan geçmiş eski hardening PR #18 main’e merge edildi.
- Live-share timeout/retry ve TTS lifecycle hardening main’e taşındı.
- Android sürümü `1.1.0` / versionCode 2’ye yükseltildi.
- Kullanılmayan Firebase AI/AppCheck ve Google Services build bağımlılıkları kaldırıldı.
- `metadata.json` gerçek yeteneklerle eşlendi.
- `.env.example` yalnızca kullanılan entegrasyonları içerir.

### P1 — Güvenlik / gizlilik

- `allowBackup=false`.
- Sample backup/data-extraction dosyaları kaldırılarak shared preferences, database, files ve external data açık biçimde backup/device-transfer dışında bırakıldı.
- Cleartext trafik zaten kapalıydı; korunuyor.

### P1 — Android yaşam döngüsü

- Aktif navigasyon için `foregroundServiceType="location"` servisi eklendi.
- Android 13+ bildirim izni akışı mevcut konum iznini bozmadan yönetilir.
- Servis navigasyon başlangıcında başlar; stop, varış ve ViewModel kapanışında durur.

### P1 — Yönetişim

- Kök dizine `ANAYASA.md` eklendi.
- README eklendi.
- Anayasa/doğruluk kontrolü CI quality gate’e bağlanır.

## Doğrulanmış mevcut zincirler

- Rota: Valhalla → OSRM → offline route cache.
- Arama: Nominatim → Photon → local/cache.
- Harita stili: primary → backup → güvenli boş durum.
- Sabit kamera: Overpass primary → mirrors → persistent last-known-good → güvenli boş/hata.
- Trafik: TomTom live → kısa süreli cache → doğrulanmamış trafik durumunda temel ETA.
- Live share: gerçek backend, süreli token, revoke, idempotent PUT/DELETE için bounded retry.
- CI: lint, unit, emulator instrumentation, APK build.
- Main release: SHA-256 + GitHub Release APK.

## Bilinçli ürün sınırları

Aşağıdaki noktalar sahte biçimde “tamamlandı” sayılmaz:

1. **Bağımsız tam offline routing:** cache’te olmayan yepyeni rotayı internet olmadan hesaplayan graph engine yok.
2. **İkinci bağımsız live-traffic provider:** şu an doğrulanmış live trafik kaynağı TomTom. Uydurma ikinci kaynak eklenmedi.
3. **Live-share kalıcı sunucu deposu:** backend session’ları process memory’dedir; backend restart aktif paylaşımı düşürebilir. Kalıcı DB eklemek ek altyapı/maliyet kararı gerektirir.
4. **GitHub main branch koruması:** denetim başlangıcında main protected değildi. Platform izinleri elverdiğinde required checks ile branch protection etkinleştirilmelidir.

Bu sınırlar kullanıcıya yanlış iddia yapılmaması için üretim dokümanında tutulur.

## Çıkış kriteri

Bu branch yalnızca PR kalite kapıları yeşil olduğunda main’e alınmalı; main Android APK workflow’u başarılı olduğunda `latest` Release içindeki `app-debug.apk` yeni commit ile güncellenmiş sayılmalıdır.

# LANU-Harita-Uygulamas- — KANITLI TEKNİK DENETİM

**Tarih:** 15.10.2026  
**Kapsam:** GitHub dosya ağacı, seçili kaynak kodları, CI logları, Releases. Bu belge gerçek cihaz uçtan uca testi yerine geçmez.  
**Dosya sayısı (denetim anında):** 230  
**CI gözlemi:** 08.10.2026 Android APK + production live share smoke başarılı; v1.1.22 Release APK + sha256 mevcut.  
**Anayasa:** [PROJE_ANAYASASI_VE_DUZELTILECEKLER.md](https://github.com/kanunal99-jpg/LANU-Harita-Uygulamas-/blob/main/PROJE_ANAYASASI_VE_DUZELTILECEKLER.md)

## Kanıtlarla Düzeltilmesi Gerekenler

### 1. Gerçek rota provider fallback'i bulunuyor — P2 (DOĞRULANMIŞ İYİ UYGULAMA)
- **Dosya/kanıt:** [app/src/main/java/com/example/haritalar/data/repository/RouteProviderFallback.kt#L9-L21](https://github.com/kanunal99-jpg/LANU-Harita-Uygulamas-/blob/main/app/src/main/java/com/example/haritalar/data/repository/RouteProviderFallback.kt#L9-L21); [CI koşusu](https://github.com/kanunal99-jpg/LANU-Harita-Uygulamas-/actions/runs/37775863745)
- **Gözlem:** Ana sağlayıcı rota vermediğinde/başarısız olduğunda alternatif çağrılıyor, RouteSelectionPolicy filtreliyor.
- **Somut örnek / etkisi:** Ana servis HTTP 500 verdiğinde alternatif geçerli rota gösterilmeli.
- **Düzeltme öncesi kabul kriteri:** Mevcut mekanizmayı bozma; null/boş/bozuk rota, provider timeouts ve alternatifin de boş kalması test edilsin.
- **Durum:** ARAŞTIRILDI — henüz kaynak kod düzeltmesi veya cihaz testi yapılmadı.

### 2. Navigasyon gerçek dünyada ayrıca doğrulanmalı — P2 (GERÇEK CİHAZ TEST GEREK)
- **Dosya/kanıt:** [app/src/androidTest/java/com/example/haritalar/navigation/NavigationScenarioInstrumentationTest.kt](https://github.com/kanunal99-jpg/LANU-Harita-Uygulamas-/blob/main/app/src/androidTest/java/com/example/haritalar/navigation/NavigationScenarioInstrumentationTest.kt); [CI koşusu](https://github.com/kanunal99-jpg/LANU-Harita-Uygulamas-/actions/runs/37775863745)
- **Gözlem:** Enstrümantasyon testleri var; gerçek GPS kayması, tünel, izin reddi, offline rota ve şerit yönlendirme gerçek cihazda kanıtlanmadı.
- **Somut örnek / etkisi:** İstanbul-Kocaeli kesintili internet rotası, önbellek yaşı ve sapma yeniden rota testi.
- **Düzeltme öncesi kabul kriteri:** Cihaz test matrisi, sürüşte dikkat dağınıklığı ölçümü, doğrulanmamış hız/radar bilgisine doğru etiket.
- **Durum:** ARAŞTIRILDI — henüz kaynak kod düzeltmesi veya cihaz testi yapılmadı.

### 3. Canlı paylaşım smoke yalnızca kendi kapsamını kanıtlar — P2 (ÜRETİM KANITI SINIRI)
- **Dosya/kanıt:** [actions/runs/37775863745](https://github.com/kanunal99-jpg/LANU-Harita-Uygulamas-/actions/runs/37775863745); [CI koşusu](https://github.com/kanunal99-jpg/LANU-Harita-Uygulamas-/actions/runs/37775863745)
- **Gözlem:** Production Live Share Smoke başarıyla tamamlandı; tüm navigasyon ve veri sağlayıcılarını kapsamaz.
- **Somut örnek / etkisi:** Canlı link erişimi başarılı ama rota veya hava verisi geçersiz olabilir.
- **Düzeltme öncesi kabul kriteri:** Feature bazında bağımsız smoke, merkezi izleme ve fail-closed.
- **Durum:** ARAŞTIRILDI — henüz kaynak kod düzeltmesi veya cihaz testi yapılmadı.

### 4. Mevcut kapsamlı ANAYASA.md korunmalı — P2 (ANAYASA UYUMLULUĞU)
- **Dosya/kanıt:** [ANAYASA.md#L9-L20](https://github.com/kanunal99-jpg/LANU-Harita-Uygulamas-/blob/main/ANAYASA.md#L9-L20); [CI koşusu](https://github.com/kanunal99-jpg/LANU-Harita-Uygulamas-/actions/runs/37775863745)
- **Gözlem:** Doğru olmayan hız/rota uydurmama ve yedek servis zinciri kuralları zaten tanımlı.
- **Somut örnek / etkisi:** Genel anayasa mevcut özel sürüş güvenliği kurallarını gevşetmemeli.
- **Düzeltme öncesi kabul kriteri:** Bu bulgu belgesi bağlayıcı mevcut ANAYASA.md'nin tamamlayıcısıdır; kapıların doğrulama otomasyonu sürdürülür.
- **Durum:** ARAŞTIRILDI — henüz kaynak kod düzeltmesi veya cihaz testi yapılmadı.

## 4 Fazlı Geliştirme ve Doğrulama Kapıları

1. **Faz 1 — Araştır:** Tam modül/dosya ve bağımlılık envanteri, doğrulanmış/farklılaşmış hatalar, etki analizi. **Bu rapor ilk kaynak/CI kanıtıdır; bütün işlevlerin çalıştığını iddia etmez.**
2. **Faz 2 — Düzelt:** Önce P0/P1, en küçük güvenli kod değişimi, gizli anahtar/ücret/erişim izinlerini güvenli yöntemlerle ele al.
3. **Faz 3 — Uygulama ve test:** Unit, integrasyon, UI, offline, yetki reddi, yanlış veri, servis kesintisi, gerçek fallback, CI + Android gerçek cihaz varsa.
4. **Faz 4 — Yayın:** CI yeşil → Android ise SHA-256'lı indirilebilir Release APK → kullanıcı akışları smoke → kanıt linkleri.

## Kapatma Kriteri
Her bulgu için issue veya değişiklik ID'si, önce/sonra kanıtı, ilgili satır, test raporu, CI koşu bağlantısı, release/dağıtım bağlantısı, sonuç tarihi zorunludur. **Yalnızca belgenin eklenmiş olması sorun giderildi anlamına gelmez.**

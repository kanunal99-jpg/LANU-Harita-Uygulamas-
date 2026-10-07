# LANU 1.1.5 — POI, Trafik Işığı ve GPS Süreklilik Düzeltmesi

## Fotoğraftaki kök nedenler

### 1. Seçili POI kategorisi ile haritadaki işaretler uyuşmuyordu
Benzinlik seçildiğinde eski kategoriye ait POI listesi ekranda kalabiliyordu. Yeni istek GPS nedeniyle başlayamazsa eski liste hiç temizlenmiyordu.

Düzeltme:
- kategori değişiminde eski POI listesi anında temizlenir,
- önceki POI ağ işi iptal edilir,
- her istek generation ile korunur; eski cevap yeni kategorinin üstüne yazamaz,
- bulunan POI'ye dokunmak artık hedef seçer.

### 2. POI araması gereksiz şekilde navigasyon GPS'ine bağlıydı
Navigasyon için taze ve hassas GPS şartı korunur. POI ise düşük riskli keşif özelliğidir.

Yeni merkez zinciri:
- harita FREE modundaysa görünür viewport merkezi,
- takip modundaysa taze GPS,
- taze GPS yoksa en fazla 15 dakikalık makul last-known konum,
- son fallback olarak görünür viewport merkezi.

Harita kullanıcı tarafından taşınırsa ve merkez yaklaşık 2,5 km değişirse seçili POI kategorisi debounced olarak yenilenir.

### 3. Trafik ışıkları uzak zoom'da üst üste kalıyordu
Önceden yüksek zoom'da yüklenen ışıklar düşük zoom'a çıkıldığında state'ten silinmiyordu. Ayrıca yeni viewport sonucu eski viewport listesine merge edildiğinden eski bölgeler birikiyordu.

Düzeltme:
- zoom 14 altına düşer düşmez trafik ışığı listesi temizlenir,
- yalnızca güncel viewport içindeki sonuçlar gösterilir,
- yeni viewport eski viewport listesiyle merge edilmez,
- katman kapatıldığında veriler ve seçili detay temizlenir.

### 4. GPS noktası telefon sabitken griye dönüyordu
IDLE GPS isteğinde 8 m minimum hareket filtresi vardı. Telefon hareket etmeyince yeni callback gelmeyebiliyor ve 30 saniye sonra son fix eski kabul ediliyordu.

Düzeltme:
- IDLE minimum hareket eşiği 0 m,
- uygulama açılışında explicit high-accuracy current fix ister,
- 15 saniyelik freshness watchdog tüm oturum boyunca çalışır,
- provider durursa otomatik yeni fix ister,
- navigasyonun sıkı tazelik/doğruluk güvenlik kriteri değiştirilmez.

## Testler
- PoiSearchCenterPolicyTest
- TrafficSignalViewportPolicyTest
- LocationSamplingPolicyTest
- mevcut POI/traffic signal/GPS/navigation testleri
- lint + JVM unit + Android instrumentation/smoke + APK + SHA-256

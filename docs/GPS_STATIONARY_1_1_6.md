# LANU 1.1.6 — Sabit Cihaz GPS Tazeliği

## Neden gerekliydi?

1.1.5 POI ve trafik ışığı viewport sorunlarını düzeltti. Ancak gerçek cihaz sabit dururken GPS noktası bir süre sonra griye dönebiliyordu.

Kök neden:
- IDLE konum isteğinde 8 metre minimum hareket koşulu vardı.
- Telefon hareket etmeyince Fused Location callback'i uzun süre yenilenmeyebiliyordu.
- Navigasyon güvenlik politikası 30 saniyeden eski fix'i stale kabul ettiği için doğru konum ekranda olsa bile canlı navigasyon uygunluğu kayboluyordu.
- Fresh-fix watchdog ilk başarılı konumdan sonra tamamen kapatılıyordu; provider daha sonra durursa otomatik kurtarma yoktu.

## Düzeltme

- IDLE minimum hareket eşiği **8 m → 0 m**.
- IDLE update interval 4 saniye olarak korunur; gereksiz 1 saniyelik sürüş örneklemesine geçilmez.
- Fresh-fix watchdog ilk başarılı fix'ten sonra kapanmaz.
- Watchdog her 15 saniyede GPS tazeliğini denetler.
- Fix missing/stale/inaccurate ise yüksek doğruluklu current fix yeniden istenir.
- Overpass/POI veya rota verisiyle GPS koordinatı uydurulmaz.
- Navigasyonun 30 saniye / 100 m tazelik-doğruluk güvenlik kriteri gevşetilmez.
- Trafik ışığı katmanı kapatıldığında veya düşük zoom'a çıkıldığında seçili eski trafik ışığı detayı da temizlenir.

## Test

- LocationSamplingPolicyTest sabit cihazda 0 m minimum hareket eşiğini zorunlu tutar.
- Mevcut GPS readiness, POI viewport, traffic signal viewport, lint, unit, Android instrumentation/smoke ve APK release kapıları korunur.

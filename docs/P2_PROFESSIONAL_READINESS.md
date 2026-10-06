# LANU Harita — P2 Professional Readiness

## Adaptif GPS sampling

Konum yöneticisi deterministik bir sampling politikası kullanır:

- Moving: 1 sn hedef aralık, 750 ms minimum aralık, 2 m minimum displacement.
- Idle/slow: 4 sn hedef aralık, 2.5 sn minimum aralık, 8 m minimum displacement.
- Hysteresis: moving moda 8 km/h ve üzerinde geçilir; 3 km/h ve altında idle moda dönülür.
- Politika fused-location ana yolunda ve Android sistem GPS fallback yolunda aynıdır.
- Mod değişince provider yeniden yapılandırılır; sahte konum üretmez.
- Tekrar start çağrısında eski callback’ler temizlenerek duplicate location subscription riski azaltılır.

## Otomatik kabul

Aşağıdakiler merge öncesi kapıdır:

- LocationSamplingPolicy unit testleri
- Android lint
- Android unit test
- emulator instrumentation smoke
- debug APK build
- Anayasa quality gate

Main sonrasında APK workflow ayrıca SHA-256 ve Release asset’ini doğrular.

## Saha kabul sınırı

Otomatik CI; gerçek cihaz üzerinde uzun süreli batarya tüketimi, termal davranış, OEM background kısıtları ve gerçek yol GPS koşullarını ölçemez. Bu testler fiziksel cihaz saha kabulüdür ve emulator sonucu ile “tamamlandı” sayılmaz.

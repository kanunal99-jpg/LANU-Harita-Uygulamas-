# LANU 1.1.13 — Rota Yol Özelliği Zekâsı

## Amaç

LANU'nun sürüş güvenliği katmanını yalnızca trafik, hava ve sabit kamerayla sınırlamamak; kaynakta bulunan gerçek yol özelliklerini seçili rota üzerinde sürüş öncesinde ve sürüş sırasında kullanılabilir hâle getirmek.

## Desteklenen kaynak-backed yol özellikleri

OpenStreetMap / Overpass üzerinden:

- hız tümseği ve trafik yavaşlatma noktaları,
- okul alanları,
- hemzemin demiryolu geçitleri,
- OSM `hazard=*` yol tehlikesi kayıtları.

Kaynakta olmayan bir özellik oluşturulmaz.

## Veri zinciri

**Overpass primary → Overpass mirror 1 → Overpass mirror 2 → aynı rota için 24 saatlik last-known-good cache → güvenli doğrulanamadı**

Cache yalnızca aynı rota imzasında kullanılır. Cache kullanımı LANU Brief'te açıkça “kısmi / önbellek” olarak gösterilir.

## Rota koridoru

- Rota en fazla 12 sorgu noktasına mesafe bazlı örneklenir.
- Her örnek çevresinde yaklaşık 1,2 km kaynak sorgusu yapılır.
- Dönen öğeler gerçek rota koridoruna yeniden filtrelenir.
- Varsayılan rota koridoru yaklaşık 220 m'dir.
- Okul alanlarında yaklaşık 300 m tolerans kullanılır.
- Paralel yoldaki ve araç ilerleyişinin gerisindeki özellikler canlı uyarıdan çıkarılır.

## Canlı sürüş uyarısı

Yaklaşma eşiği hıza göre:

- düşük hız: yaklaşık 650 m,
- 60+ km/h: yaklaşık 1 km,
- 90+ km/h: yaklaşık 1,5 km,
- okul ve tümsek uyarıları en fazla 1 km.

Aynı öğe için ilk yaklaşma uyarısından sonra 300 m içinde ikinci kısa uyarı verilebilir. Ses + kısa titreşim kullanılır.

## LANU Brief

Rota seçim ekranında yol özellikleri özetlenir:

- toplam öğe sayısı,
- tümsek/yavaşlatma sayısı,
- okul sayısı,
- hemzemin geçit sayısı,
- yol tehlikesi sayısı,
- kaynak durumu: doğrulanmış / cache / doğrulanamadı.

## Road Intelligence

Sürüş sırasında yaklaşan yol özelliği P1 güvenlik olayı olarak mevcut Road Intelligence öncelik motoruna girer. Yol kapanışı gibi P0 olaylar yine daha yüksek önceliklidir.

## Radar 1.1.12 regresyon koruması

Bu sürüm 1.1.12'nin:

- rota öncesi tam radar taraması,
- kamera rota kilometresi,
- yol/yer bağlamı,
- son 5 km içinde 500 m ses kademesi

özelliklerini değiştirmez ve kalite kapısında korunmasını doğrular.

## Testler

- Overpass sorgu üretimi,
- node + way/center parser,
- rota örnekleme sınırı,
- paralel/geride öğe filtresi,
- hız-adaptif yaklaşma eşiği,
- LANU Brief provenance,
- Road Intelligence P1 event,
- mevcut radar, hava, trafik, GPS ve Android smoke testleri.

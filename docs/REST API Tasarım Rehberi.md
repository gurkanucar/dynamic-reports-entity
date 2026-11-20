# REST API Tasarım Rehberi

Nov 20, 2026 · @User

## 1. Amaç ve temel ilkeler

Bu rehber, hangi dilde veya framework'te yazılırsa yazılsın, bir HTTP/JSON API'sinin nasıl tasarlanacağını tanımlar. Amaç, aynı ekipten çıkan her API'nin aynı şekilde davranması ve istemcinin bir kez öğrendiğini her yerde kullanabilmesidir. Örneklerde alandan bağımsız bir sipariş sistemi (`orders`, `customers`, `products`) kullanılır.

Rehber yedi ilkeye dayanır:

1. **Önce sözleşme, sonra kod.** API önce bir OpenAPI dosyasında tanımlanır; kod, testler ve istemci bu dosyadan türetilir. Sözleşmede olmayan bir davranış yoktur.
2. **HTTP'nin kendi anlamını kullan.** Başarı ve hata status koduyla, önbellek ETag ile, tekrar güvenliği metotla ifade edilir. Bunlar body içinde yeniden icat edilmez.
3. **Tutarlılık zekâdan önemlidir.** Aynı problem her endpoint'te aynı şekilde çözülür: aynı zarf, aynı hata formatı, aynı sayfalama, aynı isimlendirme.
4. **Makine için kod, insan için mesaj.** Her hata ve uyarının sabit bir `code` alanı vardır. İstemci mantığı bu koda dayanır; metin sadece gösterim içindir.
5. **Sınırsız hiçbir şey yoktur.** Her liste sayfalanır ya da yapısal bir üst sınıra sahiptir; her dosyanın, her toplu işlemin bir limiti vardır.
6. **Sızdırma.** Hatalar iç yapıyı (stack trace, SQL, başka kiracının verisinin varlığı) dışarı vermez.
7. **Geriye uyumluluk varsayılandır.** Yayımlanmış bir sözleşme kırılmaz; yalnızca genişletilir.

**Bu rehberdeki kelimeler:** *Zorunlu* yazan kurallara istisnasız uyulur. *Önerilir* yazan kurallardan ancak gerekçesi yazılı olarak sapılabilir.

## 2. URL ve kaynak tasarımı

URL bir **kaynağı** (isim) gösterir; ne yapılacağını HTTP metodu söyler.

| Kural | Doğru | Yanlış |
| --- | --- | --- |
| Çoğul isim | `/orders` | `/order`, `/getOrders` |
| kebab-case yol | `/order-items` | `/orderItems`, `/order_items` |
| camelCase JSON alanı | `createdAt`, `totalAmount` | `created_at`, `TotalAmount` |
| snake\_case enum değeri | `"status": "in_transit"` | `"InTransit"`, `"IN-TRANSIT"` |
| Fiil URL'de yok (CRUD için) | `DELETE /orders/42` | `POST /orders/42/delete` |
| En fazla bir seviye iç içe | `/orders/42/items` | `/customers/7/orders/42/items/3` |
| Sona eğik çizgi yok | `/orders` | `/orders/` |

**İç içe yollar:** Alt kaynak listelenirken ve oluşturulurken ebeveyninin altında durur (`GET/POST /orders/42/items`). Oluştuktan sonra kendi kök yoluyla erişilir (`GET/PATCH/DELETE /order-items/9`). Böylece URL'ler kısa kalır ve tekil kaynağa ulaşmak için ebeveynin ID'sini bilmek gerekmez.

**Durum geçişleri (action endpoint'leri):** Bir kaynağın bir durum makinesi varsa (taslak → onayda → onaylandı), her geçiş kendi fiil alt yoluyla ve `POST` ile çağrılır:

```http
POST /orders/42/confirm
POST /orders/42/cancel
POST /orders/42/ship
```

`PATCH /orders/42 { "status": "shipped" }` kullanılmaz. Her geçişin kendine ait yetki kuralı, ön koşulu, isteğe bağlı gövdesi (iptal gerekçesi gibi) ve yan etkisi (mail, stok, muhasebe kaydı) vardır. Bunları tek bir `status` alanının arkasına saklamak hem yetkilendirmeyi hem de sözleşmeyi bulanıklaştırır.

**Kaynak olmayan işlemler:** Hesaplama veya arama gibi bir kaynağa bağlanmayan işlemler fiil isimli bir kök yol alır ve `POST` kullanır: `POST /price-quotes`, `POST /search`. Mümkünse işlem bir kaynak olarak modellenir: "fiyat hesapla" yerine "fiyat teklifi oluştur".

**Kiracı (tenant) bilgisi URL'de yer almaz.** Kullanıcının hangi şirkete ait olduğu token'dan okunur. `/companies/5/orders` gibi bir yapı, başka bir şirketin ID'sini deneme kapısı açar.

## 3. HTTP metotları

Her metodun anlamı sabittir; istemciler, proxy'ler ve retry mekanizmaları bu anlama göre davranır.

| Metot | Kullanım | Başarılı cevap | Idempotent |
| --- | --- | --- | --- |
| GET | Okuma; yan etkisi olmaz | 200, 304 | Evet |
| POST | Oluşturma, action, asenkron iş başlatma | 201, 200, 202 | Hayır (`Idempotency-Key` ile güvenli hale gelir) |
| PUT | Kaynağı tamamen değiştirme veya upsert | 200, 201 | Evet |
| PATCH | Kısmi güncelleme | 200 | Hayır |
| DELETE | Silme | 204 | Evet |

**PUT ile PATCH arasındaki fark:** PUT ile gönderilmeyen alan varsayılan değerine döner; PATCH ile gönderilmeyen alan olduğu gibi kalır. PUT, istemcinin kaynağın tamamını bildiği durumlar için uygundur (kullanıcı ayarları, bir N-N ilişkisinin pivot kaydı). Genel güncellemeler için PATCH kullanılır.

**PATCH formatı: JSON Merge Patch (RFC 7396).** İçerik tipi `application/merge-patch+json` olur. Üç kuralı vardır:

- Gönderilmeyen alan değişmez.
- `null` gönderilen alan temizlenir.
- Boş gövde (`{}`) 422 döner; sözleşmede `minProperties: 1` ile belirtilir.

```http
PATCH /orders/42
Content-Type: application/merge-patch+json
If-Match: "7"

{ "note": null, "shippingAddress": { "line2": "Daire 4" } }
```

Bu istek `note` alanını temizler, adresin sadece `line2` alanını günceller ve diğer her şeye dokunmaz.

**DELETE idempotent davranır.** Önerilen davranış, zaten silinmiş bir kaydı tekrar silmeye çalışan isteğe 404 dönmektir; ama ikinci çağrı hiçbir şeyi bozmaz. "Oturumu kapat" veya "bildirimi okundu yap" gibi işlemler ise her zaman 204 döner; işlem zaten yapılmış olsa bile.

**GET hiçbir şeyi değiştirmez.** Bir GET isteği kayıt oluşturmaz, sayaç artırmaz, durum değiştirmez. Tarayıcılar, crawler'lar ve önbellekler GET'i istedikleri kadar tekrarlayabilir.

## 4. Status kodları

Status kodu hatanın **kategorisini** söyler; hatanın kendisini body'deki `code` alanı söyler. HTTP'de "stok yetersiz" ya da "kredi limiti aşıldı" diye bir kod yoktur ve olmasına gerek yoktur:

```text
409 Conflict  +  "code": "INSUFFICIENT_STOCK"
 ↑ kategori        ↑ asıl hata
```

### Kullanılan kodlar

| Kod | Ne zaman |
| --- | --- |
| 200 OK | Başarılı okuma, güncelleme veya action (gövdeyle) |
| 201 Created | Kayıt oluşturuldu; `Location` header'ı ve oluşturulan kayıt döner |
| 202 Accepted | İş kuyruğa alındı, henüz bitmedi; `Location` ile takip edilir |
| 204 No Content | Başarılı, dönecek gövde yok |
| 302 Found | Dosya indirme; imzalı depolama URL'ine yönlendirme |
| 304 Not Modified | `If-None-Match` eşleşti, veri değişmedi |
| 400 Bad Request | İstek ayrıştırılamadı: bozuk JSON, yanlış tipte query parametresi, sınır dışı `size`, bilinmeyen sıralama alanı |
| 401 Unauthorized | Kimlik yok, geçersiz ya da süresi dolmuş |
| 402 Payment Required | Plan veya ücret limiti (isteğe bağlı; 403 de seçilebilir, yeter ki tutarlı olsun) |
| 403 Forbidden | Kimlik doğru ama bu işlem için yetki yok |
| 404 Not Found | Kaynak yok **veya kullanıcının görmeye yetkisi olmayan bir kiracıya ait** |
| 405 Method Not Allowed | Yol var ama bu metodu desteklemiyor; `Allow` header'ı döner |
| 409 Conflict | Veri doğru ama kaynağın şu anki durumu işleme izin vermiyor |
| 410 Gone | Kaynak vardı ama artık kullanılamaz (süresi dolmuş link, davet, export) |
| 412 Precondition Failed | `If-Match` eşleşmedi; kayıt siz okuduktan sonra değişmiş |
| 413 / 415 | Dosya veya gövde çok büyük / desteklenmeyen içerik tipi |
| 422 Unprocessable Content | JSON geçerli ama doğrulamaya veya iş kuralına uymuyor |
| 428 Precondition Required | Bu işlem için `If-Match` zorunlu ama gönderilmedi |
| 429 Too Many Requests | Rate limit veya kota; `Retry-After` header'ı döner |
| 500 Internal Server Error | Beklenmeyen hata; sadece `traceId` döner |
| 502 / 503 / 504 | Bağımlı servis hatası / geçici olarak kullanılamaz (`Retry-After` ile) / zaman aşımı |

### Business hataları için karar ağacı

Bir iş kuralı ihlali için kod seçerken soruları sırayla sor; ilk "evet" cevabı kodu belirler:

&#91;embedded content: business hatası için status kodu seçimi · 7 soru\]

1. Kimlik belli değil mi? → **401**
2. Bu kullanıcının bu işi yapmaya hakkı yok mu? → **403** (`SELF_APPROVAL_FORBIDDEN`, `EMAIL_NOT_VERIFIED`)
3. Kaynak yok ya da kullanıcı onu göremiyor mu? → **404**
4. Gönderilen veri kurala aykırı mı? Aynı istek başka bir veriyle gelse geçer miydi? → **422** (`PRODUCT_DISCONTINUED`, `DELIVERY_DATE_IN_PAST`)
5. Veri doğru ama kaynağın **şu anki durumu** izin vermiyor mu? Aynı istek başka bir zamanda geçebilir miydi? → **409** (`ORDER_ALREADY_SHIPPED`, `INSUFFICIENT_STOCK`)
6. Kota veya hız sınırına mı takıldı? → **429**
7. Ücretli plan sınırına mı takıldı? → **402** veya **403**

Pratikte business hatalarının neredeyse tamamı 403, 409 ve 422 kategorilerine düşer.

### Sık karıştırılan çiftler

**400 ile 422:** Gövde veya parametre ayrıştırılamıyorsa (bozuk JSON, sayı beklenen yerde metin, `size=500`) 400 döner. Ayrıştırıldı ama içerik kurala uymuyorsa (zorunlu alan eksik, tutar negatif, tarih geçmişte) 422 döner. Ayrımın faydası: 400 genelde istemci kodunda bir hatadır, 422 ise kullanıcıya form üzerinde gösterilecek bir durumdur.

**409 ile 422:** Soru "veri mi yanlış, zamanlama mı?" Siparişi iptal etmek isteyen kullanıcının isteği doğrudur ama sipariş kargoya verilmişse 409 alır. Aynı kullanıcı geçmiş bir teslim tarihi gönderirse, zamanı ne olursa olsun 422 alır.

**401 ile 403:** 401 "seni tanımıyorum", 403 "seni tanıyorum ama izin vermiyorum" demektir. 401 alan istemci token yeniler veya giriş ekranına yönlendirir; 403 alan istemci tekrar denemez.

**403 ile 404:** Kullanıcının bir kaynağın **varlığını bilmemesi** gerekiyorsa (başka kiracının verisi) 404 döner. Varlığını görebiliyor ama üzerinde işlem yapamiyorsa 403 döner.

### Yasaklar

- **Uydurma status kodu kullanılmaz** (460, 499, 7xx). HTTP sadece 100–599 aralığını tanımlar. Bilinmeyen kodlar bazı istemcilerde exception fırlatır, bazılarında başarılı sayılır. Gateway'ler bu kodları 502'ye çevirebilir ve monitoring hata oranını yanlış hesaplar.
- **`200` ile birlikte `{ "success": false }` dönülmez.** Önbellek, retry, log ve kod üreticilerin hepsi bu isteği başarılı sanır.
- **Her şey için 400 dönülmez.** Yanlış sayılmaz ama istemciye hiç bilgi vermez.

## 5. Başarılı response yapısı

Başarılı her cevap aynı zarfı kullanır: `{ "data": ..., "meta": ... }`. Hatalar bu zarfı kullanmaz; RFC 9457 Problem formatında döner (bölüm 6). Başarıyı veya hatayı body değil, status kodu söyler.

| Alan | Zorunlu | İçerik |
| --- | --- | --- |
| `data` | Evet | Tekil kayıt (nesne), liste (dizi) ya da işlem sonucu |
| `meta` | Hayır | Sayfalama bilgisi, toplamlar, rapor üretim zamanı, `notices` |
| `meta.notices` | Hayır | İstemcinin kendi başına bilemeyeceği uyarı ve bilgi mesajları |

### Tekil kayıt

```http
GET /orders/42
```

```json
{
  "data": {
    "id": 42,
    "status": "confirmed",
    "total": { "amount": "1250.50", "currency": "TRY" },
    "createdAt": "2026-11-20T09:30:00Z"
  }
}
```

### Liste

```json
{
  "data": [ { "id": 42, "...": "..." }, { "id": 41, "...": "..." } ],
  "meta": { "page": 1, "size": 20, "totalItems": 137, "totalPages": 7 }
}
```

### Boş liste

Boş liste bir hata değildir: **200** döner, **404 dönmez**. "Kayıt bulunamadı" yazısını istemci `data.length === 0` kontrolüyle kendisi gösterir.

```json
{ "data": [], "meta": { "page": 1, "size": 20, "totalItems": 0, "totalPages": 0 } }
```

Filtre yüzünden mi boş, hiç kayıt olmadığı için mi boş ayrımı arayüzde önemliyse ("Henüz siparişiniz yok" ile "Bu filtreye uyan sipariş yok" farkı), `meta` alanına `"filtered": true` eklenebilir. Tekil bir kayıt (`GET /orders/42`) bulunamadığında ise 404 döner; bu gerçek bir hatadır.

### Backend'den mesaj: `meta.notices`

"Kayıt eklendi" gibi her başarıda gösterilen mesajlar UI metnidir ve istemciye aittir. İstemci hangi işlemi yaptığını bilir; metni kendi i18n dosyasından üretir. Backend'in her cevaba mesaj koyması, aynı metnin hem sunucuda hem istemcide yazılması ve senkron tutulması demektir.

Backend'den gelen mesaj sadece istemcinin **kendi başına bilemeyeceği** bir şey olduğunda anlamlıdır: bir uyarı, bir yan etki, kısmen gerçekleşen bir şey. Bunun yeri `meta.notices` alanıdır:

```http
POST /orders/42/confirm
```

```json
{
  "data": { "id": 42, "status": "confirmed" },
  "meta": {
    "notices": [
      {
        "code": "STOCK_LOW",
        "level": "warning",
        "message": "Ürünün stokta 2 adedi kaldı.",
        "field": "items[0].productId"
      },
      {
        "code": "INVOICE_EMAIL_DEFERRED",
        "level": "info",
        "message": "Fatura e-postası birkaç dakika içinde gönderilecek."
      }
    ]
  }
}
```

| Alan | Açıklama |
| --- | --- |
| `code` | Zorunlu, sabit. İstemci i18n anahtarı olarak kullanır. |
| `level` | `info`, `warning` veya `success`. Hata için notice kullanılmaz; hata 4xx/5xx döner. |
| `message` | `Accept-Language` header'ına göre çevrilmiş varsayılan metin. İstemci kendi çevirisini bulamazsa bunu gösterir. |
| `field` | İsteğe bağlı; mesajın bağlı olduğu alanın JSON yolu. |

### Kısmi başarı (toplu işlemler)

Toplu işlemde her kalem ayrı değerlendirilir. HTTP cevabı **200** olur ve her kalemin sonucu ayrı ayrı döner. 207 Multi-Status, WebDAV'a ait olduğu için kullanılmaz.

```json
{
  "data": {
    "results": [
      { "id": 41, "success": true },
      { "id": 42, "success": false,
        "error": { "status": 409, "code": "ORDER_ALREADY_SHIPPED", "title": "Sipariş kargoya verilmiş" } }
    ],
    "summary": { "succeeded": 1, "failed": 1 }
  }
}
```

Buradaki `success` alanı bir istisnadır: tek bir HTTP cevabı birden fazla işlemi taşıdığı için her kalemin sonucunu ayrıca belirtmek gerekir.

### Gövdesiz cevaplar

- **204:** silme, "okundu işaretle", çıkış gibi dönecek verisi olmayan işlemler.
- **Güncelleme ve action'lar** güncel kaydı `data` içinde döner (200). Böylece istemci ek bir GET yapmadan ekranı güncelleyebilir.

### Neden `{ success, message, data }` değil?

Yaygın bir kalıp olan `{ "success": true, "message": "...", "data": ... }` bu rehberde kullanılmaz:

- `success` alanı status kodunun aynısını tekrar eder ve iki kaynak birbiriyle çelişebilir (`200` + `success: false`).
- Zorunlu bir `message` alanı, her işlem için metin üretmeye zorlar; bu metinlerin çoğu istemcinin zaten bildiği şeylerdir.
- Hata ve başarı aynı şekli taşıdığında kod üreticiler doğru tipler üretemez.

### Neden tekil kayıtlar da zarflı?

Tekil kayıt çıplak (zarfsız) dönerse, ileride o cevaba `notices` veya başka bir meta bilgi eklemek kırıcı bir değişiklik olur. Zarf baştan konursa API geriye uyumlu şekilde büyüyebilir. Sunucu tarafında tek bir response helper, istemci tarafında da tek bir `unwrap` fonksiyonu yeterlidir.

## 6. Hata formatı ve hata kataloğu

Tüm hatalar RFC 9457 (Problem Details) formatında ve `application/problem+json` içerik tipiyle döner. Hatalar `{ data, meta }` zarfını kullanmaz.

```http
POST /orders
Content-Type: application/json

{ "customerId": 7, "items": [ { "productId": 15, "quantity": 0 } ], "deliveryDate": "2025-01-01" }
```

```http
HTTP/1.1 422 Unprocessable Content
Content-Type: application/problem+json

{
  "type": "https://errors.example.com/validation",
  "title": "Doğrulama hatası",
  "status": 422,
  "code": "VALIDATION_FAILED",
  "detail": "2 alan geçersiz.",
  "instance": "/orders",
  "traceId": "01JD3K9Z6Q2W8",
  "errors": [
    { "field": "items[0].quantity", "code": "MUST_BE_POSITIVE", "message": "Adet sıfırdan büyük olmalı." },
    { "field": "deliveryDate", "code": "DATE_IN_PAST", "message": "Teslim tarihi geçmişte olamaz." }
  ]
}
```

| Alan | Zorunlu | Açıklama |
| --- | --- | --- |
| `type` | Evet | Hata türünü tanımlayan URI. Bir doküman sayfasına işaret etmesi önerilir. |
| `title` | Evet | Hata türünün kısa, sabit başlığı. |
| `status` | Evet | HTTP status kodunun aynısı. |
| `code` | Evet | Makinenin okuyacağı sabit kod. İstemci mantığı buna dayanır. |
| `detail` | Hayır | Bu olaya özel açıklama. |
| `instance` | Hayır | Hatanın oluştuğu istek yolu. |
| `traceId` | Evet | Loglarda aynı isteği bulmak için. `X-Request-Id` header'ı ile aynıdır. |
| `errors[]` | Hayır | Alan bazlı hatalar: `field` (JSON yolu), `code`, `message`. |

**Bir hata, dört ayrı örnek:**

```json
{ "type": "https://errors.example.com/conflict", "title": "Sipariş kargoya verilmiş",
  "status": 409, "code": "ORDER_ALREADY_SHIPPED", "traceId": "01JD3M..." }
```

```json
{ "type": "https://errors.example.com/forbidden", "title": "Bu işlem için yetkiniz yok",
  "status": 403, "code": "INSUFFICIENT_ROLE", "detail": "İade onayı için 'manager' rolü gerekir.", "traceId": "01JD3N..." }
```

```json
{ "type": "https://errors.example.com/precondition-failed", "title": "Kayıt değişmiş",
  "status": 412, "code": "VERSION_MISMATCH", "detail": "Sayfayı yenileyip tekrar deneyin.", "traceId": "01JD3P..." }
```

```json
{ "type": "https://errors.example.com/internal", "title": "Beklenmeyen bir hata oluştu",
  "status": 500, "code": "INTERNAL_ERROR", "traceId": "01JD3Q..." }
```

**500 hataları hiçbir zaman** stack trace, SQL, dosya yolu, sınıf adı veya bağımlı servisin ham hata mesajını içermez. Ayrıntı loglarda `traceId` ile bulunur.

### Hata kodu kataloğu

Hata kodları dağınık sabitler olarak değil, tek bir katalog dosyasında tutulur (örneğin `errors.yaml`). Sunucu tarafındaki enum'lar ve istemcinin i18n anahtarları bu dosyadan üretilir. Böylece "bu hata hangi status'la dönüyordu?" sorusunun tek bir cevabı olur.

| code | status | Varsayılan mesaj | İstemci ne yapar |
| --- | --- | --- | --- |
| `VALIDATION_FAILED` | 422 | Doğrulama hatası | `errors[]` içindeki alanları form üzerinde işaretler |
| `INVALID_CREDENTIALS` | 401 | E-posta veya şifre hatalı | Formu temizlemeden hata gösterir |
| `TOKEN_EXPIRED` | 401 | Oturum süresi doldu | Sessizce token yeniler, isteği tekrarlar |
| `INSUFFICIENT_ROLE` | 403 | Bu işlem için yetkiniz yok | Butonu gizler veya devre dışı bırakır |
| `RESOURCE_NOT_FOUND` | 404 | Kayıt bulunamadı | Liste sayfasına yönlendirir |
| `ORDER_ALREADY_SHIPPED` | 409 | Sipariş kargoya verilmiş | Kaydı yeniden yükler, "iptal" butonunu kaldırır |
| `INSUFFICIENT_STOCK` | 409 | Yeterli stok yok | Adet alanını işaretler, mevcut stoku gösterir |
| `DUPLICATE_EMAIL` | 409 | Bu e-posta zaten kayıtlı | E-posta alanını işaretler |
| `VERSION_MISMATCH` | 412 | Kayıt başka biri tarafından değiştirildi | Güncel veriyi çeker, kullanıcıya farkı gösterir |
| `IF_MATCH_REQUIRED` | 428 | Sürüm bilgisi eksik | İstemci kodu hatası; loglanır |
| `RATE_LIMITED` | 429 | Çok fazla istek | `Retry-After` kadar bekleyip tekrar dener |
| `INTERNAL_ERROR` | 500 | Beklenmeyen bir hata oluştu | Genel hata ekranı ve `traceId` gösterir |

**Katalog kuralları:**

- Kodlar `SCREAMING_SNAKE_CASE` yazılır ve olayı anlatır: `ORDER_ALREADY_SHIPPED` olur, `ERR_1042` olmaz.
- Bir kod yayımlandıktan sonra anlamı ve status'u değişmez. Gerekirse yeni bir kod eklenir.
- Aynı kod her zaman aynı status ile döner.
- İstemci bilmediği bir kodla karşılaşırsa status kategorisine göre genel davranışı uygular.

### Mesajların dili

- Sunucu `Accept-Language` header'ına göre `title`, `message` ve `detail` alanlarını çevirir. Desteklenmeyen bir dil gelirse varsayılan dil kullanılır.
- İstemci önce kendi i18n dosyasında `code` karşılığına bakar; bulamazsa sunucudan gelen metni gösterir.
- `code` ve `field` alanları hiçbir zaman çevrilmez.

## 7. Listeler: sayfalama, filtreleme, sıralama

Her liste endpoint'i üç stratejiden birini kullanır. Strateji, listenin **büyüyebileceği üst sınıra** ve arayüzün ihtiyacına göre seçilir.

| Strateji | Ne zaman | Örnekler |
| --- | --- | --- |
| Offset | Sayfa numarası ve toplam kayıt sayısı gereken yönetim tabloları | Siparişler, müşteriler, ürünler |
| Cursor | Sürekli yeni kayıt eklenen akışlar veya toplam sayımın pahalı olduğu büyük tablolar | Bildirimler, yorumlar, aktivite geçmişi, audit log |
| Sayfalamasız | Yapısal bir üst sınırı olan küçük listeler | Kategoriler, para birimleri, bir siparişin kalemleri, dropdown seçenekleri |

### Offset sayfalama

```http
GET /orders?page=2&size=20&status=pending&status=confirmed&sort=-createdAt
```

```json
{
  "data": [ "...20 kayıt..." ],
  "meta": { "page": 2, "size": 20, "totalItems": 137, "totalPages": 7 }
}
```

- `page` 1'den başlar. `size` varsayılanı 20, üst sınırı 100'dür.
- `size=500` gibi sınır dışı bir değer **400 döner**; sessizce 100'e düşürülmez. Sessiz düzeltme, istemcinin eksik veriyle çalıştığını fark etmemesine yol açar.
- Son sayfadan sonrası (`page=99`) boş `data` ve doğru `meta` ile 200 döner.
- **Sıralama deterministik olmalıdır.** Sunucu, verilen sıralamanın sonuna her zaman `id` ekler. Aksi halde aynı `createdAt` değerine sahip kayıtlar sayfalar arasında kayar; biri iki kez görünür, biri hiç görünmez.
- Filtreye uyan tüm kayıtların toplamı gerekiyorsa (sadece o sayfanın değil) `meta.totals` alanında döner.

### Cursor sayfalama

```http
GET /notifications?limit=20
GET /notifications?limit=20&cursor=eyJjIjoiMjAyNi0xMS0yMFQwOTozMDowMFoiLCJpIjo5ODd9
```

```json
{
  "data": [ "...20 kayıt..." ],
  "meta": { "nextCursor": "eyJjIjoiMjAyNi0xMS0yMFQwOToxMjowMFoiLCJpIjo5NjF9", "hasMore": true }
}
```

- Cursor **opak** bir string'dir. İstemci içeriğini yorumlamaz, sadece geri gönderir. Sunucu tarafında genelde son kaydın sıralama değerleri ile `id`'nin base64 halidir.
- Sorgu `OFFSET` kullanmaz: `WHERE (created_at, id) < (:c, :i) ORDER BY created_at DESC, id DESC LIMIT :limit`. Bu yüzden milyonlarca satırda bile hızlıdır ve araya yeni kayıt eklense de kayma olmaz.
- Toplam kayıt sayısı hesaplanmaz. Son sayfada `nextCursor: null`, `hasMore: false` döner.
- Geçersiz veya bozulmuş bir cursor 400 döner.

### Sayfalamasız listeler

Kayıt sayısının yapısal bir üst sınırı olduğunda (bir siparişte en fazla 100 kalem, şirket başına en fazla 500 kategori) tüm liste tek seferde döner. Cevap yine `{ "data": [...] }` zarfını kullanır; böylece ileride sayfalamaya geçmek gerekirse sözleşme kırılmaz.

Autocomplete ve dropdown'lar için ayrı bir **lookup** endpoint'i açılır: sadece `id` ve `name` gibi hafif alanları döner, `q` ile filtrelenir ve sabit bir `limit` (en fazla 50) ile sınırlıdır.

```http
GET /customers/lookup?q=ayşe&limit=10
```

```json
{ "data": [ { "id": 7, "name": "Ayşe Yılmaz" }, { "id": 31, "name": "Ayşe Demir" } ] }
```

**`?size=all` diye bir seçenek yoktur.** Tüm veriye ihtiyaç varsa iki yol vardır: yapısal olarak sınırlı bir endpoint ya da asenkron bir **export** işi (bölüm 10). Sınırsız bir sorgu hiçbir zaman HTTP isteğinin içinde çalıştırılmaz.

### Filtreleme

| İhtiyaç | Yazım | Örnek |
| --- | --- | --- |
| Eşitlik | `alan=değer` | `?customerId=7` |
| Birden fazla değer (VEYA) | Parametre tekrar edilir | `?status=pending&status=confirmed` |
| Tarih aralığı (iki uç dahil) | `<alan>From`, `<alan>To` | `?createdFrom=2026-01-01&createdTo=2026-01-31` |
| Sayı aralığı | `<alan>Min`, `<alan>Max` | `?totalMin=100&totalMax=500` |
| Serbest metin | `q` (en az 2 karakter) | `?q=laptop` |
| Boolean | `true` / `false` | `?hasInvoice=true` |
| Görünürlük kapsamı | `scope` | `?scope=mine`, `?scope=team`, `?scope=all` |
| Silinmişleri de getir | `includeDeleted` | `?includeDeleted=true` (yetki gerektirir) |

Kurallar:

- Birden fazla farklı filtre **VE** ile birleştirilir; aynı filtrenin birden fazla değeri **VEYA** ile birleştirilir. `?status=a&status=b&customerId=7` şu anlama gelir: (status a veya b) ve müşteri 7.
- `createdFrom > createdTo` gibi tutarsız bir aralık 400 döner.
- Kullanıcının rolünün izin vermediği bir `scope` değeri 403 döner. Yetki kontrolü filtreye bağlı değildir; filtre gönderilmese bile kullanıcı sadece görebildiği kayıtları alır.
- Bilinmeyen query parametreleri yok sayılır.
- Çok karmaşık filtreler (iç içe VE/VEYA, kullanıcının kaydettiği filtreler) URL'e sığmıyorsa `POST /orders/search` ile JSON gövdede gönderilir. Bu endpoint hiçbir şeyi değiştirmez.

### Sıralama

```http
GET /orders?sort=-total,createdAt
```

- Virgülle ayrılmış alan listesi. Başında `-` olan alan azalan, olmayan artan sıralanır.
- Her endpoint'in izin verdiği sıralama alanları sözleşmede yazılıdır. Listede olmayan bir alan **400 döner.** Bu whitelist aynı zamanda SQL injection'a karşı korumadır; `sort` değeri asla doğrudan SQL'e yazılmaz.
- Her listenin sözleşmede belirtilmiş bir varsayılan sıralaması vardır.

## 8. Veri tipleri ve formatlar

Aynı tür veri her endpoint'te aynı biçimde taşınır.

| Veri | Biçim | Örnek | Not |
| --- | --- | --- | --- |
| Para | `{ amount: string, currency: string }` | `{ "amount": "1250.50", "currency": "TRY" }` | Asla float kullanılmaz |
| Zaman damgası | ISO 8601, UTC, `Z` ile | `"2026-11-20T09:30:00Z"` | Yerel saate çevirme işi istemcinindir |
| Takvim tarihi | `YYYY-MM-DD` | `"2026-11-20"` | Saat dilimi içermez (doğum günü, teslim günü) |
| Süre | Birimi alan adında olan tam sayı | `"timeoutSeconds": 30` | ISO 8601 süre biçimi (`PT30S`) yerine önerilir |
| ID | int64 veya string | `42`, `"ord_01JD3K..."` | Bir API içinde tek bir tür seçilir |
| Para birimi | ISO 4217 | `"TRY"`, `"EUR"` |  |
| Ülke | ISO 3166-1 alpha-2 | `"TR"` |  |
| Dil | BCP 47 | `"tr"`, `"en-US"` |  |
| Telefon | E.164 | `"+905321234567"` |  |
| Enum | snake\_case string | `"in_transit"` | Sayı kodu (`3`) kullanılmaz |
| Boolean | `true` / `false` | `"isActive": true` | `"Y"`, `1`, `"true"` kullanılmaz |
| Yüzde | 0–100 arası sayı | `"discountPercent": 12.5` | Alan adı birimi söyler |

**Para neden string?** JSON sayıları çoğu dilde kayan noktalı (float) olarak okunur ve `0.1 + 0.2` gibi işlemler yanlış sonuç verir. Tutar string olarak taşınır; veritabanında `NUMERIC(15,2)`, Java'da `BigDecimal`, Go'da `shopspring/decimal`, JavaScript'te `decimal.js` gibi bir tipe çevrilir. Para birimi her zaman tutarın yanında durur; "bu alan hangi para biriminde?" sorusu hiç sorulmaz.

**ID'ler için uyarı:** JavaScript, 2^53'ten büyük tam sayıları doğru tutamaz. Snowflake gibi büyük sayısal ID'ler kullanılıyorsa string olarak taşınır. Sıralı ID'ler kayıt sayısını ve oluşturulma hızını dışarıya sızdırır; bu bir sorunsa ULID veya UUIDv7 gibi tahmin edilemeyen ama sıralanabilen ID'ler tercih edilir. Bu karar baştan verilmelidir, sonradan değiştirmek kırıcıdır.

**`null`, boş ve eksik:**

- **Cevaplarda** sözleşmedeki her alan her zaman bulunur. Değeri yoksa `null` döner; alanın hiç gelmemesi istemciyi "alan eksik mi, değer mi yok?" sorusuyla baş başa bırakır.
- **Diziler** hiçbir zaman `null` olmaz; boşsa `[]` döner.
- **PATCH isteklerinde** eksik alan "dokunma", `null` alan "temizle" anlamına gelir (bölüm 3).
- Boş string (`""`) ile `null` aynı şey değildir. Bir alan opsiyonelse ve değeri yoksa `null` kullanılır.

**İlişkili kayıtlar:** Bir kayıt başka bir kayda referans veriyorsa, cevapta sadece ID değil, ekranda gösterilecek kadar özet bilgi döner. Bu, istemcinin her satır için ayrı bir istek atmasını (N+1) önler:

```json
"customer": { "id": 7, "name": "Ayşe Yılmaz", "email": "ayse@example.com" }
```

İsteklerde ise sadece ID gönderilir: `"customerId": 7`.

## 9. Eşzamanlılık ve tekrar güvenliği

İki kullanıcının aynı kaydı aynı anda değiştirmesi ve ağ hatası yüzünden aynı isteğin iki kez gelmesi, her API'nin er geç karşılaşacağı iki durumdur. İkisinin de standart bir çözümü vardır.

### Optimistic locking: ETag ve If-Match

Değişebilen her kayıt bir `version` alanı taşır ve GET cevabında `ETag` header'ı olarak döner. Güncelleme ve action istekleri bu değeri `If-Match` header'ında geri gönderir.

```http
GET /orders/42

HTTP/1.1 200 OK
ETag: "7"
```

```http
PATCH /orders/42
If-Match: "7"
Content-Type: application/merge-patch+json

{ "note": "Kapıya bırakın" }

HTTP/1.1 200 OK
ETag: "8"
```

Bu arada başka biri kaydı güncellediyse (version artık 8 ise) aynı istek **412 Precondition Failed** alır. `If-Match` header'ı hiç gönderilmezse **428 Precondition Required** döner.

Sunucu tarafında bu tek bir sorgudur: `UPDATE orders SET ..., version = version + 1 WHERE id = :id AND version = :expected`. Etkilenen satır sayısı 0 ise 412 döner.

### Pessimistic locking

Bir işlem ortak bir kaynağı tüketiyorsa (stok, bütçe, kontenjan) ve iki eşzamanlı istek limiti birlikte aşabiliyorsa, o kaynak transaction süresince kilitlenir: `SELECT ... FOR UPDATE`. Bu, API sözleşmesini değil sunucu içini ilgilendirir; ama sözleşmede "bu işlem stok yetmezse 409 INSUFFICIENT\_STOCK döner" diye yazılır.

### Idempotency-Key

Para veya stok etkisi olan `POST` isteklerinde (sipariş oluşturma, ödeme, iade) istemci bir `Idempotency-Key` header'ı gönderir. Bu anahtar bir UUID'dir ve istemci tarafından üretilir.

```http
POST /orders
Idempotency-Key: 5f1c2a9e-7b1d-4c3e-9a8f-2d6e4b0c1a77
Content-Type: application/json

{ "customerId": 7, "items": [ { "productId": 15, "quantity": 2 } ] }
```

| Durum | Sunucunun davranışı |
| --- | --- |
| Anahtar ilk kez geliyor | İstek işlenir; cevap anahtarla birlikte 24 saat saklanır |
| Aynı anahtar, aynı gövde | İstek yeniden işlenmez; saklanan cevap `Idempotent-Replayed: true` header'ıyla aynen döner |
| Aynı anahtar, farklı gövde | 422 `IDEMPOTENCY_KEY_REUSED` |
| Aynı anahtarla ilk istek hâlâ işleniyor | 409 `REQUEST_IN_PROGRESS` |
| Header zorunlu ama gelmedi | 400 |

Anahtar kullanıcı ID'si ile birlikte saklanır; başka kullanıcının anahtarı bir şey ifade etmez. İstemci aynı işlem için yaptığı her tekrarda aynı anahtarı kullanır, yeni bir işlem için yeni anahtar üretir.

### Toplu işlemler

```http
POST /orders/batch-cancel

{ "ids": [41, 42, 43], "reason": "Müşteri talebi" }
```

- Her kalem kendi transaction'ında işlenir; birinin başarısız olması diğerlerini geri almaz.
- Cevap 200'dür ve her kalemin sonucu ayrı ayrı döner (bölüm 5, kısmi başarı).
- Kalem sayısının bir üst sınırı vardır (örneğin 100). Aşılırsa 422 döner.
- "Hepsi ya da hiçbiri" davranışı gerekiyorsa bu bir toplu işlem değil, tek bir işlemdir ve tek bir status döner.
- Toplu işlemlerde `If-Match` kullanılmaz. Bu sırada değişmiş bir kayıt, o kalem için başarısız sayılır.

## 10. Asenkron işler, dosyalar ve olaylar

Bir istek birkaç saniyeden uzun sürebilecekse (rapor üretme, toplu içe aktarma, video işleme) HTTP isteğinin içinde yapılmaz. İş kuyruğa alınır ve istemciye hemen cevap döner.

### Asenkron iş akışı

```http
POST /exports
Idempotency-Key: 9a7e...

{ "type": "order_list", "format": "xlsx", "filters": { "createdFrom": "2026-10-01", "createdTo": "2026-10-31" } }

HTTP/1.1 202 Accepted
Location: /exports/91
Retry-After: 3

{ "data": { "id": 91, "status": "queued", "progressPercent": 0 } }
```

```http
GET /exports/91

HTTP/1.1 200 OK
Retry-After: 3

{ "data": { "id": 91, "status": "processing", "progressPercent": 40 } }
```

```http
GET /exports/91

{ "data": { "id": 91, "status": "completed", "fileName": "siparisler-2026-10.xlsx",
            "expiresAt": "2026-11-27T09:30:00Z" } }
```

```http
GET /exports/91/download

HTTP/1.1 302 Found
Location: https://storage.example.com/exports/91.xlsx?signature=...&expires=...
```

- İş durumları: `queued`, `processing`, `completed`, `failed`, `cancelled`, `expired`.
- İş devam ederken `Retry-After` ne kadar sonra tekrar sorulacağını önerir.
- Hazır olmayan bir dosya istenirse 409, süresi dolmuş bir dosya istenirse 410 döner.
- İş bittiğinde kullanıcıya bildirim veya e-posta gönderilebilir; böylece istemcinin sürekli sorgulaması gerekmez.
- `DELETE /exports/91` devam eden işi iptal eder ya da bitmiş dosyayı siler.
- Kullanıcı başına aynı anda çalışabilecek iş sayısı sınırlıdır; aşılırsa 429 döner.

### Dosya yükleme

Küçük dosyalar (örneğin 10 MB'a kadar) doğrudan API'ye `multipart/form-data` ile yüklenir:

```http
POST /orders/42/attachments
Content-Type: multipart/form-data; boundary=---x

---x
Content-Disposition: form-data; name="file"; filename="fatura.pdf"
Content-Type: application/pdf

<binary>
---x--
```

Büyük dosyalarda iki adımlı imzalı URL (presigned URL) akışı kullanılır. Dosya API sunucusundan geçmez:

1. `POST /uploads` → sunucu bir yükleme kaydı ve 15 dakika geçerli bir imzalı PUT URL'i döner.
2. İstemci dosyayı doğrudan depolamaya (S3, MinIO, GCS) yükler.
3. `POST /orders/42/attachments { "uploadId": "..." }` → sunucu dosyayı doğrular ve kayda bağlar.

Dosyalar için kurallar:

- Dosya tipi uzantıya veya istemcinin söylediği `Content-Type`'a göre değil, içeriğin ilk baytlarına (magic bytes) göre kontrol edilir.
- Boyut sınırı aşılırsa 413, desteklenmeyen tip için 415 döner.
- Thumbnail üretme, virüs taraması, OCR gibi işlemler kuyrukta yapılır. Yükleme cevabı 202 olabilir ve kayıtta bir `processingStatus` alanı bulunur.
- Depolama bucket'ı public değildir. İndirme, kısa süreli imzalı bir URL'e 302 yönlendirmesiyle yapılır.

### Olaylar: outbox ve inbox

API bir işlem sonucunda başka bir sisteme haber veriyorsa (e-posta, mesaj kuyruğu, webhook) bunu doğrudan transaction'ın içinden yapmaz.

- **Transactional outbox:** Olay, iş verisiyle **aynı transaction'da** bir `outbox_messages` tablosuna yazılır. Ayrı bir worker bu tabloyu okuyup olayı yayınlar. Böylece "veritabanına yazıldı ama mail gitmedi" ya da "mail gitti ama işlem geri alındı" durumları oluşmaz.
- **Inbox (idempotent consumer):** Dışarıdan gelen webhook'lar ve kuyruk mesajları önce bir `inbox_messages` tablosuna `(source, messageId)` UNIQUE kısıtıyla yazılır. Aynı mesaj ikinci kez gelirse işlenmez. Gönderici sistemler aynı mesajı birden fazla kez yollayabilir; bu bir hata değil, normal davranıştır.
- **Webhook alma:** Gelen webhook'lar HMAC imzasıyla doğrulanır. İmza geçersizse 401 döner. Geçerliyse istek hızlıca kaydedilip 200 dönülür; asıl işleme kuyrukta yapılır.
- **Webhook gönderme:** API kendisi webhook gönderiyorsa her olayın benzersiz bir `id`'si, `type` alanı ve `occurredAt` zamanı olur. Gövde HMAC ile imzalanır ve başarısız gönderimler artan aralıklarla (exponential backoff) tekrar denenir.

## 11. Birden fazla kaynaktan veri toplama ve döngüsel ilişkiler

Bir ekran birden fazla kaynaktan veri istediğinde üç yol vardır: istemcinin paralel çağrı yapması, `include` ile ilişkili kayıtları gömmek ya da o ekrana özel bir görünüm endpoint'i yazmak. Seçim, verinin kaç kaynaktan geldiğine ve kaynakların aynı veritabanında mı yoksa farklı servislerde mi olduğuna bağlıdır.

### Örnek: müşteri 360 ekranı

Müşteri hizmetleri temsilcisi bir müşteriyi açtığında tek ekranda şunları görür:

| Bölüm | Kaynağı | Gösterilen | Kritik mi? |
| --- | --- | --- | --- |
| Profil | Müşteri servisi | Ad, iletişim, segment, kayıt tarihi | Evet; yoksa ekranın anlamı kalmaz |
| Siparişler | Sipariş servisi | Son 5 sipariş, toplam sayı ve tutar | Hayır |
| Destek talepleri | Destek servisi | Açık talepler | Hayır |
| Ödeme durumu | Fatura servisi | Bakiye, geciken ödeme | Hayır |
| Yorumlar | Yorum servisi | Ortalama puan, son 3 yorum | Hayır |
| Sadakat | Sadakat servisi | Puan, seviye | Hayır |

Altı kaynak, altı farklı hız ve altı farklı hata ihtimali demektir. Ayrıca kayıtlar birbirine bağlıdır: siparişin müşterisi, destek talebinin siparişi, yorumun ürünü vardır.

### Üç yol

| Yol | Ne zaman | Artısı | Eksisi |
| --- | --- | --- | --- |
| İstemci paralel çağırır | 2–3 kaynak; her bölüm ekranda bağımsız yüklenebilir | Sunucuda ek kod yok; bir bölümün hatası diğerini etkilemez | Mobilde çok istek; birleştirme mantığı istemciye dağılır |
| `include` ile gömme | İlişkili kayıtlar aynı servis ve veritabanında (sipariş, kalemleri, müşteri özeti) | Tek istek; kaynak odaklı tasarım korunur | Kontrolsüz bırakılırsa ağır sorgu ve döngü riski |
| Görünüm (composite) endpoint'i | Çok kaynak, tek ekran; kaynaklar farklı servislerde | Tek istek; zaman aşımı, kısmi hata ve önbellek tek yerde yönetilir | Ekrana bağımlıdır; ekran değişince endpoint de değişir |

Birçok farklı ekran aynı verilerin farklı birleşimlerini istiyorsa GraphQL de değerlendirilebilir. Bu rehber REST'i kapsar; GraphQL'de de aşağıdaki döngü, derinlik ve kısmi hata kuralları aynı şekilde geçerlidir.

### 1. İstemci tarafında paralel çağrılar

```http
GET /customers/7
GET /orders?customerId=7&sort=-createdAt&size=5
GET /tickets?customerId=7&status=open
```

- İstekler sırayla değil, aynı anda atılır. Her bölüm kendi yükleniyor ve hata durumunu gösterir.
- Başka bir servise ait kayıtlar, ebeveynin altında iç içe yol yerine kök koleksiyon üzerinde filtreyle istenir: `/orders?customerId=7`. Siparişler müşteri servisinin değil sipariş servisinin sorumluluğundadır; URL de bunu yansıtır.

### 2. `include` ile ilişkili kayıtları gömme

Varsayılan cevap ilişkileri sadece **özet** olarak taşır. İstemci daha fazlasına ihtiyaç duyarsa `include` parametresiyle ister:

```http
GET /orders/42?include=items.product,customer
```

```json
{
  "data": {
    "id": 42,
    "status": "confirmed",
    "customer": { "id": 7, "name": "Ayşe Yılmaz", "email": "ayse@example.com", "segment": "gold" },
    "items": [
      { "id": 301, "quantity": 2,
        "product": { "id": 15, "name": "Kablosuz mouse", "sku": "MS-15", "imageUrl": "https://..." } }
    ]
  }
}
```

- Her endpoint'in kabul ettiği `include` değerleri sözleşmede listelenir. Listede olmayan bir değer **400** döner.
- Derinlik en fazla 2'dir (`items.product` olur, `items.product.supplier.country` olmaz).
- Gömülen listeler sınırlıdır. Kalemi sınırsız olabilecek bir ilişki gömülmez; ilk N kayıt ve toplam sayı döner ya da ayrı bir liste endpoint'ine yönlendirilir.
- Gömülen kayıt, ana kaydın içinde yerinde durur. JSON:API'deki gibi ayrı bir `included` dizisi de bir seçenektir; hangisi seçilirse seçilsin tüm API'de aynı biçim kullanılır.
- `include` sadece aynı servisin kendi verisi için kullanılır. Başka bir servise ağ çağrısı gerektiren birleşimler görünüm endpoint'ine aittir.

### 3. Görünüm (composite) endpoint'i

Birden fazla servisten veri toplayan endpoint bir kaynak değil, bir **okuma modelidir**. Bu yüzden adı bunu belli eder:

&#91;embedded content: görünüm endpoint'i · 6 kaynak, 1 kısmi hata\]

```http
GET /customers/7/overview
```

ya da ayrı bir BFF (Backend for Frontend) katmanında:

```http
GET /views/customer-360/7
```

```json
{
  "data": {
    "profile": {
      "id": 7, "name": "Ayşe Yılmaz", "email": "ayse@example.com",
      "segment": "gold", "customerSince": "2023-04-12"
    },
    "orders": {
      "totalCount": 38,
      "lifetimeValue": { "amount": "48210.00", "currency": "TRY" },
      "recent": [
        { "id": 42, "status": "confirmed", "total": { "amount": "1050.00", "currency": "TRY" },
          "createdAt": "2026-11-20T09:30:00Z" }
      ]
    },
    "openTickets": [
      { "id": 913, "subject": "Kargo gecikti", "priority": "high",
        "order": { "id": 40 }, "openedAt": "2026-11-18T16:05:00Z" }
    ],
    "billing": {
      "balance": { "amount": "0.00", "currency": "TRY" },
      "overdueInvoiceCount": 0
    },
    "reviews": {
      "averageRating": 4.6,
      "count": 12,
      "latest": [ { "id": 77, "rating": 5, "product": { "id": 15, "name": "Kablosuz mouse" } } ]
    },
    "loyalty": null
  },
  "meta": {
    "sections": {
      "profile": "ok", "orders": "ok", "openTickets": "ok",
      "billing": "ok", "reviews": "ok", "loyalty": "unavailable"
    },
    "notices": [
      { "code": "SECTION_UNAVAILABLE", "level": "warning", "field": "loyalty",
        "message": "Sadakat bilgisi şu anda alınamadı." }
    ],
    "generatedAt": "2026-11-20T09:31:02Z"
  }
}
```

Görünüm endpoint'inin kuralları:

- Her bölüm kendi şekline sahiptir ve sözleşmede ayrı bir şema olarak tanımlanır.
- Listeler kısa tutulur (`recent`, `latest`) ve yanında toplam sayı döner. "Tümünü gör" bağlantısı normal liste endpoint'ine gider (`/orders?customerId=7`).
- Kaynaklar **paralel** çağrılır. Her kaynağın kendi zaman aşımı (örneğin 800 ms) ve isteklerin tamamı için bir üst süre sınırı (örneğin 2 saniye) vardır. Sürekli hata veren bir kaynak circuit breaker ile bir süre hiç çağrılmaz.
- Görünüm endpoint'i sadece okur. Yazma işlemleri her zaman ilgili kaynağın kendi endpoint'ine gider.

### Kısmi hata

Kaynaklardan biri cevap vermezse ne olacağı, sözleşmede bölüm bazında yazılır:

| Durum | Cevap |
| --- | --- |
| Kritik kaynak (profil) bulunamadı | 404 |
| Kritik kaynak cevap vermedi | 502, 503 veya 504; tüm istek başarısız |
| Kritik olmayan bir kaynak cevap vermedi | 200; o bölüm `null`, `meta.sections` içinde `unavailable`, `meta.notices` içinde uyarı |
| Kullanıcının bir bölümü görme yetkisi yok | 200; o bölüm `null`, `meta.sections` içinde `forbidden` |
| Kaynak cevap verdi ama veri yok | 200; boş liste `[]` veya sıfır değerler, durum `ok` |

Buradaki ince ama önemli ayrım şudur: **`null` "alınamadı", `[]` "yok" demektir.** İstemci birinde "şu an gösterilemiyor, tekrar dene", diğerinde "açık talep yok" yazar. İkisini karıştırmak, temsilcinin müşteriye yanlış bilgi vermesine yol açar.

Kısmi başarı için 207 Multi-Status kullanılmaz. Cevap 200'dür, ayrıntı `meta` içindedir.

### Döngüsel ilişkiler

Kayıtlar birbirine iki yönlü bağlıdır: müşterinin siparişleri, siparişin müşterisi vardır. Destek talebi bir siparişe, sipariş bir müşteriye, müşteri de destek taleplerine bağlıdır. Bunlar kontrolsüzce gömülürse sonsuz bir JSON ya da çok ağır bir sorgu ortaya çıkar.

**1. Her kaynağın iki şeması olur: tam ve özet.**

```yaml
Customer:              # GET /customers/7 cevabı
  id, name, email, phone, segment, addresses[], createdAt, ...
CustomerRef:           # başka bir kaydın içinde göründüğünde
  id, name             # başka hiçbir ilişki taşımaz
```

Özet şemalar hiçbir ilişki alanı taşımaz. Bu sayede döngü yapısal olarak imkansız hale gelir: `Order.customer` bir `CustomerRef`'tir ve `CustomerRef`'in siparişleri yoktur.

**2. Geri referans her zaman özet ya da ID'dir.** Siparişin içindeki müşteri `CustomerRef`, destek talebinin içindeki sipariş `{ "id": 40 }` olarak döner. İstemci daha fazlasını isterse o kaydı kendi endpoint'inden alır.

**3. `include` geri dönemez.** `GET /customers/7?include=orders.customer` gibi bir istek, başladığı kaynağa geri döndüğü için **400** alır. Derinlik sınırı (2) da aynı korumayı sağlar.

**4. Veritabanı nesneleri doğrudan döndürülmez.** ORM entity'leri genelde iki yönlü ilişki taşır. Doğrudan JSON'a çevrildiklerinde ya sonsuz döngüye girerler (Java'da Jackson'ın `StackOverflowError` hatası bilinen bir örnektir) ya da her ilişki için ayrı sorgu atarlar. Her endpoint, sözleşmedeki şemaya karşılık gelen bir DTO döndürür.

### Döngüsel bağımlı oluşturma

Bazen iki kayıt birbiri olmadan oluşturulamaz. Örneğin üye olmadan alışveriş yapan biri için sipariş bir müşteriye, müşteri kaydı da ilk siparişe bağlıdır. İki çözüm vardır:

- **Tek istekte birlikte oluştur.** Bağımlı kayıtları kapsayan bir işlem endpoint'i yazılır ve hepsi tek bir transaction'da oluşturulur: `POST /checkouts { "customer": {...}, "shippingAddress": {...}, "items": [...] }` → `201` ve oluşan `customerId` ile `orderId`.
- **Taslak oluştur, sonra bağla.** Kayıtlardan biri eksik alanlarla `draft` olarak oluşturulur, diğeri ona bağlanır ve son adımda taslak tamamlanır. Bu yol, adımların farklı ekranlarda veya farklı zamanlarda yapıldığı akışlar için uygundur.

İstemcinin iki ayrı isteği sırayla atması ve aradaki hatayı kendisinin toparlaması beklenmez.

### Servisler arası döngü

Sipariş servisi müşteri adını göstermek için müşteri servisini, müşteri servisi de "toplam sipariş" için sipariş servisini çağırıyorsa servisler birbirine kilitlenmiştir. Biri yavaşladığında diğeri de yavaşlar; biri çöktüğünde ikisi birden çöker.

Çözüm, istekleri olaylarla değiştirmektir. Sipariş servisi her siparişte bir `OrderPlaced` olayı yayınlar. Müşteri servisi bu olayı dinleyip kendi tarafında bir özet tutar (`orderCount`, `lastOrderAt`, `lifetimeValue`). Sipariş servisi de müşteri adını sipariş anında kendi kaydına kopyalar. Bu kopya veri biraz gecikmeli olabilir; ekranda "son güncelleme" zamanı gösterilir.

### N+1: listede her satır için istek atmak

20 siparişlik bir listede her siparişin müşteri adını göstermek için 20 ayrı istek atılmaz.

- **Sunucu tarafında:** İlişkili kayıtlar tek sorguda veya toplu bir çağrıyla alınır (`WHERE id IN (...)`, DataLoader kalıbı).
- **İstemci tarafında:** Toplu okuma endpoint'i kullanılır: `GET /customers?ids=3,7,12,19` (en fazla 100 ID; bulunamayan ID'ler sessizce atlanır ve cevapta yer almaz).

### Karar özeti

| Durum | Çözüm |
| --- | --- |
| Aynı servis, yakın ilişki (sipariş + kalemler) | `include`, derinlik en fazla 2 |
| Farklı servisler, tek ekran | Görünüm endpoint'i veya BFF |
| 2–3 bağımsız bölüm | İstemci paralel çağırır |
| Bir bölüm alınamadı | 200, bölüm `null`, `meta.sections` ve `notices` |
| İki yönlü ilişki | Tam ve özet şema; geri referans her zaman özet |
| Birbirine bağımlı oluşturma | Tek işlem endpoint'i veya taslak ve bağlama |
| Servisler birbirini çağırıyor | Olaylar ve okuma için kopya veri |
| Listede her satır için ilişkili kayıt | Toplu yükleme veya `?ids=` endpoint'i |

## 12. Güvenlik, yetki ve limitler

### Kimlik doğrulama

- Her istek `Authorization: Bearer <access token>` header'ı taşır. Token query parametresinde (`?token=`) taşınmaz; URL'ler loglara, tarayıcı geçmişine ve `Referer` header'ına düşer.
- Access token kısa ömürlüdür (10–15 dakika). Tarayıcı istemcilerinde refresh token `HttpOnly; Secure; SameSite=Strict` bir cookie'de, yolu sadece `/auth` ile sınırlı olarak taşınır; JavaScript ona erişemez.
- **Refresh token rotation:** Her yenilemede yeni bir refresh token verilir, eskisi geçersiz olur. İptal edilmiş bir token tekrar kullanılırsa (çalınma şüphesi) kullanıcının tüm oturumları kapatılır.
- Şifre değiştirme ve şifre sıfırlama, diğer tüm oturumları kapatır.
- Token'ın hangi API için verildiği `aud` claim'inde yazar. Bir API'ye ait token diğerinde kabul edilmez.

### Kullanıcı varlığını sızdırmamak

- Girişte hatalı e-posta ile hatalı şifre aynı cevabı alır: `401 INVALID_CREDENTIALS`.
- "Şifremi unuttum" ve "doğrulama mailini tekrar gönder" istekleri, hesap olsa da olmasa da `202` döner.
- Başka bir kiracıya ait kayıt için 403 değil 404 döner.

### Yetkilendirme

- Yetki her istekte sunucuda kontrol edilir. İstemciye dönen `permissions` veya `allowedActions` alanları sadece arayüzde buton göstermek içindir; güvenlik bunlara dayanmaz.
- Rol kontrolü ("manager mı?") ile sahiplik kontrolü ("bu kayıt onun mu?") ayrı ayrı yapılır. İkincisinin unutulması, OWASP API Top 10 listesinin bir numaralı açığıdır (BOLA: Broken Object Level Authorization).
- İstemcinin gönderdiği gövdede yetki alanı kabul edilmez (mass assignment). `PATCH /users/7 { "role": "admin" }` gibi bir istek, sözleşmede ayrıca izin verilmemişse 422 döner. Gövdeler sözleşmedeki şemaya göre doğrulanır; bilinmeyen alanlar reddedilir.

### Çok kiracılı (multi-tenant) sistemler

- Kiracı kimliği token'dan okunur, URL'den veya gövdeden değil.
- Her sorgu kiracı filtresi içermek zorundadır. Bunu her geliştiricinin hatırlamasına bırakmak yerine veri erişim katmanında zorunlu kıl: ORM filtresi, ortak bir sorgu yardımcısı ya da PostgreSQL row-level security.
- İç içe ilişkilerde (`/orders/42/items`) hem ebeveynin hem çocuğun aynı kiracıya ait olduğu kontrol edilir.

### Rate limit

- Giriş, kayıt ve şifre sıfırlama gibi endpoint'ler hem IP hem hesap başına sınırlanır (örneğin 5 başarısız denemeden sonra 15 dakika).
- Genel istekler kullanıcı veya API anahtarı başına sınırlanır (örneğin dakikada 300).
- Pahalı işlemler (export, toplu işlem) ayrı ve daha düşük bir kotaya sahiptir.
- Cevaplar kalan hakkı gösterir:

```http
RateLimit-Limit: 300
RateLimit-Remaining: 287
RateLimit-Reset: 41
```

Limit aşılırsa `429` ve `Retry-After` header'ı döner.

### Diğer kurallar

- Sadece HTTPS kullanılır; HTTP istekleri yönlendirilmez, reddedilir.
- CORS'ta `Access-Control-Allow-Origin: *` kullanılmaz; izin verilen origin'ler listelenir.
- İstek gövdesinin bir boyut sınırı vardır (örneğin JSON için 1 MB).
- Her cevap bir `X-Request-Id` header'ı taşır. İstemci kendi ID'sini gönderirse sunucu aynısını kullanır.
- Şifre, token, kart numarası gibi hassas alanlar loglanmaz; loglarda maskelenir.
- `GET /health` (liveness) ve `GET /ready` (veritabanı, kuyruk ve depolama bağlantıları) kimlik doğrulama gerektirmez ve iç detay dönmez.

## 13. API ayrımı ve versiyonlama

### Ne zaman ayrı bir API?

Kullanıcı kitlesi, güvenlik gereksinimi veya yük profili belirgin şekilde farklıysa API'ler ayrı sözleşmelere bölünür. Tipik bir üründe üç API olur:

| API | Base path | Kim kullanır | Neden ayrı |
| --- | --- | --- | --- |
| Uygulama | `/api/v1` | Son kullanıcılar ve onların yöneticileri | Ürünün kendisi; yazma ağırlıklı |
| Raporlama | `/reports/v1` | Yönetici rolleri | Ağır aggregate sorguları; ileride read replica'ya veya ayrı servise taşınabilir |
| Backoffice | `/admin/v1` | Platform operatörü (kendi ekibin) | Kiracılar arası erişim, ayrı kullanıcı tablosu, 2FA, VPN veya IP kısıtı |

Dikkat edilecek noktalar:

- **Şirket yöneticisi ile platform admini farklı kişilerdir.** Şirket içi yönetim (kullanıcı davet etme, rolleri düzenleme) uygulama API'sinde kalır. Backoffice, tüm kiracıları gören ve altyapıyı (kuyruklar, outbox, zamanlanmış görevler, mail logları) yöneten ekibe aittir.
- **Sözleşmeler ayrı, kod başlangıçta ortak olabilir.** Üç API tek bir uygulamada (modüler monolit) farklı route prefix'leriyle çalışabilir. Modül sınırları temiz tutulursa sonradan ayrı servislere bölmek kolaydır.
- **Token'lar ayrıdır.** Uygulama token'ı backoffice'te, backoffice token'ı uygulamada geçmez.
- **Backoffice'teki her yazma işlemi** zorunlu bir `reason` alanı ister ve audit log'a yazılır.
- **Kullanıcı adına işlem (impersonation)** gerekiyorsa kısa süreli, tercihen salt okunur bir token üretilir; token'da işlemi yapan admin'in kimliği de yer alır.

### Versiyonlama

Major versiyon URL'de yer alır: `/api/v1`. Küçük ve geriye uyumlu değişiklikler versiyon gerektirmez.

| Değişiklik | Kırıcı mı? |
| --- | --- |
| Yeni endpoint eklemek | Hayır |
| Cevaba yeni alan eklemek | Hayır |
| İsteğe opsiyonel alan eklemek | Hayır |
| Enum'a yeni değer eklemek | Hayır, istemciler bilinmeyen değere dayanıklıysa |
| Yeni hata kodu eklemek | Hayır |
| Alan silmek veya yeniden adlandırmak | Evet |
| Alanın tipini değiştirmek | Evet |
| İsteğe yeni zorunlu alan eklemek | Evet |
| Enum'dan değer çıkarmak | Evet |
| Bir hatanın status kodunu değiştirmek | Evet |
| Varsayılan sıralamayı veya sayfa boyutunu değiştirmek | Evet |

**Kırıcı bir değişiklik gerektiğinde:**

1. Yeni davranış `/v2` altında yayımlanır; `/v1` çalışmaya devam eder.
2. Eski endpoint'ler `Deprecation` ve `Sunset` header'larıyla işaretlenir: `Sunset: Wed, 30 Jun 2027 00:00:00 GMT`.
3. Kullanım izlenir; kapatma tarihi istemcilere önceden duyurulur.
4. Tarih geldiğinde eski versiyon 410 döner.

**İstemciler için kural:** Bilinmeyen alanlar yok sayılır. Bilinmeyen bir enum değeri genel bir "diğer" durumu olarak gösterilir, uygulama çökmez.

## 14. Sözleşme önce iş akışı ve araçlar

API'nin tek doğru kaynağı bir OpenAPI dosyasıdır. Bir değişiklik önce bu dosyaya, sonra koda yansır; hiçbir zaman tersi olmaz.

### Akış

1. **Sözleşmeyi yaz.** Endpoint'ler, istek ve cevap şemaları, hata cevapları ve kurallar (`description` alanlarında) OpenAPI dosyasına yazılır.
2. **Gözden geçir.** Sözleşme bir pull request olarak açılır. Frontend ve backend geliştiricileri kod yazılmadan önce üzerinde anlaşır.
3. **Lint'ten geçir.** Bu rehberdeki kuralların çoğu otomatik kontrol edilebilir: isimlendirme, her operasyonda hata cevapları, `operationId` benzersizliği.
4. **Kodu üret.** İstemci tipleri ve fonksiyonları, sunucu tarafındaki DTO'lar ve arayüzler sözleşmeden üretilir.
5. **Mock ile paralel çalış.** Frontend, backend hazır olmadan sözleşmeden üretilen mock sunucuya karşı geliştirilir.
6. **Sözleşmeye karşı test et.** Gerçek sunucunun cevaplarının sözleşmeye uyduğu otomatik testlerle doğrulanır. Aynı API'nin birden fazla implementasyonu varsa (farklı dillerde) hepsi aynı test paketini geçmek zorundadır.

### Dosya düzeni

```text
api/
├── common.yaml          # ortak şemalar: Problem, Money, PageMeta, parametreler, hata cevapları
├── app-api.yaml         # uygulama API'si
├── reports-api.yaml     # raporlama API'si
├── admin-api.yaml       # backoffice API'si
├── errors.yaml          # hata kodu kataloğu
├── redocly.yaml         # lint kuralları
└── dist/                # tek dosyaya birleştirilmiş çıktılar (kod üreticiler için)
```

Ortak parçalar `$ref: './common.yaml#/components/schemas/Problem'` gibi referanslarla paylaşılır. Kod üreticilerin çoğu tek dosya istediği için CI'da önce birleştirme (bundle) yapılır.

### Araçlar

| İş | Araç | Komut |
| --- | --- | --- |
| Lint | Redocly CLI veya Spectral | `npx @redocly/cli lint app-api.yaml` |
| Tek dosyaya birleştirme | Redocly CLI | `npx @redocly/cli bundle app-api.yaml -o dist/app-api.yaml` |
| Dokümantasyon | Redocly, Swagger UI, Scalar | `npx @redocly/cli preview-docs app-api.yaml` |
| Mock sunucu | Prism | `npx @stoplight/prism-cli mock dist/app-api.yaml` |
| TypeScript istemci | openapi-typescript, orval | `npx openapi-typescript dist/app-api.yaml -o src/api/app.d.ts` |
| Go | oapi-codegen, ogen | şemalardan struct ve server arayüzü |
| Java / Kotlin | openapi-generator | DTO'lar ve controller arayüzleri |
| Kırıcı değişiklik kontrolü | oasdiff | `oasdiff breaking old.yaml new.yaml` (CI'da PR'larda çalışır) |

OpenAPI 3.1'i destekleme düzeyi araçtan araca değişir. Özellikle `type: [string, 'null']` ve `if/then` gibi yapılar bazı kod üreticilerde sorun çıkarabilir. Bir üretici bir şemada takılırsa o şemayı elle yazmak, bütün sözleşmeyi eski sürüme indirmekten daha iyidir.

### Sözleşmede her operasyon için bulunması gerekenler

- Benzersiz bir `operationId` (üretilen fonksiyon adı olur: `listOrders`, `confirmOrder`).
- Kısa bir `summary` ve iş kurallarını anlatan bir `description`.
- Bu işlemin dönebileceği **tüm** status kodları ve hangi durumda hangi `code` ile döndüğü.
- Gerekiyorsa `If-Match`, `Idempotency-Key` gibi header'lar.
- Listelerde izin verilen sıralama alanları, varsayılan sıralama ve filtreler.
- Yetki gereksinimi (hangi rol veya yetki).

## 15. Uçtan uca örnek: sipariş kaynağı

Aşağıdaki set, bu rehberdeki kuralların bir kaynak üzerinde birlikte nasıl uygulandığını gösterir. Yeni bir kaynak tasarlarken şablon olarak kullanılabilir.

### Endpoint listesi

| Metot | Yol | Amaç | Başarı |
| --- | --- | --- | --- |
| GET | `/orders` | Listele (offset, filtre, sıralama) | 200 |
| POST | `/orders` | Oluştur (`Idempotency-Key` zorunlu) | 201 |
| GET | `/orders/{id}` | Detay (ETag döner) | 200 |
| PATCH | `/orders/{id}` | Kısmi güncelle (`If-Match` zorunlu) | 200 |
| DELETE | `/orders/{id}` | Sil (sadece `draft` durumunda) | 204 |
| POST | `/orders/{id}/confirm` | draft → confirmed | 200 |
| POST | `/orders/{id}/cancel` | confirmed → cancelled (gerekçe zorunlu) | 200 |
| POST | `/orders/{id}/ship` | confirmed → shipped | 200 |
| GET | `/orders/{id}/items` | Kalemler (sayfalamasız, en fazla 100) | 200 |
| GET | `/orders/{id}/activity` | Değişiklik geçmişi (cursor) | 200 |
| POST | `/orders/batch-cancel` | Toplu iptal (kalem bazında sonuç) | 200 |
| POST | `/exports` | Sipariş listesini dışa aktar | 202 |

### Oluşturma

```http
POST /api/v1/orders
Authorization: Bearer eyJhbGciOi...
Idempotency-Key: 5f1c2a9e-7b1d-4c3e-9a8f-2d6e4b0c1a77
Content-Type: application/json
Accept-Language: tr

{
  "customerId": 7,
  "items": [
    { "productId": 15, "quantity": 2 },
    { "productId": 22, "quantity": 1 }
  ],
  "deliveryDate": "2026-11-25",
  "note": "Kapıya bırakın"
}
```

```http
HTTP/1.1 201 Created
Location: /api/v1/orders/42
ETag: "1"
X-Request-Id: 01JD3K9Z6Q2W8
Content-Type: application/json

{
  "data": {
    "id": 42,
    "status": "draft",
    "customer": { "id": 7, "name": "Ayşe Yılmaz" },
    "items": [
      { "id": 301, "product": { "id": 15, "name": "Kablosuz mouse" }, "quantity": 2,
        "unitPrice": { "amount": "450.00", "currency": "TRY" } },
      { "id": 302, "product": { "id": 22, "name": "Mousepad" }, "quantity": 1,
        "unitPrice": { "amount": "150.00", "currency": "TRY" } }
    ],
    "total": { "amount": "1050.00", "currency": "TRY" },
    "deliveryDate": "2026-11-25",
    "note": "Kapıya bırakın",
    "allowedActions": ["edit", "delete", "confirm"],
    "version": 1,
    "createdAt": "2026-11-20T09:30:00Z",
    "updatedAt": "2026-11-20T09:30:00Z"
  }
}
```

### Listeleme

```http
GET /api/v1/orders?status=confirmed&status=shipped&createdFrom=2026-11-01&sort=-total&page=1&size=2
```

```json
{
  "data": [
    { "id": 40, "status": "shipped", "customer": { "id": 3, "name": "Mehmet Kaya" },
      "total": { "amount": "8900.00", "currency": "TRY" }, "createdAt": "2026-11-12T14:02:00Z" },
    { "id": 38, "status": "confirmed", "customer": { "id": 7, "name": "Ayşe Yılmaz" },
      "total": { "amount": "3200.00", "currency": "TRY" }, "createdAt": "2026-11-08T10:45:00Z" }
  ],
  "meta": {
    "page": 1, "size": 2, "totalItems": 14, "totalPages": 7,
    "totals": { "amount": "41250.00", "currency": "TRY" }
  }
}
```

### Durum geçişi ve uyarı

```http
POST /api/v1/orders/42/confirm
If-Match: "1"
```

```json
{
  "data": { "id": 42, "status": "confirmed", "allowedActions": ["cancel", "ship"], "version": 2, "...": "..." },
  "meta": {
    "notices": [
      { "code": "STOCK_LOW", "level": "warning", "message": "Kablosuz mouse stokta 2 adet kaldı.",
        "field": "items[0].productId" }
    ]
  }
}
```

### Aynı kaydı iki kişi aynı anda değiştirirse

```http
PATCH /api/v1/orders/42
If-Match: "1"
Content-Type: application/merge-patch+json

{ "note": "Komşuya bırakın" }
```

```http
HTTP/1.1 412 Precondition Failed
Content-Type: application/problem+json

{
  "type": "https://errors.example.com/precondition-failed",
  "title": "Kayıt başka biri tarafından değiştirildi",
  "status": 412,
  "code": "VERSION_MISMATCH",
  "detail": "Kayıt şu anda 2. sürümde. Sayfayı yenileyip tekrar deneyin.",
  "traceId": "01JD3M4R8T1Y5"
}
```

### Geçersiz durum geçişi

```http
POST /api/v1/orders/40/cancel
If-Match: "5"
Content-Type: application/json

{ "reason": "Müşteri vazgeçti" }
```

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "https://errors.example.com/conflict",
  "title": "Sipariş kargoya verilmiş",
  "status": 409,
  "code": "ORDER_ALREADY_SHIPPED",
  "detail": "Kargoya verilmiş bir sipariş iptal edilemez; iade sürecini başlatın.",
  "traceId": "01JD3N7W2K9P4"
}
```

### Doğrulama hatası

```http
POST /api/v1/orders
Idempotency-Key: 0b3e...
Content-Type: application/json

{ "customerId": 7, "items": [], "deliveryDate": "2026-11-18" }
```

```http
HTTP/1.1 422 Unprocessable Content
Content-Type: application/problem+json

{
  "type": "https://errors.example.com/validation",
  "title": "Doğrulama hatası",
  "status": 422,
  "code": "VALIDATION_FAILED",
  "traceId": "01JD3P1C6V3H8",
  "errors": [
    { "field": "items", "code": "MIN_ITEMS", "message": "En az bir ürün eklenmeli." },
    { "field": "deliveryDate", "code": "DATE_IN_PAST", "message": "Teslim tarihi geçmişte olamaz." }
  ]
}
```

### OpenAPI sözleşmesinden bir parça

```yaml
/orders/{id}/cancel:
  post:
    operationId: cancelOrder
    summary: Siparişi iptal et (confirmed → cancelled)
    description: |
      Sadece `confirmed` durumdaki siparişler iptal edilebilir.
      - Kargoya verilmişse: 409 ORDER_ALREADY_SHIPPED
      - Kullanıcı siparişin sahibi veya manager değilse: 403 INSUFFICIENT_ROLE
      Stok iadesi ve müşteri bildirimi outbox üzerinden yapılır.
    parameters:
      - $ref: './common.yaml#/components/parameters/Id'
      - $ref: './common.yaml#/components/parameters/IfMatch'
    requestBody:
      required: true
      content:
        application/json:
          schema:
            type: object
            required: [reason]
            properties:
              reason: { type: string, minLength: 5, maxLength: 500 }
    responses:
      '200':
        description: İptal edildi.
        headers:
          ETag: { $ref: './common.yaml#/components/headers/ETag' }
        content:
          application/json:
            schema: { $ref: '#/components/schemas/OrderResponse' }
      '401': { $ref: './common.yaml#/components/responses/Unauthorized' }
      '403': { $ref: './common.yaml#/components/responses/Forbidden' }
      '404': { $ref: './common.yaml#/components/responses/NotFound' }
      '409': { $ref: './common.yaml#/components/responses/Conflict' }
      '412': { $ref: './common.yaml#/components/responses/PreconditionFailed' }
      '422': { $ref: './common.yaml#/components/responses/UnprocessableEntity' }
      '428': { $ref: './common.yaml#/components/responses/PreconditionRequired' }
```

## 16. Yapılmaması gerekenler ve kontrol listesi

### Yapılmaması gerekenler

| Yanlış | Doğrusu |
| --- | --- |
| `200` + `{ "success": false, "error": "..." }` | 4xx/5xx + Problem gövdesi |
| Uydurma status kodu (460, 499, 7xx) | Standart kod + body'de `code` |
| Her hata için 400 | Karar ağacına göre 401, 403, 404, 409, 422 |
| Boş liste için 404 | 200 + `"data": []` |
| Her başarılı cevapta zorunlu `message` | UI metni istemcide; sadece gerekirse `meta.notices` |
| `GET /getOrders`, `POST /orders/42/delete` | `GET /orders`, `DELETE /orders/42` |
| `PATCH { "status": "shipped" }` | `POST /orders/42/ship` |
| `/companies/5/orders` (kiracı URL'de) | Kiracı token'dan okunur |
| Para için float: `"total": 1050.5` | `{ "amount": "1050.50", "currency": "TRY" }` |
| Yerel saat: `"2026-11-20 12:30"` | UTC ISO 8601: `"2026-11-20T09:30:00Z"` |
| Enum için sayı: `"status": 3` | `"status": "shipped"` |
| `?size=all` veya sınırsız liste | Sayfalama, sınırlı endpoint veya export |
| `size` sınırını sessizce düzeltmek | 400 dönmek |
| `sort` değerini doğrudan SQL'e yazmak | Whitelist, bilinmeyen alana 400 |
| Uzun işlemi HTTP isteğinin içinde yapmak | 202 + kuyruk + polling |
| Transaction içinden doğrudan mail veya kuyruk mesajı | Transactional outbox |
| Token'ı query parametresinde taşımak | `Authorization` header'ı |
| 500 cevabında stack trace | Sadece `traceId` |
| Başka kiracının kaydı için 403 | 404 |
| Kodda yazıp sözleşmeyi sonradan güncellemek | Önce sözleşme |

### Yeni endpoint kontrol listesi

Bir endpoint sözleşmeye eklenmeden önce:

**Tasarım**

- [ ] Yol çoğul isim ve kebab-case; fiil yok (action endpoint'leri hariç).
- [ ] Metot doğru; GET'in yan etkisi yok.
- [ ] Durum değişikliği varsa ayrı bir action endpoint'i olarak tanımlandı.
- [ ] `operationId`, `summary` ve iş kurallarını anlatan `description` yazıldı.

**İstek**

- [ ] Gövde şemasında zorunlu alanlar, uzunluk ve değer sınırları tanımlı.
- [ ] PATCH ise `application/merge-patch+json` ve `minProperties: 1` kullanıldı.
- [ ] Değişen bir kayıtsa `If-Match` zorunlu.
- [ ] Para veya stok etkisi olan bir POST ise `Idempotency-Key` zorunlu.
- [ ] Para, tarih, ID, enum alanları bölüm 8'deki biçimlerde.

**Cevap**

- [ ] Başarılı cevap `{ data, meta? }` zarfında.
- [ ] Oluşturmada `201` + `Location`; asenkron işte `202` + `Location`.
- [ ] Liste ise sayfalama stratejisi seçildi; sıralama alanları ve varsayılan sıralama yazıldı.
- [ ] İlişkili kayıtlar ekranda gösterilecek kadar özet bilgiyle dönüyor.

**Hatalar**

- [ ] Dönebilecek tüm status kodları listelendi.
- [ ] Her business hatasının `code` değeri hata kataloğuna eklendi.
- [ ] Yetki kuralı (rol ve sahiplik) yazılı.
- [ ] Kiracı izolasyonu düşünüldü; başka kiracının kaydı 404 dönüyor.

**Sınırlar ve kalite**

- [ ] Liste boyutu, dosya boyutu ve toplu işlem kalem sayısının bir üst sınırı var.
- [ ] Rate limit gerekip gerekmediği değerlendirildi.
- [ ] Lint hatasız geçiyor; kırıcı değişiklik kontrolü temiz.
- [ ] Sözleşmeye karşı çalışan testler yazıldı: başarılı durum, her hata kodu, yetkisiz erişim ve başka kiracının verisi.

### Kaynaklar

- [RFC 9110: HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110) — metotlar, status kodları, koşullu istekler
- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) — hata formatı
- [RFC 7396: JSON Merge Patch](https://www.rfc-editor.org/rfc/rfc7396) — PATCH gövdesi
- [RFC 8594: The Sunset HTTP Header](https://www.rfc-editor.org/rfc/rfc8594) — versiyon kapatma
- [OpenAPI Specification 3.1](https://spec.openapis.org/oas/v3.1.0) — sözleşme formatı
- [OWASP API Security Top 10](https://owasp.org/API-Security/) — API güvenlik açıkları

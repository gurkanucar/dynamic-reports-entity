# entity-reports'u başka bir projeye ekleme

Bu doküman, entity-reports'u mevcut bir Spring Boot projesine (aşağıda "X projesi") eklemek için yapılacakları sırayla
anlatır. Annotation'ların ve REST uçlarının ayrıntısı [README](README.md)'de; burada sadece "ne, hangi sırayla" var.

Özet: **jar'ı erişilebilir yap → bağımlılığı ekle → entity'leri işaretle → kullanıcı/yetkiyi bağla → (opsiyonel)
kayıtlı rapor tablosu, satır yetkisi, frontend.**

İki yol var:

- **A. Bağımlılık olarak (jar):** 1. ve 2. adımlar, sonra 3–9.
- **B. Kaynakları kopyalayarak (bağımlılık yok):** 1. ve 2. adımlar yerine
  [B yolu: kopyala-yapıştır](#b-yolu-kopyala-yapıştır-bağımlılık-olmadan) bölümü, sonra yine 3–9.

## 0. Önkoşullar

| Konu | Gereken |
|---|---|
| Java | 25 |
| Spring Boot | 4.x. Boot 3 ise sadece kopyala-yapıştır yolu (README, "Spring Boot 3 projesine kopyalarken") |
| Veritabanı | PostgreSQL veya H2. Dialect açılışta `DataSource`'tan otomatik seçilir |
| Bean | Bir `DataSource`. `JdbcTemplate` ve transaction manager'ı Boot kurar |
| REST uçları | Servlet web uygulaması (`spring-boot-starter-webmvc`). WebFlux desteklenmez |
| Entity keşfi | JPA (`EntityManagerFactory`). JPA yoksa kendi `DatasetCatalog.of(...)` bean'iniz |

Sorgular JPA ile değil, `JdbcTemplate` üzerinden parametreli SQL olarak çalışır; JPA sadece entity listesini ve
tablo/kolon isimlerini bulmak için kullanılır.

## 1. Jar'ı X projesinin erişebileceği bir yere koy

Kütüphane şu an `com.gucardev:entity-reports:0.0.1-SNAPSHOT` olarak bu reponun bir modülü; hiçbir yerde yayınlanmış
değil. Üç seçenek:

| Yol | Ne zaman | Nasıl |
|---|---|---|
| Kurum içi Maven reposu (Nexus/Artifactory) | X projesi CI'da build ediliyorsa (önerilen) | Kök `pom.xml`'e `<distributionManagement>` ekle, `sh mvnw -pl entity-reports -am deploy` |
| Yerel kurulum | Sadece denemek için | `sh mvnw -pl entity-reports -am install -DskipTests` → `~/.m2`'ye gider. CI'da çalışmaz |
| Kaynakları kopyala | Jar yayınlanamıyorsa | [B yolu](#b-yolu-kopyala-yapıştır-bağımlılık-olmadan). 1. ve 2. adımı atla |

Notlar:

- `entity-reports/pom.xml` parent olarak kök pom'u (`dynamic-reports-entity`) kullanıyor. Deploy ederken parent pom
  da repoya gitmeli (`-am` bunu yapar); yoksa X projesi jar'ı çözemez.
- Kalıcı kullanım için `SNAPSHOT` yerine sabit bir sürüm (`0.1.0`) yayınlayın; X projesi her build'de farklı kod
  almasın.

## 2. Bağımlılığı ekle

```xml
<dependency>
    <groupId>com.gucardev</groupId>
    <artifactId>entity-reports</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gelen geçişli bağımlılıklar: `spring-boot-starter-jdbc`, `jakarta.persistence-api`, `jakarta.validation-api`,
Jackson 3 (`tools.jackson`), Apache POI (`poi-ooxml`). Web starter'ı `optional`; X projesinde zaten olmalı.

Auto-configuration `META-INF/spring/...AutoConfiguration.imports` ile kendiliğinden yüklenir, `@Import` veya
`@ComponentScan` gerekmez. Kapatmak için `entity-reports.enabled=false`.

## 3. Entity'leri işaretle

Raporlanacak her entity'ye `@DatasetEntity`, görünecek alanlara `@DatasetField`, ilişki id'lerine
`@DatasetReference` ekleyin. Örnek ve ilişki türleri README'nin 2. bölümünde.

Karar verilmesi gerekenler:

- **opt-in mi opt-out mu?** Varsayılan opt-in: sadece `@DatasetField` olan alan görünür. Hassas veri olan entity'lerde
  bunu kullanın; `includeAllFields = true` yeni eklenen her kolonu otomatik açar.
- **Hassas alanlar:** maaş, TCKN gibi alanlara `authority = "..."` verin. Yetkisi olmayan kullanıcı alanı katalogda
  bile görmez.
- **İlişkiler:** X projesinde `@ManyToOne Department department` varsa, kütüphane id alanı bekler. Ya yanına
  `@Column(name = "department_id", insertable = false, updatable = false) Long departmentId` ekleyip onu
  işaretleyin, ya da ilişkiyi id alanına çevirin.
- **İsimlendirme:** Tablo/kolon isimleri Boot'un varsayılan stratejisiyle (camelCase → snake_case) çıkarılır;
  `@Table(name)` ve `@Column(name)` okunur. X projesi farklı bir `PhysicalNamingStrategy` kullanıyorsa, isimleri
  `@Table`/`@Column` ile açıkça yazın.

Hatalı bir annotation (olmayan `target`, çakışan isim) uygulamayı açılışta açık bir mesajla durdurur.

## 4. Kullanıcıyı ve yetkiyi bağla

Kütüphane her istekte bir `Dataset.Viewer` (kullanıcı adı + yetkiler) ister.

- **Spring Security varsa:** ek iş yok. Varsayılan `ViewerResolver`, servlet principal'ından kullanıcıyı ve
  `getAuthorities()`'ten yetkileri okur.
- **Kendi oturum/token yapınız varsa:** bir bean tanımlayın:

  ```java
  @Bean
  DatasetWeb.ViewerResolver datasetViewerResolver(CurrentUser currentUser) {
      return request -> Dataset.Viewer.of(currentUser.username(), currentUser.authorities());
  }
  ```

  Kendi kullanıcı tipiniz `Dataset.Viewer`'ı implement ederse (bu projede `Actor`) doğrudan da verilebilir.

Ayarlar:

```properties
# Uçları kim kullanabilir (boş bırakmayın, aşağıya bakın)
entity-reports.web.user-authority=REPORT_USER
# SQL önizleme ve başkasının kayıtlı raporunu değiştirme
entity-reports.admin-authority=REPORT_ADMIN
```

**Dikkat:** `user-authority` boşsa ve istek principal'sız gelirse kullanıcı `anonymous` olur ve rapor çalıştırabilir.
Ya `user-authority` verin, ya da security config'te `/api/datasets/**` ve `/api/saved-datasets/**` yollarını
`authenticated()` yapın; ikisi birden en güvenlisi.

Session/cookie tabanlı auth kullanıyorsanız `POST /run`, `/export` istekleri CSRF korumasına takılır; frontend'in
CSRF token'ı gönderdiğinden emin olun.

## 5. Kayıtlı raporlar (opsiyonel)

İstemiyorsanız: `entity-reports.saved.enabled=false`. Bitti.

İstiyorsanız `saved_dataset` tablosu gerekir. DDL jar'ın içinde: `META-INF/entity-reports/schema.sql`.

- **Flyway/Liquibase kullanıyorsanız (önerilen):** DDL'i X projesinin migration klasörüne kopyalayın, ör.
  `db/migration/V20261127_1__saved_dataset.sql`, ve `entity-reports.saved.initialize-schema=false` bırakın.
  Tablo şeması kütüphanenin `SavedDatasets` sınıfıyla birebir uyumlu olmalı (özellikle `id uuid` PK ve `name`
  unique); sütunları değiştirmeyin.
- **Basit kurulum:** `entity-reports.saved.initialize-schema=true` tabloyu açılışta `create table if not exists` ile
  açar. Migration aracı olan projede bunu kullanmayın; şema iki yerden yönetilir.

Başka bir özellik kayıtlı raporu isimle kullanıyorsa (zamanlanmış e-posta vb.), silinmesini engellemek için:

```java
@Component
class ScheduledMailUsage implements SavedDatasets.Usage {
    public boolean usesSavedDataset(String name) { return mailJobs.existsByDatasetName(name); }
}
```

## 6. Satır yetkisi (opsiyonel)

Kullanıcı bazı satırları görmemeliyse (sadece kendi departmanı vb.) bir `Dataset.RowPolicy` bean'i yazın
(README, 3. bölüm). Kısıt, entity'nin geçtiği her yere ve `owner = true` ile ona bağlı entity'lere otomatik uygulanır.
Birden fazla policy olabilir; hepsi uygulanır.

Arka plan işleri `Dataset.Viewer.system("amaç")` ile çalışır ve **hiçbir kısıta takılmaz**; ürettikleri dosyanın kime
gittiğini o iş kontrol etmeli.

## 7. Ayarları gözden geçir

Varsayılanlar çoğu proje için uygundur; tam liste README'nin 7. bölümünde. Üretimde özellikle bakılacaklar:

| Ayar | Varsayılan | Neden bakılmalı |
|---|---|---|
| `entity-reports.query-timeout` | 30s | Rapor sorgusu DB'yi bu kadar meşgul edebilir |
| `entity-reports.max-page-size` | 5000 | Tek istekte dönen en fazla satır |
| `entity-reports.export-max-rows` | 1000000 | İndirme üst sınırı; daha büyüğü arka plan işi olmalı |
| `entity-reports.web.path` | `/api/datasets` | X projesinde bu yol doluysa değiştirin |
| `entity-reports.index-check-on-startup` | true | İndekssiz ilişki kolonlarını açılışta loglar; uyarıları okuyun |

Rapor sorguları ana uygulama ile aynı connection pool'u kullanır. Ağır raporlar bekleniyorsa read replica'ya giden
ayrı bir `DataSource` düşünün (kütüphane `@Primary` olanı kullanır).

Üretilen SQL'i görmek için: `logging.level.com.gucardev.entityreports=DEBUG`.

## 8. Frontend (opsiyonel)

Kütüphane sadece REST uçları verir; rapor oluşturucu ekranı bu reponun `ui/src/features/datasets` klasöründe
(React + TypeScript + shadcn). X projesi de React/shadcn ise:

1. `ui/src/features/datasets` klasörünü kopyalayın.
2. Kullandığı ortak parçaları sağlayın:
   - shadcn bileşenleri: `button`, `select`, `input`, `field`, `toggle-group`, `textarea`, `table`, `spinner`,
     `skeleton`, `popover`, `empty`, `command`, `card`, `badge`, `tabs`, `dropdown-menu`, `dialog`, `button-group`,
     `alert-dialog` (`pnpm dlx shadcn@latest add ...`)
   - `@/shared/api/http-client` (`http`, `saveResponseAsFile`) — X projesinin kendi HTTP istemcisine bağlayın
   - `@/shared/components/*` (`error-alert`, `page-header`, `search-input`, `pager`, `date-picker`),
     `@/shared/lib/format`, `@/shared/lib/list-state`, `@/shared/types/api.types`, `@/lib/utils`
3. Bu projeye özel bağımlılıkları çıkarın: `features/flows` (rapor zamanlama diyaloğu), `features/assistant`
   (asistan paneli), `features/auth/queries/use-current-user`. X projesinde karşılığı yoksa ilgili butonları silin.
4. Route'ları ekleyin: `/datasets` → `SavedDatasetsPage`, `/datasets/new` ve `/datasets/:datasetId` →
   `DatasetBuilderPage`.

Farklı bir frontend'de: `GET /api/datasets/catalog` ile alanları çekip `POST /api/datasets/run` ile çalıştırmak
yeterli. İstek/yanıt tipleri `ui/src/features/datasets/types/*.dto.ts` içinde.

## 9. Doğrulama

- [ ] Uygulama açılıyor ve logda `entity-reports catalog ready: N entities, M relations` görünüyor (N > 0)
- [ ] Açılıştaki index uyarıları okundu; gereken index'ler migration'a eklendi
- [ ] `GET /api/datasets/catalog` yetkili kullanıcıyla 200, yetkisiz/anonim kullanıcıyla 401/403 dönüyor
- [ ] Yetkili alan (`authority`) yetkisi olmayan kullanıcının katalogunda görünmüyor
- [ ] `POST /api/datasets/run` basit bir tanımla satır dönüyor; satır yetkisi varsa başka departmanın satırı yok
- [ ] `POST /api/datasets/export?format=xlsx` dosya indiriyor
- [ ] (Kayıtlı rapor açıksa) kaydet, listele, güncelle, sil çalışıyor
- [ ] Testcontainers ile gerçek PostgreSQL'de en az bir entegrasyon testi var (H2 ile geçen sorgu PostgreSQL'de
      farklı davranabilir)

Hızlı deneme:

```bash
curl -u user:pass http://localhost:8080/api/datasets/catalog
curl -u user:pass -H 'Content-Type: application/json' \
  -d '{"root":"Personnel","columns":[{"field":"id"}]}' \
  'http://localhost:8080/api/datasets/run?size=5'
```

## B yolu: kopyala-yapıştır (bağımlılık olmadan)

Jar yayınlamak mümkün değilse, kütüphanenin 14 Java dosyası X projesinin içine bir paket olarak kopyalanır. Kod
aynıdır; fark, kodun artık X projesinin parçası olması: güncellemeyi siz taşırsınız, ayarların bir kısmı farklı
davranır (aşağıda).

### B1. Dosyaları kopyala ve paketi değiştir

Hedef paket, `@SpringBootApplication` sınıfının paketinin **altında** olmalı; kütüphane component scan ile bulunur.

```bash
SRC=dynamic-reports-entity/entity-reports/src/main
DST=x-proje/src/main
PKG=com.firma.xproje.reporting            # kendi paketin
DIR="$DST/java/${PKG//.//}"

mkdir -p "$DIR"
cp "$SRC"/java/com/gucardev/entityreports/*.java "$DIR"/
rm "$DIR/SavedDatasets.java"              # kayıtlı rapor İSTEMİYORSAN (bkz. B4)
sed -i '' "s/com\.gucardev\.entityreports/$PKG/g" "$DIR"/*.java   # Linux'ta: sed -i "s/.../g"
```

Kopyalanmayacaklar:

- `META-INF/spring/...AutoConfiguration.imports`: sadece jar içindir; kopyalanırsa sınıf ismi tutmaz ve açılış hata
  verir.
- `META-INF/entity-reports/schema.sql`: sadece `entity-reports.saved.initialize-schema=true` kullanacaksan, aynı yola
  (`src/main/resources/META-INF/entity-reports/schema.sql`) kopyala. Flyway kullanıyorsan gerekmez (B4).

Kopyanın hangi sürümden alındığını kaydet, ör. paketin içine bir `package-info.java` yorumu:
`// entity-reports kopyası, kaynak commit: 4805be3`. Güncellerken o commit ile yeni sürüm arasındaki farkı
(`git diff 4805be3..HEAD -- entity-reports/src/main`) uygularsın.

### B2. Bağımlılıkları ekle

Boot projesinde çoğu zaten vardır. Eksik olanları ekle:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>   <!-- JPA yoksa: spring-boot-starter-jdbc -->
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.4.1</version>                                 <!-- Excel export -->
</dependency>
```

Opsiyonel: `spring-boot-configuration-processor` (annotation processor olarak) eklenirse IDE `entity-reports.*`
ayarlarını otomatik tamamlar.

Jackson 3 (`tools.jackson`) Boot 4 ile gelir. **Spring Boot 3** projesinde Jackson 2 vardır; o zaman:

- `DatasetExecutor` ve `SavedDatasets`'te `tools.jackson.databind.json.JsonMapper` →
  `com.fasterxml.jackson.databind.json.JsonMapper`
- Jackson 2'de `readValue` / `writeValueAsString` checked exception fırlatır: `try/catch` ile
  `UncheckedIOException`'a sar.
- `@AutoConfigureAfter` içindeki sınıf isimleri Boot 3'te farklıdır ama kopya modunda kullanılmaz; dokunma.

### B3. Derle ve aç

`./mvnw compile`, sonra uygulamayı başlat. Logda `entity-reports catalog ready: ...` satırı görünmeli. Bundan sonrası
A yoluyla aynı: **3. adımdan (entity'leri işaretle) devam et.**

Log kategorisi artık senin paketin: `logging.level.com.firma.xproje.reporting=DEBUG`.

### B4. Kopya modunda farklı davranan ayarlar

Kopya modunda controller'lar (`@RestController` olan iç sınıflar) component scan ile **doğrudan** kaydolur; ayarlara
bakılmaz. Bu yüzden:

| Ayar | Jar'da | Kopyada |
|---|---|---|
| `entity-reports.web.enabled=false` | REST uçlarını kapatır | **Etkisiz**: uçlar yine açılır. Kapatmak için security config'te yolları engelle |
| `entity-reports.saved.enabled=false` | Kayıtlı raporları kapatır | **Kullanma**: controller yine kaydolur, servisi bulamaz, açılış hata verir. Kayıtlı rapor istemiyorsan `SavedDatasets.java`'yı kopyalama |
| `entity-reports.enabled=false` | Her şeyi kapatır | Aynı sebeple açılış hata verir; kapatmak için paketi sil |
| Diğer tüm ayarlar | — | Aynı çalışır |

Kayıtlı rapor istiyorsan `SavedDatasets.java`'yı al ve tabloyu 5. adımdaki gibi migration ile aç.

### B5. Testler (önerilen)

`entity-reports/src/test/java/.../DatasetQueryBuilderTest.java` ve `PlugInTest.java` da paket değiştirilerek
kopyalanabilir. İkisi de H2 ister (`com.h2database:h2`, test scope). Kodu ileride kendi tarafında değiştirirsen
bu testler davranışın bozulmadığını gösterir.

### Hangi yol?

| | A. Jar | B. Kopya |
|---|---|---|
| Kurulum | Kurum içi Maven reposu gerekir | Repo gerekmez |
| Güncelleme | Sürüm numarasını artır | Farkı elle taşı |
| Kodu projeye özel değiştirmek | Mümkün değil (fork gerekir) | Serbest, ama güncellemesi zorlaşır |
| Ayarlarla kapatma | Çalışır | Kısmen (B4) |
| Birden fazla proje kullanacaksa | Uygun | Her projede ayrı kopya, zamanla ayrışır |

Tek proje ve repo yoksa B; iki veya daha fazla proje kullanacaksa A.

## Sık karşılaşılan sorunlar

| Belirti | Sebep | Çözüm |
|---|---|---|
| Katalog boş (`0 entities`) | Entity'lerde `@DatasetEntity` yok ya da JPA yok | Annotation'ları ekleyin; JPA yoksa `DatasetCatalog.of(...)` bean'i |
| Açılışta "points at unknown dataset-ready entity" hatası | `@DatasetReference(target)` entity'nin mantıksal adıyla eşleşmiyor | `target` = hedefin `@DatasetEntity(name)`'i, yoksa sınıf adı |
| Sorguda "column does not exist" | Tablo/kolon adı farklı bir naming strategy ile üretilmiş | `@Table`/`@Column` ile adı açıkça yazın |
| Herkes 403 alıyor | Yetki adları eşleşmiyor (ör. `ROLE_` öneki) | `user-authority` değerini `getAuthorities()`'in döndürdüğüyle aynı yazın |
| `saved_dataset` bulunamadı | Tablo açılmamış | 5. adım: migration ekleyin veya `saved.enabled=false` |
| `/api/datasets` başka controller ile çakışıyor | Yol dolu | `entity-reports.web.path` değiştirin |
| Kendi controller'ımız/bean'imiz devre dışı kaldı | — | Olmaz: tüm bean'ler `@ConditionalOnMissingBean`; sizinki varsa o kullanılır |
| (Kopya) Uçlar hiç açılmıyor, katalog logu yok | Paket `@SpringBootApplication` paketinin dışında | Paketi uygulama paketinin altına taşı |
| (Kopya) `ClassNotFoundException: com.gucardev.entityreports...` | `AutoConfiguration.imports` kopyalanmış ya da bir `import` değişmemiş | O dosyayı sil; `grep -r com.gucardev` ile kalanları bul |
| (Kopya) Açılışta `SavedDatasets` bean'i bulunamadı | `saved.enabled=false` verilmiş ama dosya kopyalanmış | Ayarı kaldır ya da `SavedDatasets.java`'yı sil (B4) |

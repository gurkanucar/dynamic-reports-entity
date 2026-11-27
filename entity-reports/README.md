# entity-reports

JPA entity'lerinin üzerinden self-servis raporlama. Entity'lere birkaç annotation eklersiniz; analistler alan seçer,
filtreler, gruplar, ilişkiler üzerinden gezer (`department.manager.name`). Kütüphane parametreli SQL üretir, alan ve
satır yetkilerini uygular, sayfalar ve Excel/CSV olarak indirir. Spring Boot auto-configuration ile gelir: bağımlılığı
ekleyin, entity'leri işaretleyin, bitti.

Desteklenen veritabanları: PostgreSQL ve H2. Java 25, Spring Boot 4.2.

İki şekilde kullanılır: **jar olarak** (aşağıdaki 1. adım) veya **kaynakları kopyalayarak** (en alttaki
"Kopyala-yapıştır ile kullanım" bölümü). İkisinde de kod aynıdır.

## 1. Bağımlılık

```xml
<dependency>
    <groupId>com.gucardev</groupId>
    <artifactId>entity-reports</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Gerekenler: bir `DataSource` (JdbcTemplate ve transaction manager'ı Boot zaten kurar). REST uçları için
`spring-boot-starter-webmvc`. Kullanıcı servlet principal'ından okunur; Spring Security varsa yetkileri de
(compile bağımlılığı olmadan). Farklıysa kendi `DatasetWeb.ViewerResolver` bean'inizi tanımlayın.

## 2. Entity'leri işaretleyin

```java
@Entity
@DatasetEntity(label = "Personel")                       // rapor kökü ve ilişki hedefi olur
public class Personnel {
    @Id Long id;                                         // tek kolonlu PK her zaman "id" olarak açılır
    @DatasetField(label = "Ad Soyad") String fullName;   // varsayılan: sadece işaretli alanlar görünür
    @DatasetField(label = "Maaş", authority = "SALARY_VIEW") BigDecimal salary;   // alan yetkisi
    @DatasetReference(target = "Department", inverse = "members", owner = true)
    Long departmentId;                                   // ilişki: "department" ve ters yönde "members"
    String passwordHash;                                 // işaretsiz: raporda asla görünmez
}

@Entity
@DatasetEntity(label = "Eğitim", includeAllFields = true) // tersine: her alan açık...
public class Training {
    @Id Long id;
    String name;
    @DatasetHidden String internalNote;                  // ...bu hariç
}
```

İlişki türleri: many-to-one (`@DatasetReference`), one-to-many (`inverse`), one-to-one (`oneToOne = true`),
self-reference (`target` kendisi), many-to-many (iki referanslı ara entity). Entity'ler birbirini import etmez; sadece
id alanı tutulur, kütüphane ilişkiyi isimle kurar.

Tablo/kolon isimleri Spring Boot'un varsayılan isimlendirmesiyle (camelCase → snake_case) çıkarılır;
`@Table(name)` ve `@Column(name)` dikkate alınır. Hatalı bir annotation uygulamayı açılışta net bir mesajla durdurur.

## 3. Satır yetkisi (opsiyonel)

```java
@Component
class DepartmentRowPolicy implements Dataset.RowPolicy {
    public List<Dataset.RowRestriction> restrictionsFor(Dataset.Viewer viewer) {
        if (viewer.hasAuthority("ALL_DEPARTMENTS")) return List.of();
        return List.of(new Dataset.RowRestriction("Personnel", "departmentId", departmentOf(viewer)));
    }
}
```

Kısıt, entity'nin geçtiği her yere (kök, join'ler, alt sorgular) ve onu `owner = true` ile sahip olarak gösteren
entity'lere (ör. kişinin eğitim kayıtları) otomatik uygulanır.

Kendi kullanıcı tipiniz `Dataset.Viewer`'ı implement ederse servislere doğrudan verilebilir (bu projede `Actor`).

## 4. Kayıtlı raporlar (opsiyonel)

Tamamı tek dosyada: `SavedDatasets.java` (servis, REST uçları, hata eşleme, Spring kurulumu). Başka hiçbir dosya ona
bağlı değil; istemiyorsanız o dosyayı almayın (jar'da: `entity-reports.saved.enabled=false`).

`saved_dataset` tablosu gerekir. DDL: `META-INF/entity-reports/schema.sql` (H2 + PostgreSQL). Flyway/Liquibase
kullanıyorsanız migration'a kopyalayın; basit kurulumda `entity-reports.saved.initialize-schema=true` tabloyu açılışta
oluşturur.

Bir kayıtlı raporu kullanan başka bir özellik (zamanlanmış e-posta vb.) `SavedDatasets.Usage` bean'i tanımlarsa, o
kullanımdayken rapor silinemez.

## 5. REST uçları

| Uç | Ne yapar |
|---|---|
| `GET  /api/datasets/catalog` | Kullanıcının görebileceği entity, alan ve ilişkiler |
| `POST /api/datasets/run?page=0&size=200&includeTotal=false` | Bir sayfa satır |
| `POST /api/datasets/export?format=xlsx\|csv` | Tüm sonuç, akışla dosya |
| `POST /api/datasets/preview` | Üretilen SQL + index önerileri (sadece admin) |
| `GET  /api/datasets/index-advice` | Gerçek kullanımda indekssiz kalan kolonlar ve DDL önerisi |
| `GET/POST/PUT/DELETE /api/saved-datasets[/{id}]` | Kayıtlı raporlar (`{"data": ...}` zarfıyla) |

Hatalar RFC 9457 problem formatında, sabit bir `code` ve `traceId` ile döner.

Örnek tanım:

```json
{
  "root": "Personnel",
  "columns": [ {"field": "department.name"},
               {"aggregate": "COUNT", "label": "Kişi sayısı"},
               {"field": "salary", "aggregate": "AVG"} ],
  "where":   {"field": "active", "operator": "EQ", "value": true},
  "having":  {"aggregate": "COUNT", "operator": "GTE", "value": 3},
  "sort":    [ {"field": "salary", "aggregate": "AVG", "direction": "DESC"} ]
}
```

Desteklenenler: gruplama ve `COUNT/COUNT_DISTINCT/SUM/AVG/MIN/MAX/LIST`, tarih gruplama (`YEAR`, `MONTH`...),
iç içe AND/OR, `EXISTS`/`NOT_EXISTS`, alan–alan ve alan–ortalama karşılaştırma, kişi başına alt listeler (`perRoot`).
Satır çoğaltıp yanlış toplam üretecek tanımlar (fan/chasm trap) hata mesajıyla reddedilir.

## 6. Kodla kullanım

```java
Dataset.Definition def = new Dataset.Definition("Personnel",
        List.of(Dataset.Column.of("department.name"), Dataset.Column.count()), null, null, null);
Dataset.Result page = executor.run(def, viewer, 0, 50, false);
executor.write(executor.prepare(def, viewer), Dataset.ExportFormat.XLSX, outputStream);
```

Arka plan işleri için `executor.export(...)`: satır limiti aşılınca hata verir ve `DatasetExecutor.QueryHandle` ile
başka thread'den iptal edilebilir.

## 7. Ayarlar (`entity-reports.*`)

| Ayar | Varsayılan | |
|---|---|---|
| `web.enabled` | `true` | REST uçlarını kaydet |
| `web.path` / `web.saved-path` | `/api/datasets` / `/api/saved-datasets` | |
| `web.user-authority` | boş | Uçları kullanmak için gereken yetki (boş: güvenlik ayarınıza bırakılır) |
| `admin-authority` | boş | SQL önizleme ve başkasının raporunu değiştirme (boş: kimse) |
| `default-page-size` / `max-page-size` | 200 / 5000 | |
| `query-timeout` | 30s | |
| `export-max-rows` / `export-fetch-size` | 1000000 / 1000 | |
| `slow-query-threshold` | 500ms | Daha yavaş sorgular index ipuçlarıyla loglanır |
| `index-check-on-startup` | `true` | İndekssiz ilişki kolonlarını açılışta uyar |
| `saved.enabled` / `saved.initialize-schema` | `true` / `false` | |
| `enabled` | `true` | Kütüphaneyi tamamen kapat |

Her bean `@ConditionalOnMissingBean`: kendi `DatasetCatalog`, `DatasetWeb.ViewerResolver` vb. tanımınız varsa o
kullanılır. JPA yoksa kataloğu elle verin: `DatasetCatalog.of(List.of(A.class, B.class))`.

## Dosyalar

| Dosya | İçerik |
|---|---|
| `Dataset` | Tüm veri tipleri: tanım, kolon, koşul, sıralama, enum'lar, sonuç, `Viewer`, `RowPolicy`, hatalar |
| `DatasetEntity`, `DatasetField`, `DatasetReference`, `DatasetHidden` | Entity annotation'ları |
| `DatasetCatalog` | Annotation'ları okur; entity/alan/ilişki haritası |
| `DatasetQueryBuilder` | Tanım → SQL (join'ler, gruplama, alt sorgular, yetkiler) |
| `DatasetExecutor` | Sayfalı çalıştırma, dosya akışı, arka plan export, iptal |
| `TableWriter` | Excel (akışlı) ve CSV yazıcıları |
| `IndexAdvisor` | Index kontrolü ve öneriler |
| `DatasetWeb` | Controller, hata eşleme, kullanıcı çözümleyici |
| `SavedDatasets` | *Opsiyonel:* kayıtlı raporlar – servis, controller, hatalar, kurulum (JDBC) |
| `EntityReportsAutoConfiguration`, `EntityReportsProperties` | Spring Boot kurulumu ve ayarlar |

`PlugInTest`, kütüphanenin uygulama olmadan (sadece H2 + Boot, JPA ve Spring Security yok) çalıştığını gösterir.

## Kopyala-yapıştır ile kullanım

Jar yayınlamadan, mevcut bir projeye (Java 25, Spring Boot 4) doğrudan:

1. `src/main/java/com/gucardev/entityreports/` altındaki dosyaları projenizde bir pakete kopyalayın (ör.
   `com.firma.proje.reporting`) ve `package` / `import` satırlarındaki `com.gucardev.entityreports`'u o paketle
   değiştirin. Paket, `@SpringBootApplication` sınıfınızın paketinin altında olmalı (component scan bulsun).
   - Kayıtlı rapor istemiyorsanız `SavedDatasets.java`'yı **almayın**. İstiyorsanız
     `src/main/resources/META-INF/entity-reports/schema.sql`'i de aynı yola kopyalayın (veya tabloyu kendi
     migration'ınızla açın).
   - `META-INF/spring/...AutoConfiguration.imports` dosyası **gerekmez** (sadece jar içindir).
2. Bağımlılıklar (Boot'ta çoğu zaten vardır): `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa` (veya
   `-jdbc`), `spring-boot-starter-validation` ve
   ```xml
   <dependency>
       <groupId>org.apache.poi</groupId>
       <artifactId>poi-ooxml</artifactId>
       <version>5.4.1</version>
   </dependency>
   ```
3. Entity'lere annotation'ları ekleyin (2. bölüm). Bitti: `/api/datasets/catalog` açılır.

Doğrulandı: boş bir Boot 4 projesine farklı bir pakete kopyalanıp (kayıtlı raporlarla ve onlarsız) katalog, çalıştırma
ve Excel indirme test edildi.

**Spring Boot 3 projesine kopyalarken:** Jackson 2 kullanıldığı için `DatasetExecutor` ve `SavedDatasets`'teki
`tools.jackson.databind.json.JsonMapper` import'unu `com.fasterxml.jackson.databind.json.JsonMapper` yapın; Jackson 2'de
`readValue` / `writeValueAsString` checked exception fırlatır, `try/catch` ile `UncheckedIOException`'a sarın.
`EntityReportsAutoConfiguration`'daki `@AutoConfigureAfter` sınıf isimleri Boot 3'te farklıdır, ama kopyalama modunda
kullanılmazlar.

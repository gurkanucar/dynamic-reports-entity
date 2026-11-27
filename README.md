# dynamic-reports-entity

Kurum içi bir veri ve otomasyon platformu: JPA entity'leri üzerinden self-servis raporlama, raporları zamanlayıp
e-postayla gönderen akışlar (n8n benzeri), onaylar, anketler ve bir yapay zekâ asistanı. Backend Spring Boot 4 (Java 25),
frontend React + shadcn/ui.

## Modüller

| Klasör | Ne | Doküman |
|---|---|---|
| [`entity-reports/`](entity-reports/) | Raporlama kütüphanesi. Başka projelere bağımlılık veya kopya olarak eklenebilir; uygulamaya bağımlı değildir | [README](entity-reports/README.md) · [Başka projeye ekleme](entity-reports/ENTEGRASYON.md) |
| [`app/`](app/) | Platform (Spring Boot): akışlar, onaylar, anketler, asistan, bildirimler, örnek iş modülleri (personel, organizasyon, eğitim, proje). `entity-reports`'u kullanır | — |
| [`ui/`](ui/) | Frontend: React 19, TypeScript, Vite, Tailwind v4, shadcn/ui, TanStack Query | [README](ui/README.md) |

## Hızlı başlangıç

**Tüm platform Docker'da** (UI + API + Kafka + Mailpit):

```bash
cp .env.example .env          # DEMO_PASSWORD zorunlu
docker compose up -d --build  # http://localhost:8088
```

**Geliştirme** (API IDE'den, UI `pnpm dev` ile):

```bash
docker compose -f docker-compose.dev.yml up -d     # PostgreSQL :5433, Mailpit :8025, S3Mock :9090, Kafka :9092
sh mvnw -pl entity-reports -am install -DskipTests # kütüphaneyi bir kez yerel repoya kur
sh mvnw -pl app spring-boot:run                    # API: http://localhost:8080 (ya da IDE'den DynamicReportsEntityApplication)
cd ui && pnpm install && pnpm dev                  # UI:  http://localhost:5173
```

Demo kullanıcılar: `admin`, `alice`, `carol`, `bob`; şifre `password` (Docker'da `.env`'deki `DEMO_PASSWORD`).

Spring profilleri:

| Profil | Veritabanı | Veri |
|---|---|---|
| (yok) | H2, bellekte | Demo + ~800k üretilmiş satır |
| `small` | H2, `./data` altında dosya | Sadece küçük demo seti |
| `postgres` | PostgreSQL, `localhost:5433` | Yok; [perf/](perf/README.md) ile yüklenir |
| `docker` | H2, volume'da | Demo + örnekler (`docker-compose.yml` kullanır) |

Build ve test: `sh mvnw verify`.

## Dokümanlar

| Doküman | İçerik |
|---|---|
| [entity-reports/README.md](entity-reports/README.md) | Raporlama kütüphanesi: annotation'lar, REST uçları, ayarlar |
| [entity-reports/ENTEGRASYON.md](entity-reports/ENTEGRASYON.md) | Kütüphaneyi başka bir projeye ekleme: jar ve kopyala-yapıştır yolları, kontrol listesi |
| [ui/README.md](ui/README.md) | Frontend kurulumu ve klasör yapısı |
| [docs/REST API Tasarım Rehberi.md](<docs/REST API Tasarım Rehberi.md>) | Yeni REST uçları için tasarım kuralları |
| [docs/deploy-dokploy.md](docs/deploy-dokploy.md) | Dokploy ile kurulum |
| [perf/README.md](perf/README.md) | PostgreSQL üzerinde performans ve yük testi |

## Diğer klasörler

| Klasör | İçerik |
|---|---|
| [`api/`](api/) | OpenAPI sözleşmeleri (akışlar, anketler, asistan) ve hata kodları |
| [`http/`](http/) | IDE'den çalıştırılabilir örnek istekler (`datasets.http`, `flows.http`) |
| [`perf/`](perf/) | Performans testi: PostgreSQL compose, veri yükleme, benchmark |
| [`scripts/`](scripts/) | `populate_demo.py`: platformu API üzerinden demo içerikle doldurur |

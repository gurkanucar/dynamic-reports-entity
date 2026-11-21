# Deploying with Dokploy

One **Compose** service runs the whole platform from `docker-compose.yml`:

| Service | What | Reachable |
|---|---|---|
| `web` | nginx: the UI, forwards `/api` to `app` | **domain → port 80** |
| `app` | the API (Spring Boot); H2 database and run files on the `app-data` volume | internal (8080) |
| `kafka` | Kafka triggers and "Kafka'ya yaz" steps | internal (9092) |
| `mailpit` | catches every e-mail the flows send | optional domain → port 8025 |

## Steps

1. Dokploy → *Projects* → new project → *Create Service* → **Compose** (type: Docker Compose).
2. *Provider*: the Git repository and branch; *Compose Path*: `./docker-compose.yml`.
3. *Environment*: paste `.env.example` and fill it in. `DEMO_PASSWORD` is required (the app refuses to start
   without it); `PUBLIC_URL` is the domain below with `https://`.
4. *Domains*: add `veri-akis.example.com` → service **web**, port **80**, HTTPS on (Let's Encrypt).
   Optional: `mail.veri-akis.example.com` → service **mailpit**, port **8025**, to read the sent mails; put it behind
   Dokploy's basic auth, the mails contain data.
5. *Deploy*. The first build takes a few minutes (Maven and pnpm download their dependencies).

No other host ports are needed. `web` also publishes 8088 for trying it without a proxy; remove the `ports:` lines
of `web` if the server's port 8088 should stay closed.

## Good to know

- **Data**: everything lives in the `app-data` volume (database `/data/db`, run files `/data/files`). Redeploys keep
  it; deleting the volume starts over with the demo data.
- **One instance**: the demo setup uses H2. For several instances use the `postgres` profile and an S3 bucket
  (`STORAGE_TYPE=s3`, `storage.s3.*`).
- **Webhooks** from other systems call `https://<domain>/api/flow-hooks/<id>`.
- **Locally**: `cp .env.example .env`, set `SESSION_COOKIE_SECURE=false`, then `docker compose up -d --build` →
  http://localhost:8088. The same `.env` is read by the app when it runs from the IDE / `mvnw spring-boot:run`
  (profile, assistant key, Kafka ...); tests ignore it.

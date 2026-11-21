# UI — Rapor & Akış

React 19 + TypeScript + Vite + Tailwind v4 + shadcn/ui (radix, lucide), TanStack Query, React Router. Package
manager: **pnpm**.

```bash
pnpm install
pnpm dev          # http://localhost:5173 — /api is proxied to http://localhost:8080 (API_URL=... to change)
pnpm typecheck && pnpm lint && pnpm build
```

The backend must run (see the `docker-compose.dev.yml`; the whole app: `docker-compose.yml`). Demo users: `admin`, `alice`, `carol`, `bob`, password
`password`. Login uses `POST /api/session`: an HttpOnly session cookie shared by all tabs; the password is never
stored in the browser. Changing requests send the CSRF token (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header).

## Structure (feature based)

```
src/
  app/                 providers (Query, theme, toasts), router, layout with sidebar
  shared/              used by every feature, imports no feature
    api/               http-client (cookies, CSRF, RFC 9457 problems → ApiError)
    components/        page header, error alert, pager
    lib/  types/       formatting, the {data, meta} envelope
  features/<feature>/  auth, reports, assistant, flows, templates
    api/               <feature>.api.ts — the only place that calls the backend
    types/             <feature>.dto.ts (requests/responses as the API sends them), <feature>.types.ts (UI models)
    queries/           use-*.ts read hooks + <feature>.keys.ts query keys
    mutations/         use-*.ts write hooks (invalidate the queries they change)
    components/        the feature's components
    pages/             routed pages
    lib/               feature logic without React (e.g. report field paths, cron presets)
  components/ui/       shadcn components (added with `pnpm dlx shadcn@latest add …`)
```

Features may use another feature's public pieces (the report builder page uses the assistant panel and the
schedule dialog); `shared/` never imports from `features/`.

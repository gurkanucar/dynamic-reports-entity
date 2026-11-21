import { useQueryClient } from "@tanstack/react-query"
import { useEffect, useRef } from "react"
import { useLocation, useNavigate } from "react-router"
import { approvalsApi } from "@/features/approvals/api/approvals.api"
import { assistantApi } from "@/features/assistant/api/assistant.api"
import type { HelpDraftDto } from "@/features/assistant/types/assistant.dto"
import type { SessionUser } from "@/features/auth/types/auth.types"
import { datasetsApi } from "@/features/datasets/api/datasets.api"
import { savedDatasetsApi } from "@/features/datasets/api/saved-datasets.api"
import { flowsApi } from "@/features/flows/api/flows.api"
import type { RunStatus } from "@/features/flows/types/flow.dto"
import { currentPage } from "@/features/help/lib/current-page"
import { surveysApi } from "@/features/surveys/api/surveys.api"
import { errorMessage } from "@/shared/api/http-client"
import { startAgentWork } from "@/shared/lib/agent-activity"
import { agentNotice, highlight, type AgentFocus } from "@/shared/lib/agent-highlight"

/**
 * WebMCP (https://developer.chrome.com/docs/ai/webmcp): the page offers tools to an AI agent in the user's browser,
 * so the agent works through the same APIs and permissions as the signed-in user instead of clicking around. It is
 * purely client-side: nothing here exists on the server, and browsers without WebMCP (`document.modelContext`) simply
 * skip it. Consequential tools (starting a flow, deciding an approval) are marked so the browser asks the user first.
 */

interface ModelContextTool {
  name: string
  description: string
  inputSchema: Record<string, unknown>
  annotations?: { readOnlyHint?: boolean; untrustedContentHint?: boolean; consequentialHint?: boolean }
  execute: (input: Record<string, unknown>) => Promise<unknown>
}

interface ModelContext {
  registerTool: (tool: ModelContextTool, options?: { signal?: AbortSignal }) => unknown
  /** Early previews only: removed in favour of aborting the signal. */
  unregisterTool?: (name: string) => void
}

/** document.modelContext; navigator.modelContext in early Chrome builds (deprecated since Chromium 150). */
function modelContext(): ModelContext | null {
  const fromDocument = (document as unknown as { modelContext?: ModelContext }).modelContext
  const fromNavigator = (navigator as unknown as { modelContext?: ModelContext }).modelContext
  return fromDocument ?? fromNavigator ?? null
}

const PAGES: { path: string; label: string; admin?: boolean }[] = [
  { path: "/", label: "Pano: bekleyen onaylar, son çalışmalar" },
  { path: "/approvals", label: "Onaylarım: kararımı bekleyen talepler" },
  { path: "/datasets", label: "Veri setleri: kayıtlı raporlar" },
  { path: "/datasets/new", label: "Yeni veri seti (asistanlı)" },
  { path: "/flows", label: "Akışlar" },
  { path: "/flows/new", label: "Yeni akış (asistanlı)" },
  { path: "/flows/import", label: "Akış içe aktar (JSON dosyasından, bağımlılıkları eşleştirerek)" },
  { path: "/runs", label: "Çalışmalar: akışların çalışma geçmişi" },
  { path: "/surveys", label: "Anketler" },
  { path: "/surveys/new", label: "Yeni anket (asistanlı)" },
  { path: "/templates", label: "Mail şablonları" },
  { path: "/approval-groups", label: "Onay grupları" },
  { path: "/admin/personnel", label: "Personel", admin: true },
  { path: "/admin/departments", label: "Departmanlar", admin: true },
  { path: "/admin/projects", label: "Projeler", admin: true },
  { path: "/admin/trainings", label: "Eğitimler", admin: true },
  { path: "/admin/training-records", label: "Eğitim kayıtları", admin: true },
  { path: "/admin/skills", label: "Beceriler", admin: true },
]
const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
const DETAIL_PAGES = [new RegExp(`^/(datasets|templates)/${UUID}$`), new RegExp(`^/(flows|surveys)/${UUID}(/edit)?$`),
  new RegExp(`^/s/${UUID}$`), /^\/templates\/new$/]
const RUN_STATUSES: RunStatus[] = ["queued", "running", "completed", "failed", "cancelled"]

/** What the glow says while a tool runs. */
const WORKING: Record<string, string> = {
  get_current_page: "sayfaya bakıyor", list_pages: "sayfalara bakıyor", open_page: "sayfa açıyor",
  point_at: "gösteriyor", ask_platform_help: "yardıma soruyor", search_flows: "akışları arıyor",
  start_flow: "akışı başlatıyor", list_runs: "çalışmalara bakıyor", list_saved_datasets: "veri setlerine bakıyor",
  run_saved_dataset: "veri setini çalıştırıyor", list_my_approvals: "onaylara bakıyor", show_approval: "talebi açıyor",
  decide_approval: "karar veriyor", list_surveys: "anketlere bakıyor",
}

/**
 * The page an agent means, in whatever form it says it: "/runs", "runs", "http://host/runs/", or a page's name
 * ("Çalışmalar"). Only paths of this app come out; anything else is null.
 */
function resolvePage(raw: string, pages: { path: string; label: string }[]): string | null {
  let value = raw.trim()
  if (!value) return null
  try {
    const url = new URL(value, window.location.origin)
    if (url.origin !== window.location.origin) return null
    if (/^[a-z][a-z0-9+.-]*:/i.test(value) || value.startsWith("/") || /^[\w-]+(\/|$)/.test(value)) value = url.pathname
  } catch {
    // not a URL: maybe a name
  }
  const path = ("/" + value.replace(/^\/+/, "")).replace(/\/+$/, "") || "/"
  if (pages.some((page) => page.path === path) || DETAIL_PAGES.some((page) => page.test(path))) return path
  const name = raw.trim().toLocaleLowerCase("tr")
  return pages.find((page) => page.label.toLocaleLowerCase("tr").startsWith(name)
    || page.label.toLocaleLowerCase("tr").split(":")[0] === name)?.path ?? null
}

const text = (value: unknown, max = 200) => (typeof value === "string" ? value.trim().slice(0, max) : "")
const int = (value: unknown, min: number, max: number, fallback: number) =>
  typeof value === "number" && Number.isFinite(value) ? Math.min(max, Math.max(min, Math.trunc(value))) : fallback
const schema = (properties: Record<string, unknown> = {}, required: string[] = []) =>
  ({ type: "object", properties, required })

/** Runs a tool; a failure comes back as {error} (thrown messages do not reach the agent). */
const safely = (run: (input: Record<string, unknown>) => Promise<unknown>) =>
  async (input: Record<string, unknown>) => {
    try {
      return await run(input ?? {})
    } catch (error) {
      return { error: errorMessage(error) }
    }
  }

/** What the tools may do in the tab: move to a page, refresh what is cached, read where the user is. */
interface Page {
  go: (path: string) => void
  refresh: () => Promise<void>
  path: () => string
}

const SHOW = {
  show: { type: "boolean", description: "Also show it to the user: open the page and highlight it (default true)." },
}
const TARGETS = ["page", "flows", "runs", "approvals", "datasets", "dataset-results", "surveys", "open-surveys", "templates"]

function tools(user: SessionUser, page: Page): ModelContextTool[] {
  const admin = user.authorities.includes("PLATFORM_ADMIN")
  const pages = PAGES.filter((item) => admin || !item.admin)
  /** Opens the page (when the agent did not say show: false), tells the user, and outlines the area or the row. */
  const present = async (input: Record<string, unknown>, path: string, focus: AgentFocus, notice: string) => {
    if (input.show === false) return { shown: false }
    if (page.path() + window.location.search !== path) page.go(path)
    agentNotice(notice)
    return { shown: await highlight(focus) }
  }
  return [
    {
      name: "get_current_page",
      description: "The page the user is looking at in the data & flow platform, and who is signed in.",
      inputSchema: schema(),
      annotations: { readOnlyHint: true },
      execute: safely(async () => ({ ...currentPage(page.path()), user: user.username, admin })),
    },
    {
      name: "list_pages",
      description: "Pages of the platform the user can open, with what each is for.",
      inputSchema: schema(),
      annotations: { readOnlyHint: true },
      execute: safely(async () => pages),
    },
    {
      name: "open_page",
      description: "Opens a page of the platform for the user and shows it with a highlight. Always use this instead "
        + "of changing the address bar: it keeps the user's session and shows them what you did. Accepts a path from "
        + "list_pages (/runs), a full address of this site, a page name (\"Çalışmalar\") or a detail page "
        + "(/datasets/<id>, /flows/<id>, /flows/<id>/edit, /surveys/<id>, /surveys/<id>/edit, /templates/<id>; ids are UUIDs).",
      inputSchema: schema({ path: { type: "string", description: "e.g. /runs, Çalışmalar or /flows/<id>" } }, ["path"]),
      execute: safely(async (input) => {
        const target = resolvePage(text(input.path, 300).split(/[?#]/)[0], pages)
        if (!target) return { error: `Unknown page ${text(input.path)}; call list_pages for the pages that exist.` }
        page.go(target)
        agentNotice("Asistan bir sayfa açtı", target)
        return { opened: target, shown: await highlight({ target: "page", label: "Asistan burada" }) }
      }),
    },
    {
      name: "point_at",
      description: "Draws the user's attention to an area of the current page (outline and a short label for a few "
        + `seconds), e.g. before explaining it. Areas: ${TARGETS.join(", ")}; or a row by record id.`,
      inputSchema: schema({
        target: { type: "string", enum: TARGETS },
        rowId: { type: "string", description: "record id of a row in that area (optional)" },
        label: { type: "string", description: "a few words, shown on the outline" },
      }, ["target"]),
      annotations: { readOnlyHint: true },
      execute: safely(async (input) => ({ shown: await highlight({ target: text(input.target, 40),
        row: text(input.rowId, 36) || undefined, label: text(input.label, 60) || "Asistan" }) })),
    },
    {
      name: "ask_platform_help",
      description: "Asks the platform's help assistant how to do something in the UI (pages, buttons, steps). "
        + "Answers come with links to the right pages.",
      inputSchema: schema({ question: { type: "string" } }, ["question"]),
      annotations: { readOnlyHint: true },
      execute: safely(async (input) => {
        const question = text(input.question, 2000)
        if (!question) return { error: "question is required" }
        const created = await assistantApi.create("help", {
          messages: [{ role: "user", content: question }], current: currentPage(page.path()) })
        const done = await assistantApi.waitFor("help", created.id)
        if (done.status === "failed" || !done.reply) return { error: done.error?.message ?? "No answer" }
        const draft = done.reply.draft as HelpDraftDto
        return { answer: done.reply.message, links: draft.links, handoff: draft.handoff }
      }),
    },
    {
      name: "search_flows",
      description: "Lists the flows (automations) the user can see, newest first; optional text search. Shows the "
        + "list to the user with the search filled in.",
      inputSchema: schema({ query: { type: "string" }, page: { type: "integer", minimum: 1 }, ...SHOW }),
      annotations: { readOnlyHint: true },
      execute: safely(async (input) => {
        const query = text(input.query)
        const result = await flowsApi.list(int(input.page, 1, 1000, 1), 20, { q: query || undefined })
        const shown = await present(input, `/flows${query ? `?q=${encodeURIComponent(query)}` : ""}`,
          { target: "flows", row: result.data.length === 1 ? result.data[0].id : undefined, label: "Asistan: akışlar" },
          query ? `Asistan “${query}” akışlarını arıyor` : "Asistan akışlara bakıyor")
        return {
          ...shown,
          total: result.meta?.totalItems ?? null,
          flows: result.data.map((flow) => ({ id: flow.id, name: flow.name, description: flow.description,
            owner: flow.owner.username, steps: flow.stepCount, updatedAt: flow.updatedAt })),
        }
      }),
    },
    {
      name: "start_flow",
      description: "Starts a run of a flow now, as the signed-in user, and shows the new run on the flow's page.",
      inputSchema: schema({ flowId: { type: "string", description: "flow id (UUID) from search_flows" }, ...SHOW },
        ["flowId"]),
      annotations: { consequentialHint: true },
      execute: safely(async (input) => {
        const run = await flowsApi.start(text(input.flowId, 36))
        await page.refresh()
        const shown = await present(input, `/flows/${run.flow.id}`,
          { target: "runs", row: run.id, label: "Asistan: yeni çalışma" }, `Asistan “${run.flow.name}” akışını başlattı`)
        return { ...shown, runId: run.id, status: run.status, flow: run.flow.name }
      }),
    },
    {
      name: "list_runs",
      description: "Recent flow runs with status (queued, running, completed, failed, cancelled) and errors.",
      inputSchema: schema({
        status: { type: "array", items: { type: "string", enum: RUN_STATUSES } },
        query: { type: "string", description: "flow name" },
        ...SHOW,
      }),
      annotations: { readOnlyHint: true },
      execute: safely(async (input) => {
        const statuses = (Array.isArray(input.status) ? input.status : [])
          .filter((status): status is RunStatus => RUN_STATUSES.includes(status as RunStatus))
        const query = text(input.query)
        const result = await flowsApi.runs(statuses, 1, 20, { q: query || undefined })
        const shown = await present(input, `/runs${query ? `?q=${encodeURIComponent(query)}` : ""}`,
          { target: "runs", label: "Asistan: çalışmalar" }, "Asistan çalışmalara bakıyor")
        return {
          ...shown,
          runs: result.data.map((run) => ({ id: run.id, flow: run.flow.name, flowId: run.flow.id, status: run.status,
            trigger: run.trigger, createdAt: run.createdAt, finishedAt: run.finishedAt, error: run.error?.message ?? null })),
        }
      }),
    },
    {
      name: "list_saved_datasets",
      description: "Saved datasets (reports) the user can run: name, label, description and record type.",
      inputSchema: schema({ ...SHOW }),
      annotations: { readOnlyHint: true },
      execute: safely(async (input) => {
        const datasets = (await savedDatasetsApi.list()).map((dataset) => ({ id: dataset.id, name: dataset.name,
          label: dataset.label, description: dataset.description, root: dataset.definition.root }))
        const shown = await present(input, "/datasets", { target: "datasets", label: "Asistan: veri setleri" },
          "Asistan veri setlerine bakıyor")
        return { ...shown, datasets }
      }),
    },
    {
      name: "run_saved_dataset",
      description: "Runs a saved dataset and returns one page of rows (only what the user may see); opens it so the "
        + "user sees the same result.",
      inputSchema: schema({
        name: { type: "string" },
        page: { type: "integer", minimum: 1, description: "1 = first page" },
        size: { type: "integer", minimum: 1, maximum: 50 },
        ...SHOW,
      }, ["name"]),
      annotations: { readOnlyHint: true, untrustedContentHint: true },
      execute: safely(async (input) => {
        const name = text(input.name, 80)
        const saved = (await savedDatasetsApi.list()).find((dataset) => dataset.name === name)
        if (!saved) return { error: `No saved dataset ${name}; see list_saved_datasets.` }
        const result = await datasetsApi.run({ definition: saved.definition,
          page: int(input.page, 1, 100_000, 1) - 1, size: int(input.size, 1, 50, 20) })
        const shown = await present(input, `/datasets/${saved.id}`,
          { target: "dataset-results", label: `Asistan: ${saved.label ?? saved.name}` },
          `Asistan “${saved.label ?? saved.name}” veri setini çalıştırdı`)
        return { ...shown, columns: result.columns.map((column) => column.label), rows: result.rows,
          total: result.total, hasNext: result.hasNext }
      }),
    },
    {
      name: "list_my_approvals",
      description: "Approval requests of the user's groups; by default the ones waiting for a decision.",
      inputSchema: schema({ pendingOnly: { type: "boolean" }, ...SHOW }),
      annotations: { readOnlyHint: true, untrustedContentHint: true },
      execute: safely(async (input) => {
        const result = await approvalsApi.requests("mine", input.pendingOnly === false ? [] : ["pending"], 1, 20)
        const shown = await present(input, "/approvals", { target: "approvals", label: "Asistan: onaylar" },
          "Asistan onay taleplerine bakıyor")
        return {
          ...shown,
          requests: result.data.map((request) => ({ id: request.id, title: request.title,
            description: request.description, group: request.group.name, requestedBy: request.requestedBy,
            status: request.status, dueAt: request.dueAt, canDecide: request.canDecide, whyNot: request.decisionBlocker })),
        }
      }),
    },
    {
      name: "show_approval",
      description: "Opens one approval request in the user's view (details, decision buttons) without deciding.",
      inputSchema: schema({ requestId: { type: "string" } }, ["requestId"]),
      annotations: { readOnlyHint: true },
      execute: safely(async (input) => {
        const id = text(input.requestId, 36)
        page.go(`/approvals?request=${encodeURIComponent(id)}`)
        agentNotice("Asistan bir onay talebini açtı")
        return { shown: await highlight({ target: "approval-dialog", label: "Asistan: bu talep" }) }
      }),
    },
    {
      name: "decide_approval",
      description: "Approves or rejects an approval request as the signed-in user (only when canDecide is true). The "
        + "request is shown to the user while it is decided.",
      inputSchema: schema({
        requestId: { type: "string", description: "request id (UUID) from list_my_approvals" },
        decision: { type: "string", enum: ["approve", "reject"] },
        comment: { type: "string" },
      }, ["requestId", "decision"]),
      annotations: { consequentialHint: true },
      execute: safely(async (input) => {
        if (input.decision !== "approve" && input.decision !== "reject") return { error: "decision: approve or reject" }
        const id = text(input.requestId, 36)
        page.go(`/approvals?request=${encodeURIComponent(id)}`)
        const decided = await approvalsApi.decide(id, input.decision === "approve",
          { comment: text(input.comment, 1000) || null })
        await page.refresh()
        agentNotice(input.decision === "approve" ? "Asistan talebi onayladı" : "Asistan talebi reddetti", decided.title)
        await highlight({ target: "approval-dialog", label: input.decision === "approve" ? "Asistan: onaylandı" : "Asistan: reddedildi" })
        return { id: decided.id, status: decided.status }
      }),
    },
    {
      name: "list_surveys",
      description: "Surveys: 'mine' (that I own) or 'open' (that I can answer now), with response counts.",
      inputSchema: schema({ scope: { type: "string", enum: ["mine", "open"] }, ...SHOW }),
      annotations: { readOnlyHint: true },
      execute: safely(async (input) => {
        const open = input.scope === "open"
        const result = await surveysApi.list(open ? "open" : "mine", 1, 50)
        const shown = await present(input, "/surveys", { target: open ? "open-surveys" : "surveys",
          label: "Asistan: anketler" }, "Asistan anketlere bakıyor")
        return {
          ...shown,
          surveys: result.data.map((survey) => ({ id: survey.id, title: survey.title, status: survey.status,
            questions: survey.questionCount, responses: survey.responseCount, answered: survey.answered,
            fillAt: `/s/${survey.id}`, resultsAt: `/surveys/${survey.id}` })),
        }
      }),
    },
  ]
}

/** Offers the tools while someone is signed in; they go away on sign-out. Does nothing without WebMCP. */
export function useWebMcp(user: SessionUser | null | undefined) {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  // the tools read the latest location and navigate without being registered again
  const latest = useRef({ navigate, path: location.pathname })
  useEffect(() => { latest.current = { navigate, path: location.pathname } }, [navigate, location.pathname])

  useEffect(() => {
    const context = modelContext()
    if (!context || !user) return
    const controller = new AbortController()
    const registered = tools(user, {
      go: (path) => latest.current.navigate(path),
      refresh: () => queryClient.invalidateQueries(),
      path: () => latest.current.path,
    })
    for (const plain of registered) {
      // while a tool runs the screen glows, so the user sees the agent at work
      const tool: ModelContextTool = { ...plain, execute: async (input) => {
        console.info(`[WebMCP] ${plain.name}`, input)   // tells whether an agent uses the tools or drives the page itself
        const done = startAgentWork(WORKING[plain.name] ?? plain.name)
        try {
          return await plain.execute(input)
        } finally {
          done()
        }
      } }
      Promise.resolve()
        .then(() => context.registerTool(tool, { signal: controller.signal }))
        .catch((error) => {
          // unregistered on purpose (sign-out, React re-running the effect in development): nothing to report
          if (!controller.signal.aborted) console.warn(`WebMCP: ${tool.name} not registered`, error)
        })
    }
    return () => {
      controller.abort()
      registered.forEach((tool) => { try { context.unregisterTool?.(tool.name) } catch { /* already gone */ } })
    }
  }, [user, queryClient])
}

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
const DETAIL_PAGES = [new RegExp(`^/datasets/${UUID}$`), new RegExp(`^/(flows|surveys)/${UUID}(/edit)?$`), new RegExp(`^/s/${UUID}$`)]
const RUN_STATUSES: RunStatus[] = ["queued", "running", "completed", "failed", "cancelled"]

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

function tools(user: SessionUser, go: (path: string) => void, path: () => string): ModelContextTool[] {
  const admin = user.authorities.includes("PLATFORM_ADMIN")
  const pages = PAGES.filter((page) => admin || !page.admin)
  return [
    {
      name: "get_current_page",
      description: "The page the user is looking at in the data & flow platform, and who is signed in.",
      inputSchema: schema(),
      annotations: { readOnlyHint: true },
      execute: safely(async () => ({ ...currentPage(path()), user: user.username, admin })),
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
      description: "Opens a page of the platform in this tab: a path from list_pages or a detail page "
        + "(/datasets/<id>, /flows/<id>, /flows/<id>/edit, /surveys/<id>, /surveys/<id>/edit; ids are UUIDs).",
      inputSchema: schema({ path: { type: "string", description: "e.g. /runs or /flows/12" } }, ["path"]),
      execute: safely(async (input) => {
        const target = text(input.path).split(/[?#]/)[0]
        const known = pages.some((page) => page.path === target) || DETAIL_PAGES.some((page) => page.test(target))
        if (!known) return { error: `Unknown page ${target}; use list_pages.` }
        go(target)
        return { opened: target }
      }),
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
          messages: [{ role: "user", content: question }], current: currentPage(path()) })
        const done = await assistantApi.waitFor("help", created.id)
        if (done.status === "failed" || !done.reply) return { error: done.error?.message ?? "No answer" }
        const draft = done.reply.draft as HelpDraftDto
        return { answer: done.reply.message, links: draft.links, handoff: draft.handoff }
      }),
    },
    {
      name: "search_flows",
      description: "Lists the flows (automations) the user can see, newest first; optional text search.",
      inputSchema: schema({ query: { type: "string" }, page: { type: "integer", minimum: 1 } }),
      annotations: { readOnlyHint: true },
      execute: safely(async (input) => {
        const result = await flowsApi.list(int(input.page, 1, 1000, 1), 20, { q: text(input.query) || undefined })
        return {
          total: result.meta?.totalItems ?? null,
          flows: result.data.map((flow) => ({ id: flow.id, name: flow.name, description: flow.description,
            owner: flow.owner.username, steps: flow.stepCount, updatedAt: flow.updatedAt })),
        }
      }),
    },
    {
      name: "start_flow",
      description: "Starts a run of a flow now, as the signed-in user. Returns the run id; see list_runs.",
      inputSchema: schema({ flowId: { type: "string", description: "flow id (UUID) from search_flows" } }, ["flowId"]),
      annotations: { consequentialHint: true },
      execute: safely(async (input) => {
        const run = await flowsApi.start(text(input.flowId, 36))
        return { runId: run.id, status: run.status, flow: run.flow.name }
      }),
    },
    {
      name: "list_runs",
      description: "Recent flow runs with status (queued, running, completed, failed, cancelled) and errors.",
      inputSchema: schema({
        status: { type: "array", items: { type: "string", enum: RUN_STATUSES } },
        query: { type: "string", description: "flow name" },
      }),
      annotations: { readOnlyHint: true },
      execute: safely(async (input) => {
        const statuses = (Array.isArray(input.status) ? input.status : [])
          .filter((status): status is RunStatus => RUN_STATUSES.includes(status as RunStatus))
        const result = await flowsApi.runs(statuses, 1, 20, { q: text(input.query) || undefined })
        return result.data.map((run) => ({ id: run.id, flow: run.flow.name, flowId: run.flow.id, status: run.status,
          trigger: run.trigger, createdAt: run.createdAt, finishedAt: run.finishedAt, error: run.error?.message ?? null }))
      }),
    },
    {
      name: "list_saved_datasets",
      description: "Saved datasets (reports) the user can run: name, label, description and record type.",
      inputSchema: schema(),
      annotations: { readOnlyHint: true },
      execute: safely(async () => (await savedDatasetsApi.list()).map((dataset) => ({ id: dataset.id, name: dataset.name,
        label: dataset.label, description: dataset.description, root: dataset.definition.root }))),
    },
    {
      name: "run_saved_dataset",
      description: "Runs a saved dataset and returns one page of rows (only what the user may see).",
      inputSchema: schema({
        name: { type: "string" },
        page: { type: "integer", minimum: 1, description: "1 = first page" },
        size: { type: "integer", minimum: 1, maximum: 50 },
      }, ["name"]),
      annotations: { readOnlyHint: true, untrustedContentHint: true },
      execute: safely(async (input) => {
        const name = text(input.name, 80)
        const saved = (await savedDatasetsApi.list()).find((dataset) => dataset.name === name)
        if (!saved) return { error: `No saved dataset ${name}; see list_saved_datasets.` }
        const result = await datasetsApi.run({ definition: saved.definition,
          page: int(input.page, 1, 100_000, 1) - 1, size: int(input.size, 1, 50, 20) })
        return { columns: result.columns.map((column) => column.label), rows: result.rows, total: result.total,
          hasNext: result.hasNext }
      }),
    },
    {
      name: "list_my_approvals",
      description: "Approval requests of the user's groups; by default the ones waiting for a decision.",
      inputSchema: schema({ pendingOnly: { type: "boolean" } }),
      annotations: { readOnlyHint: true, untrustedContentHint: true },
      execute: safely(async (input) => {
        const result = await approvalsApi.requests("mine", input.pendingOnly === false ? [] : ["pending"], 1, 20)
        return result.data.map((request) => ({ id: request.id, title: request.title, description: request.description,
          group: request.group.name, requestedBy: request.requestedBy, status: request.status, dueAt: request.dueAt,
          canDecide: request.canDecide, whyNot: request.decisionBlocker }))
      }),
    },
    {
      name: "decide_approval",
      description: "Approves or rejects an approval request as the signed-in user (only when canDecide is true).",
      inputSchema: schema({
        requestId: { type: "string", description: "request id (UUID) from list_my_approvals" },
        decision: { type: "string", enum: ["approve", "reject"] },
        comment: { type: "string" },
      }, ["requestId", "decision"]),
      annotations: { consequentialHint: true },
      execute: safely(async (input) => {
        if (input.decision !== "approve" && input.decision !== "reject") return { error: "decision: approve or reject" }
        const decided = await approvalsApi.decide(text(input.requestId, 36),
          input.decision === "approve", { comment: text(input.comment, 1000) || null })
        return { id: decided.id, status: decided.status }
      }),
    },
    {
      name: "list_surveys",
      description: "Surveys: 'mine' (that I own) or 'open' (that I can answer now), with response counts.",
      inputSchema: schema({ scope: { type: "string", enum: ["mine", "open"] } }),
      annotations: { readOnlyHint: true },
      execute: safely(async (input) => {
        const result = await surveysApi.list(input.scope === "open" ? "open" : "mine", 1, 50)
        return result.data.map((survey) => ({ id: survey.id, title: survey.title, status: survey.status,
          questions: survey.questionCount, responses: survey.responseCount, answered: survey.answered,
          fillAt: `/s/${survey.id}`, resultsAt: `/surveys/${survey.id}` }))
      }),
    },
  ]
}

/** Offers the tools while someone is signed in; they go away on sign-out. Does nothing without WebMCP. */
export function useWebMcp(user: SessionUser | null | undefined) {
  const navigate = useNavigate()
  const location = useLocation()
  // the tools read the latest location and navigate without being registered again
  const latest = useRef({ navigate, path: location.pathname })
  useEffect(() => { latest.current = { navigate, path: location.pathname } }, [navigate, location.pathname])

  useEffect(() => {
    const context = modelContext()
    if (!context || !user) return
    const controller = new AbortController()
    const registered = tools(user, (path) => latest.current.navigate(path), () => latest.current.path)
    for (const tool of registered) {
      Promise.resolve()
        .then(() => context.registerTool(tool, { signal: controller.signal }))
        .catch((error) => console.warn(`WebMCP: ${tool.name} not registered`, error))
    }
    return () => {
      controller.abort()
      registered.forEach((tool) => { try { context.unregisterTool?.(tool.name) } catch { /* already gone */ } })
    }
  }, [user])
}

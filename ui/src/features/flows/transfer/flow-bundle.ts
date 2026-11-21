import { approvalsApi } from "@/features/approvals/api/approvals.api"
import type { GroupMemberDto } from "@/features/approvals/types/approval.dto"
import { savedDatasetsApi } from "@/features/datasets/api/saved-datasets.api"
import type { DatasetDefinition } from "@/features/datasets/types/dataset.types"
import { surveysApi } from "@/features/surveys/api/surveys.api"
import type { SurveyRequest } from "@/features/surveys/types/survey.dto"
import { templatesApi } from "@/features/templates/api/templates.api"
import { ApiError } from "@/shared/api/http-client"
import { flowsApi } from "../api/flows.api"
import type { FlowEdgeDto, FlowStepDto, JsonLogicRule, PositionDto, TriggerType } from "../types/flow.dto"

/**
 * A flow as a JSON file, to move it between environments (test → production) or keep a copy: the flow, its
 * schedules and triggers, and everything its steps use from other modules (mail templates, approval groups, surveys,
 * saved datasets), so the importing side can reuse what it already has or create what is missing. Ids inside are the
 * exporting side's; the import maps them. Webhook secrets never leave: imported webhooks get new ones.
 */
export const BUNDLE_FORMAT = "veri-akis/flow"
export const BUNDLE_VERSION = 1

export interface FlowBundle {
  format: typeof BUNDLE_FORMAT
  version: number
  exportedAt: string
  exportedBy: string | null
  flow: { name: string; description: string | null; steps: FlowStepDto[]; edges: FlowEdgeDto[]; layout: Record<string, PositionDto> }
  schedules: { cron: string; timeZone: string; enabled: boolean }[]
  triggers: { type: TriggerType; enabled: boolean; topic: string | null; filter: JsonLogicRule | null }[]
  resources: {
    mailTemplates: { id: string; name: string; description: string | null; subject: string; bodyHtml: string }[]
    approvalGroups: { id: string; name: string; description: string | null; members: GroupMemberDto[] }[]
    surveys: ({ id: string } & SurveyRequest)[]
    savedDatasets: { name: string; label: string | null; description: string | null; definition: DatasetDefinition }[]
  }
  /** What could not be included (e.g. a survey someone else owns): the import asks for a replacement. */
  missing: { kind: DependencyKind; id: string; reason: string }[]
}

export type DependencyKind = "mail-template" | "approval-group" | "survey" | "saved-dataset"

export const KIND_LABELS: Record<DependencyKind, string> = {
  "mail-template": "Mail şablonu",
  "approval-group": "Onay grubu",
  "survey": "Anket",
  "saved-dataset": "Veri seti",
}

const SURVEY_TOPIC = "survey:"

type Step = FlowStepDto & { templateId?: string; groupId?: string; surveyId?: string; datasetName?: string | null }

/** What the steps and triggers use, by kind: ids (names for saved datasets). */
function usedBy(steps: Step[], triggers: { topic: string | null }[]) {
  const used = { "mail-template": new Set<string>(), "approval-group": new Set<string>(), "survey": new Set<string>(),
    "saved-dataset": new Set<string>() } satisfies Record<DependencyKind, Set<string>>
  for (const step of steps) {
    if (step.type === "mail" && step.templateId) used["mail-template"].add(step.templateId)
    if (step.type === "approval" && step.groupId) used["approval-group"].add(step.groupId)
    if (step.type === "survey" && step.surveyId) used.survey.add(step.surveyId)
    if (step.type === "dataset" && step.datasetName) used["saved-dataset"].add(step.datasetName)
  }
  for (const trigger of triggers) {
    if (trigger.topic?.startsWith(SURVEY_TOPIC)) used.survey.add(trigger.topic.slice(SURVEY_TOPIC.length))
  }
  return used
}

// ---- export ---------------------------------------------------------------------------------------------------

export async function exportFlow(flowId: string, exportedBy: string | null): Promise<FlowBundle> {
  const [flow, schedules, triggers] = await Promise.all([flowsApi.get(flowId), flowsApi.schedules(flowId),
    flowsApi.triggers(flowId)])
  const used = usedBy(flow.steps as Step[], triggers)
  const missing: FlowBundle["missing"] = []
  const keep = async <T>(kind: DependencyKind, id: string, load: () => Promise<T | undefined>): Promise<T | null> => {
    try {
      const found = await load()
      if (found) return found
      missing.push({ kind, id, reason: "bulunamadı" })
    } catch (error) {
      missing.push({ kind, id, reason: error instanceof ApiError && error.status === 404 ? "erişim yok ya da silinmiş" : String(error) })
    }
    return null
  }
  const groups = used["approval-group"].size ? await approvalsApi.groups() : []
  const datasets = used["saved-dataset"].size ? await savedDatasetsApi.list() : []
  const present = <T>(items: (T | null)[]) => items.filter((item): item is T => item !== null)

  return {
    format: BUNDLE_FORMAT,
    version: BUNDLE_VERSION,
    exportedAt: new Date().toISOString(),
    exportedBy,
    flow: { name: flow.name, description: flow.description, steps: flow.steps, edges: flow.edges, layout: flow.layout },
    schedules: schedules.map((schedule) => ({ cron: schedule.cron, timeZone: schedule.timeZone, enabled: schedule.enabled })),
    triggers: triggers.map((trigger) => ({ type: trigger.type, enabled: trigger.enabled, topic: trigger.topic,
      filter: trigger.filter })),
    resources: {
      mailTemplates: present(await Promise.all([...used["mail-template"]].map((id) => keep("mail-template", id, async () => {
        const template = await templatesApi.get(id)
        return { id, name: template.name, description: template.description, subject: template.subject,
          bodyHtml: template.bodyHtml }
      })))),
      approvalGroups: present(await Promise.all([...used["approval-group"]].map((id) => keep("approval-group", id, async () => {
        const group = groups.find((item) => item.id === id)
        return group && { id, name: group.name, description: group.description, members: group.members }
      })))),
      surveys: present(await Promise.all([...used.survey].map((id) => keep("survey", id, async () => {
        const survey = await surveysApi.get(id)
        return { id, title: survey.title, description: survey.description, status: survey.status,
          definition: survey.definition, anonymous: survey.anonymous, oneResponsePerUser: survey.oneResponsePerUser,
          closesAt: survey.closesAt ?? null, settings: survey.settings }
      })))),
      savedDatasets: present(await Promise.all([...used["saved-dataset"]].map((name) => keep("saved-dataset", name, async () => {
        const dataset = datasets.find((item) => item.name === name)
        return dataset && { name, label: dataset.label, description: dataset.description, definition: dataset.definition }
      })))),
    },
    missing,
  }
}

export function downloadBundle(bundle: FlowBundle) {
  const slug = bundle.flow.name.toLocaleLowerCase("tr-TR").normalize("NFD").replace(/[̀-ͯ]/g, "")
    .replace(/ı/g, "i").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 60) || "akis"
  const url = URL.createObjectURL(new Blob([JSON.stringify(bundle, null, 2)], { type: "application/json" }))
  const link = document.createElement("a")
  link.href = url
  link.download = `akis-${slug}.json`
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

/** Reads a file someone picked; anything that is not a bundle of a known version is refused with a reason. */
export function parseBundle(text: string): FlowBundle {
  let value: unknown
  try {
    value = JSON.parse(text)
  } catch {
    throw new Error("Dosya geçerli bir JSON değil.")
  }
  const bundle = value as Partial<FlowBundle>
  if (bundle?.format !== BUNDLE_FORMAT) throw new Error("Bu dosya bir akış dışa aktarımı değil.")
  if (typeof bundle.version !== "number" || bundle.version > BUNDLE_VERSION) {
    throw new Error(`Bu dosyanın sürümü (${bundle.version}) desteklenmiyor; platform güncellenmeli.`)
  }
  if (!bundle.flow || !Array.isArray(bundle.flow.steps)) throw new Error("Dosyada akış adımları yok.")
  return {
    ...bundle,
    schedules: bundle.schedules ?? [],
    triggers: bundle.triggers ?? [],
    resources: { mailTemplates: [], approvalGroups: [], surveys: [], savedDatasets: [], ...bundle.resources },
    missing: bundle.missing ?? [],
  } as FlowBundle
}

// ---- plan -----------------------------------------------------------------------------------------------------

export type DependencyStatus = "same" | "different" | "missing" | "not-exported"

/** One thing the flow uses, and what this environment has for it. */
export interface Dependency {
  key: string
  kind: DependencyKind
  /** The exporting side's id (a name for saved datasets). */
  ref: string
  name: string
  status: DependencyStatus
  /** Same-named records here, the first one is the best match. */
  candidates: { id: string; name: string }[]
  /** Everything of this kind here, for "use another one". */
  options: { id: string; name: string }[]
  /** May this user create one (approval groups: admins only)? */
  creatable: boolean
}

export type Decision = { action: "use"; id: string } | { action: "create"; name: string }

const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b)

/** Compares what the bundle brings with what is here: same content, same name but different content, or missing. */
export async function planImport(bundle: FlowBundle, admin: boolean): Promise<Dependency[]> {
  const used = usedBy(bundle.flow.steps as Step[], bundle.triggers)
  const [templates, groups, datasets, surveys] = await Promise.all([
    used["mail-template"].size ? templatesApi.list(1, 100).then((page) => page.data) : [],
    used["approval-group"].size ? approvalsApi.groups() : [],
    used["saved-dataset"].size ? savedDatasetsApi.list() : [],
    used.survey.size ? surveysApi.list("mine", 1, 100).then((page) => page.data) : [],
  ])
  const dependencies: Dependency[] = []
  const add = (kind: DependencyKind, ref: string, name: string, status: DependencyStatus,
    candidates: Dependency["candidates"], options: Dependency["options"]) =>
    dependencies.push({ key: `${kind}:${ref}`, kind, ref, name, status, candidates, options,
      creatable: status !== "not-exported" && (kind !== "approval-group" || admin) })
  const notExported = (kind: DependencyKind, ref: string) =>
    bundle.missing.find((item) => item.kind === kind && item.id === ref)

  for (const ref of used["mail-template"]) {
    const exported = bundle.resources.mailTemplates.find((item) => item.id === ref)
    const options = templates.map((item) => ({ id: item.id, name: item.name }))
    if (!exported) { add("mail-template", ref, notExported("mail-template", ref)?.reason ?? ref, "not-exported", [], options); continue }
    const named = templates.filter((item) => item.name === exported.name)
    let status: DependencyStatus = named.length ? "different" : "missing"
    for (const candidate of named) {
      const full = await templatesApi.get(candidate.id)
      if (full.subject === exported.subject && full.bodyHtml === exported.bodyHtml) status = "same"
    }
    add("mail-template", ref, exported.name, status, named.map((item) => ({ id: item.id, name: item.name })), options)
  }
  for (const ref of used["approval-group"]) {
    const exported = bundle.resources.approvalGroups.find((item) => item.id === ref)
    const options = groups.map((item) => ({ id: item.id, name: item.name }))
    if (!exported) { add("approval-group", ref, ref, "not-exported", [], options); continue }
    const named = groups.filter((item) => item.name === exported.name)
    const usernames = (members: GroupMemberDto[]) => members.map((member) => member.username).sort()
    const status = named.some((item) => same(usernames(item.members), usernames(exported.members))) ? "same"
      : named.length ? "different" : "missing"
    add("approval-group", ref, exported.name, status, named.map((item) => ({ id: item.id, name: item.name })), options)
  }
  for (const ref of used.survey) {
    const exported = bundle.resources.surveys.find((item) => item.id === ref)
    const options = surveys.map((item) => ({ id: item.id, name: item.title }))
    if (!exported) { add("survey", ref, ref, "not-exported", [], options); continue }
    const named = surveys.filter((item) => item.title === exported.title)
    let status: DependencyStatus = named.length ? "different" : "missing"
    for (const candidate of named) {
      if (same((await surveysApi.get(candidate.id)).definition, exported.definition)) status = "same"
    }
    add("survey", ref, exported.title, status, named.map((item) => ({ id: item.id, name: item.title })), options)
  }
  for (const ref of used["saved-dataset"]) {
    const exported = bundle.resources.savedDatasets.find((item) => item.name === ref)
    const options = datasets.map((item) => ({ id: item.name, name: item.label ?? item.name }))
    if (!exported) { add("saved-dataset", ref, ref, "not-exported", [], options); continue }
    const named = datasets.filter((item) => item.name === ref)
    const status = named.some((item) => same(item.definition, exported.definition)) ? "same"
      : named.length ? "different" : "missing"
    add("saved-dataset", ref, exported.label ?? ref, status, named.map((item) => ({ id: item.name, name: item.label ?? item.name })), options)
  }
  return dependencies
}

/** What to do unless the user says otherwise: reuse an identical one, otherwise create a copy (or pick one). */
export function defaultDecision(dependency: Dependency): Decision | null {
  if (dependency.status === "same") return { action: "use", id: dependency.candidates[0].id }
  if (dependency.creatable) return { action: "create", name: freeName(dependency) }
  return dependency.options[0] ? { action: "use", id: dependency.options[0].id } : null
}

function freeName(dependency: Dependency): string {
  if (dependency.status === "missing") return dependency.kind === "saved-dataset" ? dependency.ref : dependency.name
  return dependency.kind === "saved-dataset" ? `${dependency.ref}_2`.slice(0, 40) : `${dependency.name} (içe aktarıldı)`
}

// ---- apply ----------------------------------------------------------------------------------------------------

export interface ImportOptions {
  name: string
  /** Schedules start disabled unless asked: a copy should not start mailing people on its own. */
  enableSchedules: boolean
  enableTriggers: boolean
}

export interface ImportResult {
  flowId: string
  created: { kind: DependencyKind; name: string }[]
  webhooks: number
}

/**
 * Creates what was chosen to be created, the flow with its ids mapped, then its schedules and triggers. If the flow
 * cannot be saved, the records created for it are deleted again, so a failed import leaves nothing behind.
 */
export async function applyImport(bundle: FlowBundle, dependencies: Dependency[], decisions: Record<string, Decision>,
  options: ImportOptions): Promise<ImportResult> {
  const ids = new Map<string, string>()   // dependency key -> id here (name for saved datasets)
  const created: ImportResult["created"] = []
  const undo: (() => Promise<unknown>)[] = []
  try {
    for (const dependency of dependencies) {
      const decision = decisions[dependency.key]
      if (!decision) throw new Error(`${KIND_LABELS[dependency.kind]} “${dependency.name}” için bir seçim yapın.`)
      if (decision.action === "use") {
        ids.set(dependency.key, decision.id)
        continue
      }
      const id = await create(bundle, dependency, decision.name)
      ids.set(dependency.key, id.value)
      created.push({ kind: dependency.kind, name: decision.name })
      undo.push(id.remove)
    }
    const map = (kind: DependencyKind, ref: string | undefined | null) => (ref ? ids.get(`${kind}:${ref}`) ?? ref : ref)
    const steps = (bundle.flow.steps as Step[]).map((step) => {
      if (step.type === "mail") return { ...step, templateId: map("mail-template", step.templateId) }
      if (step.type === "approval") return { ...step, groupId: map("approval-group", step.groupId) }
      if (step.type === "survey") return { ...step, surveyId: map("survey", step.surveyId) }
      if (step.type === "dataset" && step.datasetName) return { ...step, datasetName: map("saved-dataset", step.datasetName) }
      return step
    }) as FlowStepDto[]
    const flow = await flowsApi.create({ name: options.name, description: bundle.flow.description, steps,
      edges: bundle.flow.edges, layout: bundle.flow.layout })
    undo.push(() => flowsApi.remove(flow.id))
    for (const schedule of bundle.schedules) {
      await flowsApi.createSchedule(flow.id, { ...schedule, enabled: options.enableSchedules && schedule.enabled })
    }
    let webhooks = 0
    for (const trigger of bundle.triggers) {
      const topic = trigger.topic?.startsWith(SURVEY_TOPIC)
        ? SURVEY_TOPIC + map("survey", trigger.topic.slice(SURVEY_TOPIC.length)) : trigger.topic
      await flowsApi.createTrigger(flow.id, { ...trigger, topic, enabled: options.enableTriggers && trigger.enabled })
      if (trigger.type === "webhook") webhooks++
    }
    return { flowId: flow.id, created, webhooks }
  } catch (error) {
    for (const remove of undo.reverse()) await remove().catch(() => undefined)
    throw error
  }
}

async function create(bundle: FlowBundle, dependency: Dependency, name: string): Promise<{ value: string; remove: () => Promise<unknown> }> {
  switch (dependency.kind) {
    case "mail-template": {
      const source = bundle.resources.mailTemplates.find((item) => item.id === dependency.ref)!
      const template = await templatesApi.create({ name, description: source.description, subject: source.subject,
        bodyHtml: source.bodyHtml })
      return { value: template.id, remove: () => templatesApi.remove(template.id) }
    }
    case "approval-group": {
      const source = bundle.resources.approvalGroups.find((item) => item.id === dependency.ref)!
      const group = await approvalsApi.createGroup({ name, description: source.description, members: source.members })
      return { value: group.id, remove: () => approvalsApi.removeGroup(group.id) }
    }
    case "survey": {
      const { id: _exported, ...source } = bundle.resources.surveys.find((item) => item.id === dependency.ref)!
      void _exported
      const survey = await surveysApi.create({ ...source, title: name })
      return { value: survey.id, remove: () => surveysApi.remove(survey.id) }
    }
    case "saved-dataset": {
      const source = bundle.resources.savedDatasets.find((item) => item.name === dependency.ref)!
      const dataset = await savedDatasetsApi.create({ name, label: source.label, description: source.description,
        definition: source.definition })
      return { value: dataset.name, remove: () => savedDatasetsApi.remove(dataset.id) }
    }
  }
}

import type { FlowStepDto } from "../types/flow.dto"
import { looksLikeCron } from "./cron"
import { type GraphModel } from "./flow-graph"
import { stepType } from "./step-types"
import { START_NODE, type TriggerDraft } from "./triggers"

export interface FlowIssue {
  /** A step key, the start node, or the flow itself. */
  target: string
  severity: "error" | "warning"
  message: string
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function stepIssues(step: FlowStepDto): string[] {
  switch (step.type) {
    case "dataset": {
      const format = step.format ?? "csv"
      const file = step.fileName && !step.fileName.toLowerCase().endsWith(`.${format}`)
        ? [`Dosya adı .${format} ile bitmeli.`] : []
      if (step.datasetName != null) return step.datasetName ? file : ["Kayıtlı veri setini seçin.", ...file]
      if (!step.definition?.root) return ["Veri setini tasarlayın: kayıt türü seçilmedi.", ...file]
      return step.definition.columns.length === 0 ? ["Veri setine en az bir kolon ekleyin.", ...file] : file
    }
    case "mail": return [
      ...(!step.templateId ? ["Mail şablonu seçin."] : []),
      ...(step.to.length === 0 ? ["En az bir alıcı girin."] : []),
      ...[...step.to, ...(step.cc ?? [])].filter((address) => !EMAIL.test(address))
        .map((address) => `Geçersiz e-posta: ${address}`),
    ]
    case "condition": return step.expression === true || step.expression == null ? ["Koşul için bir kural ekleyin."] : []
    case "filter": return [
      ...(!step.source.trim() ? ["Süzülecek listenin yolunu girin."] : []),
      ...(step.expression === true || step.expression == null ? ["Tutulacak elemanlar için bir kural ekleyin."] : []),
    ]
    case "approval": return [
      ...(!step.groupId ? ["Onay grubu seçin."] : []),
      ...(!step.title.trim() ? ["Onaylayana gösterilecek başlığı girin."] : []),
    ]
    case "webhook": return /^https?:\/\/[^\s/]+/.test(step.url.trim()) ? [] : ["Geçerli bir http(s) adresi girin."]
    case "survey": return [
      ...(!step.surveyId ? ["Gönderilecek anketi seçin."] : []),
      ...(step.to.length === 0 ? ["En az bir alıcı girin."] : []),
      ...step.to.filter((address) => !address.includes("{{") && !EMAIL.test(address)).map((address) => `Geçersiz e-posta: ${address}`),
    ]
    case "kafka": return [
      ...(!step.topic.trim() || step.topic.trim().endsWith(".") ? ["Topic adını tamamlayın."] : []),
      ...(!step.payload.trim() ? ["Mesaj içeriğini girin."] : []),
    ]
  }
}

/**
 * What keeps a flow from being saved (errors) or may surprise its owner (warnings), checked in the browser so the
 * user sees it while building; the server checks again on save.
 */
export function checkFlow(name: string, model: GraphModel, triggers: TriggerDraft[]): FlowIssue[] {
  const issues: FlowIssue[] = []
  if (!name.trim()) issues.push({ target: "flow", severity: "error", message: "Akışa bir ad verin." })
  if (model.steps.length === 0) {
    issues.push({ target: "flow", severity: "error", message: "Başlangıçtan sonra en az bir adım ekleyin." })
  }
  for (const step of model.steps) {
    stepIssues(step).forEach((message) => issues.push({ target: step.key, severity: "error", message }))
  }
  // one way in: exactly one step without an incoming edge
  if (model.steps.length > 1) {
    const incoming = new Set(model.edges.map((edge) => edge.to))
    const starts = model.steps.filter((step) => !incoming.has(step.key))
    if (starts.length > 1) {
      starts.slice(1).forEach((step) => issues.push({ target: step.key, severity: "error",
        message: "Bu adıma hiçbir adımdan gelinmiyor: önceki bir adımın çıkışından bu adıma bağlayın." }))
    }
    if (starts.length === 0) {
      issues.push({ target: "flow", severity: "error", message: "Akış döngü oluşturuyor; başlangıç adımı yok." })
    }
  }
  // branches that end the run without saying so
  for (const step of model.steps) {
    const outcomes = stepType(step.type).outcomes
    if (outcomes.length < 2) continue
    const out = model.edges.filter((edge) => edge.from === step.key)
    if (out.length === 0 || out.some((edge) => !edge.when)) continue
    outcomes.filter((outcome) => !out.some((edge) => edge.when === outcome.id)).forEach((outcome) =>
      issues.push({ target: step.key, severity: "warning",
        message: `“${outcome.label}” çıkışı bağlı değil: bu durumda akış burada biter.` }))
  }
  for (const trigger of triggers) {
    if (trigger.kind === "schedule" && !looksLikeCron(trigger.cron)) {
      issues.push({ target: START_NODE, severity: "error", message: `Zamanlama geçersiz: “${trigger.cron}”.` })
    }
    if (trigger.kind === "survey" && trigger.surveyId === null) {
      issues.push({ target: START_NODE, severity: "error", message: "Anket tetikleyicisi için bir anket seçin." })
    }
    if (trigger.kind === "kafka" && (!trigger.topic.trim() || trigger.topic.trim().endsWith("."))) {
      issues.push({ target: START_NODE, severity: "error", message: "Kafka tetikleyicisinin topic adını tamamlayın." })
    }
  }
  if (triggers.length === 0) {
    issues.push({ target: START_NODE, severity: "warning",
      message: "Akış yalnızca elle başlatılabilir; zamanlama, webhook, Kafka ya da anket yanıtı ekleyebilirsiniz." })
  }
  return issues
}

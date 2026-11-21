import {
  CheckCheckIcon, ClipboardListIcon, DatabaseIcon, FilterIcon, GitBranchIcon, MailIcon, RadioTowerIcon, WebhookIcon, type LucideIcon,
} from "lucide-react"
import type { FlowStepDto, StepType } from "../types/flow.dto"

export interface Outcome {
  /** Value of `edge.when`; "out" = the single way out (edge.when null). */
  id: string
  label: string
}

/** Palette sections, in order. */
export const STEP_GROUPS = ["Veri", "Mantık", "Eylemler"] as const

export interface StepTypeInfo {
  type: StepType
  group: (typeof STEP_GROUPS)[number]
  label: string
  description: string
  icon: LucideIcon
  outcomes: Outcome[]
  /** A new step of this type with sensible defaults. */
  create: (key: string) => FlowStepDto
}

const SINGLE: Outcome[] = [{ id: "out", label: "" }]

export const STEP_TYPES: StepTypeInfo[] = [
  {
    type: "dataset", group: "Veri", label: "Veri seti", icon: DatabaseIcon, outcomes: SINGLE,
    description: "Veri setini akış sahibinin yetkileriyle oluşturur, Excel ya da CSV dosyası üretir.",
    create: (key) => ({ type: "dataset", key, format: "xlsx", definition: { root: "", columns: [] } }),
  },
  {
    type: "condition", group: "Mantık", label: "Koşul", icon: GitBranchIcon,
    outcomes: [{ id: "true", label: "Evet" }, { id: "false", label: "Hayır" }],
    description: "Bir kurala göre iki yoldan birine gider.",
    create: (key) => ({ type: "condition", key, expression: { ">": [{ var: "steps.dataset.rowCount" }, 0] } }),
  },
  {
    type: "filter", group: "Veri", label: "Filtre", icon: FilterIcon, outcomes: SINGLE,
    description: "Bir listedeki kurala uyan elemanları ayırır.",
    create: (key) => ({ type: "filter", key, source: "trigger.items", expression: { ">": [{ var: "item.amount" }, 0] } }),
  },
  {
    type: "approval", group: "Mantık", label: "Onay", icon: CheckCheckIcon,
    outcomes: [{ id: "approved", label: "Onaylandı" }, { id: "rejected", label: "Reddedildi" },
      { id: "timeout", label: "Zaman aşımı" }],
    description: "Bir onay grubundan birinin kararını bekler.",
    create: (key) => ({ type: "approval", key, groupId: 0, title: "{{flow.name}} onayı", timeoutHours: 72 }),
  },
  {
    type: "survey", group: "Mantık", label: "Anket gönder", icon: ClipboardListIcon,
    outcomes: [{ id: "answered", label: "Yanıtlandı" }, { id: "timeout", label: "Zaman aşımı" }],
    description: "Bir anketin tek kullanımlık bağlantısını mailler ve yanıtı bekler; cevaplar sonraki adımlarda kullanılır.",
    create: (key) => ({ type: "survey", key, surveyId: 0, to: [], timeoutHours: 72 }),
  },
  {
    type: "mail", group: "Eylemler", label: "Mail", icon: MailIcon, outcomes: SINGLE,
    description: "Şablondan mail gönderir, veri setlerini ekleyebilir.",
    create: (key) => ({ type: "mail", key, templateId: 0, to: [], attachments: [] }),
  },
  {
    type: "webhook", group: "Eylemler", label: "HTTP isteği", icon: WebhookIcon, outcomes: SINGLE,
    description: "Başka bir sisteme HTTP isteği atar.",
    create: (key) => ({ type: "webhook", key, url: "https://", method: "POST", body: "{\"run\": {{run.id}}}" }),
  },
  {
    type: "kafka", group: "Eylemler", label: "Kafka'ya yaz", icon: RadioTowerIcon, outcomes: SINGLE,
    description: "Bir Kafka topic'ine event yayınlar.",
    create: (key) => ({ type: "kafka", key, topic: "flows.", payload: "{\"run\": {{run.id}}}" }),
  },
]

export function stepType(type: StepType): StepTypeInfo {
  return STEP_TYPES.find((info) => info.type === type)!
}

/** One line under the step's name on the canvas. */
export function summary(step: FlowStepDto): string {
  switch (step.type) {
    case "dataset": return step.datasetName ? `Kayıtlı: ${step.datasetName}`
      : step.definition?.root ? `${step.definition.root} · ${step.definition.columns.length} kolon`
      : "Henüz tasarlanmadı"
    case "mail": return step.to.length ? step.to.join(", ") : "Alıcı yok"
    case "condition": return "Kurala göre dallanır"
    case "filter": return step.source
    case "approval": return `${step.timeoutHours ?? 72} saat içinde`
    case "webhook": return `${step.method ?? "POST"} ${safeHost(step.url)}`
    case "kafka": return step.topic
    case "survey": return step.to.length ? `${step.to.join(", ")} · ${step.timeoutHours ?? 72} saat` : "Alıcı yok"
  }
}

function safeHost(url: string): string {
  try {
    return new URL(url).host || url
  } catch {
    return url
  }
}

/** A key not used yet: dataset, dataset_2, ... */
export function newKey(type: StepType, taken: string[]): string {
  if (!taken.includes(type)) return type
  for (let i = 2; ; i++) {
    if (!taken.includes(`${type}_${i}`)) return `${type}_${i}`
  }
}

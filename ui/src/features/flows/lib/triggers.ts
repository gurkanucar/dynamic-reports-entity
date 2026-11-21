import { flowsApi } from "../api/flows.api"
import type { JsonLogicRule, ScheduleResponse, TriggerResponse } from "../types/flow.dto"
import { DEFAULT_TIME_ZONE, describeCron } from "./cron"
import { shortId } from "@/shared/lib/format"

/** What starts a flow, as edited on the canvas; saved together with the flow (`id` = already saved). */
export type TriggerDraft =
  | { kind: "schedule"; uid: string; saved?: ScheduleResponse; cron: string; timeZone: string; enabled: boolean }
  | { kind: "webhook"; uid: string; saved?: TriggerResponse; enabled: boolean }
  | { kind: "kafka"; uid: string; saved?: TriggerResponse; topic: string; filter: JsonLogicRule | null; enabled: boolean }
  /** Every response to a survey (an `event` trigger on `survey:<id>`). */
  | { kind: "survey"; uid: string; saved?: TriggerResponse; surveyId: string | null; title?: string;
      filter: JsonLogicRule | null; enabled: boolean }

export type TriggerKind = TriggerDraft["kind"]

/** The id of the start node on the canvas (and its position in the layout). */
export const START_NODE = "__start__"

let counter = 0
const uid = () => `t${++counter}`

export function newTrigger(kind: TriggerKind): TriggerDraft {
  switch (kind) {
    case "schedule": return { kind, uid: uid(), cron: "0 8 * * MON-FRI", timeZone: DEFAULT_TIME_ZONE, enabled: true }
    case "webhook": return { kind, uid: uid(), enabled: true }
    case "kafka": return { kind, uid: uid(), topic: "flows.", filter: null, enabled: true }
    case "survey": return { kind, uid: uid(), surveyId: null, filter: null, enabled: true }
  }
}

export const SURVEY_SOURCE = "survey:"

export function surveyTrigger(surveyId: string, title?: string, filter: JsonLogicRule | null = null): TriggerDraft {
  return { kind: "survey", uid: uid(), surveyId, title, filter, enabled: true }
}

export function toDrafts(schedules: ScheduleResponse[], triggers: TriggerResponse[]): TriggerDraft[] {
  return [
    ...schedules.map((saved): TriggerDraft => ({ kind: "schedule", uid: uid(), saved, cron: saved.cron,
      timeZone: saved.timeZone, enabled: saved.enabled })),
    ...triggers.map((saved): TriggerDraft => saved.type === "webhook"
      ? { kind: "webhook", uid: uid(), saved, enabled: saved.enabled }
      : saved.type === "event" && saved.topic?.startsWith(SURVEY_SOURCE)
        ? { kind: "survey", uid: uid(), saved, surveyId: saved.topic.slice(SURVEY_SOURCE.length),
          filter: saved.filter, enabled: saved.enabled }
        : { kind: "kafka", uid: uid(), saved, topic: saved.topic ?? "", filter: saved.filter, enabled: saved.enabled }),
  ]
}

/** One line per trigger on the start node. */
export function describeTrigger(trigger: TriggerDraft): string {
  switch (trigger.kind) {
    case "schedule": return describeCron(trigger.cron)
    case "webhook": return trigger.saved ? "Webhook çağrısı" : "Webhook çağrısı (kaydedince adres verilir)"
    case "kafka": return `Kafka: ${trigger.topic || "topic seçilmedi"}${trigger.filter ? " · filtreli" : ""}`
    case "survey": return `Anket yanıtı: ${trigger.surveyId === null ? "anket seçilmedi"
      : trigger.title ?? `#${shortId(trigger.surveyId)}`}${trigger.filter ? " · filtreli" : ""}`
  }
}

export interface WebhookSecret {
  url: string
  secret: string
}

/**
 * Brings the flow's schedules and triggers in line with the drafts: removed ones are deleted, new ones created,
 * changed ones replaced. Returns the secrets of new webhooks (shown once).
 */
export async function saveTriggers(flowId: string, original: TriggerDraft[], drafts: TriggerDraft[])
  : Promise<WebhookSecret[]> {
  const kept = new Set(drafts.map((draft) => draft.saved?.id).filter((id) => id !== undefined))
  for (const old of original) {
    if (old.saved && !kept.has(old.saved.id)) {
      await (old.kind === "schedule" ? flowsApi.removeSchedule(old.saved.id) : flowsApi.removeTrigger(old.saved.id))
    }
  }
  const secrets: WebhookSecret[] = []
  for (const draft of drafts) {
    if (draft.kind === "schedule") {
      const request = { cron: draft.cron.trim(), timeZone: draft.timeZone, enabled: draft.enabled }
      if (!draft.saved) await flowsApi.createSchedule(flowId, request)
      else if (draft.saved.cron !== request.cron || draft.saved.timeZone !== request.timeZone
        || draft.saved.enabled !== request.enabled) await flowsApi.replaceSchedule(draft.saved, request)
      continue
    }
    const request = draft.kind === "kafka"
      ? { type: "kafka" as const, enabled: draft.enabled, topic: draft.topic.trim(), filter: draft.filter }
      : draft.kind === "survey"
        ? { type: "event" as const, enabled: draft.enabled, topic: `${SURVEY_SOURCE}${draft.surveyId}`, filter: draft.filter }
        : { type: "webhook" as const, enabled: draft.enabled }
    if (!draft.saved) {
      const created = await flowsApi.createTrigger(flowId, request)
      if (created.secret && created.trigger.webhookUrl) {
        secrets.push({ url: `${window.location.origin}${created.trigger.webhookUrl}`, secret: created.secret })
      }
    } else if (draft.saved.enabled !== draft.enabled || ((draft.kind === "kafka" || draft.kind === "survey")
      && (draft.saved.topic !== request.topic || JSON.stringify(draft.saved.filter) !== JSON.stringify(draft.filter)))) {
      await flowsApi.replaceTrigger(draft.saved, request)
    }
  }
  return secrets
}

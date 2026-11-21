import { ApiError } from "@/shared/api/http-client"
import { templatesApi } from "@/features/templates/api/templates.api"
import type { FlowDraftDto } from "@/features/assistant/types/assistant.dto"
import type { FlowEdgeDto, FlowStepDto, JsonLogicRule } from "../types/flow.dto"
import { DEFAULT_TIME_ZONE } from "./cron"
import { withExplicitEdges, withLayout, type GraphModel } from "./flow-graph"
import { newTrigger, surveyTrigger, type TriggerDraft } from "./triggers"

export interface AppliedDraft {
  name: string
  model: GraphModel
  schedules: TriggerDraft[]
  surveys: TriggerDraft[]
  /** Names of the mail templates created for it. */
  createdTemplates: string[]
}

/**
 * Turns the flow assistant's draft into editor state. Templates the draft brings along are created now (they are
 * reusable on their own; a taken name gets a number), and mail steps get their ids instead of `templateRef`.
 */
export async function applyFlowDraft(draft: FlowDraftDto): Promise<AppliedDraft> {
  const ids = new Map<string, string>()
  const createdTemplates: string[] = []
  for (const template of draft.newTemplates ?? []) {
    const created = await createWithFreeName(template)
    ids.set(template.ref, created.id)
    createdTemplates.push(created.name)
  }
  const steps = draft.flow.steps.map((raw) => {
    const { templateRef, ...step } = raw as Record<string, unknown> & { templateRef?: string }
    if (templateRef !== undefined) step.templateId = ids.get(templateRef) ?? null
    return step as unknown as FlowStepDto
  })
  const edges: FlowEdgeDto[] = (draft.flow.edges ?? []).map((edge) => ({ from: edge.from, to: edge.to, when: edge.when ?? null }))
  return {
    name: draft.flow.name,
    model: withLayout(withExplicitEdges({ steps, edges, layout: {} })),
    schedules: (draft.schedules ?? []).map((schedule) => ({
      ...(newTrigger("schedule") as Extract<TriggerDraft, { kind: "schedule" }>),
      cron: schedule.cron, timeZone: schedule.timeZone ?? DEFAULT_TIME_ZONE,
    })),
    surveys: (draft.surveyTriggers ?? []).map((trigger) =>
      surveyTrigger(trigger.surveyId, undefined, (trigger.filter ?? null) as JsonLogicRule | null)),
    createdTemplates,
  }
}

export async function createWithFreeName(template: FlowDraftDto["newTemplates"][number]) {
  for (let attempt = 1; ; attempt++) {
    const name = attempt === 1 ? template.name : `${template.name} (${attempt})`
    try {
      return await templatesApi.create({ name, description: "Akış asistanı hazırladı", subject: template.subject,
        bodyHtml: template.bodyHtml })
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 409) || attempt >= 10) throw error
    }
  }
}

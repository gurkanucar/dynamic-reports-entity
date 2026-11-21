import { http, saveResponseAsFile, downloadUrl } from "@/shared/api/http-client"
import { queryString } from "@/shared/lib/list-state"
import type { Envelope } from "@/shared/types/api.types"
import type {
  DownloadLinkResponse, FileContentResponse, FlowDetailResponse, FlowRequest, FlowSummaryResponse, RunDetailResponse,
  RunStatus, RunSummaryResponse, ScheduleRequest, ScheduleResponse, TriggerRequest, TriggerResponse,
  TriggerWithSecretResponse,
} from "../types/flow.dto"

const ifMatch = (version: number) => ({ "If-Match": `"${version}"` })

export const flowsApi = {
  /** `sort`: name, updatedAt, createdAt, owner. */
  list: (page: number, size = 20, filters: { q?: string; sort?: string } = {}) =>
    http.get<Envelope<FlowSummaryResponse[]>>(`/api/flows${queryString({ page, size, q: filters.q, sort: filters.sort })}`),
  get: async (id: string) => (await http.get<Envelope<FlowDetailResponse>>(`/api/flows/${id}`)).data,
  create: async (request: FlowRequest) =>
    (await http.send<Envelope<FlowDetailResponse>>("POST", "/api/flows", request)).data,
  replace: async (id: string, version: number, request: FlowRequest) =>
    (await http.send<Envelope<FlowDetailResponse>>("PUT", `/api/flows/${id}`, request, ifMatch(version))).data,
  remove: (id: string) => http.send<void>("DELETE", `/api/flows/${id}`),

  // ---- runs ----
  start: async (flowId: string) =>
    (await http.send<Envelope<RunSummaryResponse>>("POST", `/api/flows/${flowId}/runs`)).data,
  runsOfFlow: (flowId: string, page: number, size = 20) =>
    http.get<Envelope<RunSummaryResponse[]>>(`/api/flows/${flowId}/runs?page=${page}&size=${size}`),
  /** `sort`: createdAt, startedAt, finishedAt, status, flow. */
  runs: (statuses: RunStatus[], page: number, size = 20,
    filters: { q?: string; trigger?: string[]; sort?: string } = {}) =>
    http.get<Envelope<RunSummaryResponse[]>>(`/api/flow-runs${queryString({ page, size, status: statuses,
      trigger: filters.trigger ?? [], q: filters.q, sort: filters.sort })}`),
  run: async (runId: string) => (await http.get<Envelope<RunDetailResponse>>(`/api/flow-runs/${runId}`)).data,
  cancel: async (runId: string) =>
    (await http.send<Envelope<RunSummaryResponse>>("POST", `/api/flow-runs/${runId}/cancel`)).data,

  /**
   * Downloads through the presigned S3 URL (signed as an attachment, so the page stays); streams through the API
   * when the store has none. Asking for the link first turns an expired file into a message instead of an error page.
   */
  async downloadFile(fileId: string) {
    const link = (await http.get<Envelope<DownloadLinkResponse>>(`/api/flow-files/${fileId}/download-link`)).data
    if (link.url) {
      downloadUrl(link.url, link.fileName)
      return
    }
    await saveResponseAsFile(await http.raw(`/api/flow-files/${fileId}/download`), link.fileName)
  },

  /** An HTML or text file of a run (a sent mail's body), read through the API to show it in the page. */
  fileContent: async (fileId: string) =>
    (await http.get<Envelope<FileContentResponse>>(`/api/flow-files/${fileId}/content`)).data,

  // ---- schedules ----
  schedules: async (flowId: string) =>
    (await http.get<Envelope<ScheduleResponse[]>>(`/api/flows/${flowId}/schedules`)).data,
  createSchedule: async (flowId: string, request: ScheduleRequest) =>
    (await http.send<Envelope<ScheduleResponse>>("POST", `/api/flows/${flowId}/schedules`, request)).data,
  replaceSchedule: async (schedule: ScheduleResponse, request: ScheduleRequest) =>
    (await http.send<Envelope<ScheduleResponse>>("PUT", `/api/flow-schedules/${schedule.id}`, request,
      ifMatch(schedule.version))).data,
  removeSchedule: (id: string) => http.send<void>("DELETE", `/api/flow-schedules/${id}`),

  // ---- event triggers ----
  triggers: async (flowId: string) =>
    (await http.get<Envelope<TriggerResponse[]>>(`/api/flows/${flowId}/triggers`)).data,
  createTrigger: async (flowId: string, request: TriggerRequest) =>
    (await http.send<Envelope<TriggerWithSecretResponse>>("POST", `/api/flows/${flowId}/triggers`, request)).data,
  replaceTrigger: async (trigger: TriggerResponse, request: TriggerRequest) =>
    (await http.send<Envelope<TriggerResponse>>("PUT", `/api/flow-triggers/${trigger.id}`, request,
      ifMatch(trigger.version))).data,
  rotateSecret: async (id: string) =>
    (await http.send<Envelope<TriggerWithSecretResponse>>("POST", `/api/flow-triggers/${id}/rotate-secret`)).data,
  removeTrigger: (id: string) => http.send<void>("DELETE", `/api/flow-triggers/${id}`),
}

import { http } from "@/shared/api/http-client"
import { queryString } from "@/shared/lib/list-state"
import type { Envelope } from "@/shared/types/api.types"
import type {
  Answers, FollowUpChange, ResponseFilters, ResponseNote, ResponseRow, SurveyStatus, SubmittedResponse, SurveyCheckResponse, SurveyDefinition, SurveyFormResponse, SurveyListItem,
  SurveyRequest, SurveyResponse, SurveySummary,
} from "../types/survey.dto"

export const surveysApi = {
  /** `sort`: title, updatedAt, createdAt, status, closesAt. */
  list: (scope: "mine" | "open", page: number, size = 20,
    filters: { q?: string; status?: SurveyStatus[]; sort?: string } = {}) =>
    http.get<Envelope<SurveyListItem[]>>(`/api/surveys${queryString({ scope, page, size, q: filters.q,
      status: filters.status ?? [], sort: filters.sort })}`),
  get: async (id: number) => (await http.get<Envelope<SurveyResponse>>(`/api/surveys/${id}`)).data,
  create: async (request: SurveyRequest) =>
    (await http.send<Envelope<SurveyResponse>>("POST", "/api/surveys", request)).data,
  replace: async (id: number, version: number, request: SurveyRequest) =>
    (await http.send<Envelope<SurveyResponse>>("PUT", `/api/surveys/${id}`, request,
      { "If-Match": `"${version}"` })).data,
  remove: (id: number) => http.send<void>("DELETE", `/api/surveys/${id}`),

  /** Checks a definition (and answers to it) without saving. */
  check: async (definition: SurveyDefinition, answers?: Answers) =>
    (await http.send<Envelope<SurveyCheckResponse>>("POST", "/api/survey-checks", { definition, answers })).data,

  form: async (id: number, invitation?: string | null) =>
    (await http.get<Envelope<SurveyFormResponse>>(`/api/surveys/${id}/form${queryString({ invitation })}`)).data,
  submit: async (id: number, answers: Answers, invitation?: string | null) =>
    (await http.send<Envelope<SubmittedResponse>>("POST", `/api/surveys/${id}/responses`,
      { answers, invitation: invitation ?? undefined })).data,
  /** The counted answers respondents may see (403 when the survey does not show them to this user). */
  results: async (id: number) => (await http.get<Envelope<SurveySummary>>(`/api/surveys/${id}/results`)).data,

  /** `sort`: submittedAt, status, priority, followUpAt, assignee, respondent. */
  responses: (id: number, page: number, size = 20, filters?: Partial<ResponseFilters>) =>
    http.get<Envelope<ResponseRow[]>>(`/api/surveys/${id}/responses${queryString({ page, size,
      status: filters?.status ?? [], priority: filters?.priority ?? [], q: filters?.q, sort: filters?.sort })}`),
  response: async (id: number, responseId: number) =>
    (await http.get<Envelope<ResponseRow>>(`/api/surveys/${id}/responses/${responseId}`)).data,
  followUp: async (id: number, responseId: number, change: FollowUpChange) =>
    (await http.send<Envelope<ResponseRow>>("PATCH", `/api/surveys/${id}/responses/${responseId}`, change)).data,
  notes: async (id: number, responseId: number) =>
    (await http.get<Envelope<ResponseNote[]>>(`/api/surveys/${id}/responses/${responseId}/notes`)).data,
  addNote: async (id: number, responseId: number, text: string) =>
    (await http.send<Envelope<ResponseNote>>("POST", `/api/surveys/${id}/responses/${responseId}/notes`, { text })).data,
  deleteNote: (id: number, responseId: number, noteId: number) =>
    http.send<void>("DELETE", `/api/surveys/${id}/responses/${responseId}/notes/${noteId}`),
  summary: async (id: number) => (await http.get<Envelope<SurveySummary>>(`/api/surveys/${id}/summary`)).data,
  exportUrl: (id: number) => `/api/surveys/${id}/responses/export`,
}

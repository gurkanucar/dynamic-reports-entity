import { http } from "@/shared/api/http-client"
import type { Envelope } from "@/shared/types/api.types"
import type {
  TemplatePreviewRequest, TemplatePreviewResponse, TemplateRequest, TemplateResponse, TemplateSummaryResponse,
} from "../types/template.dto"

export const templatesApi = {
  /** `sort`: name, updatedAt. */
  list: (page = 1, size = 100, filters: { q?: string; sort?: string } = {}) =>
    http.get<Envelope<TemplateSummaryResponse[]>>(`/api/mail-templates?page=${page}&size=${size}${
      filters.q ? `&q=${encodeURIComponent(filters.q)}` : ""}${filters.sort ? `&sort=${filters.sort}` : ""}`),
  get: async (id: string) => (await http.get<Envelope<TemplateResponse>>(`/api/mail-templates/${id}`)).data,
  create: async (request: TemplateRequest) =>
    (await http.send<Envelope<TemplateResponse>>("POST", "/api/mail-templates", request)).data,
  replace: async (id: string, version: number, request: TemplateRequest) =>
    (await http.send<Envelope<TemplateResponse>>("PUT", `/api/mail-templates/${id}`, request,
      { "If-Match": `"${version}"` })).data,
  remove: (id: string) => http.send<void>("DELETE", `/api/mail-templates/${id}`),
  preview: async (request: TemplatePreviewRequest) =>
    (await http.send<Envelope<TemplatePreviewResponse>>("POST", "/api/mail-template-previews", request)).data,
  sampleModel: async () =>
    (await http.get<Envelope<Record<string, unknown>>>("/api/mail-template-previews/sample-model")).data,
}

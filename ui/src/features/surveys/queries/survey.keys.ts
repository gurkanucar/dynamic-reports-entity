export const surveyKeys = {
  all: ["surveys"] as const,
  list: (scope: string, page: number) => [...surveyKeys.all, "list", scope, page] as const,
  detail: (id: number) => [...surveyKeys.all, "detail", id] as const,
  form: (id: number) => [...surveyKeys.all, "form", id] as const,
  responses: (id: number, page: number) => [...surveyKeys.all, "responses", id, page] as const,
  response: (id: number, responseId: number) => [...surveyKeys.all, "response", id, responseId] as const,
  notes: (id: number, responseId: number) => [...surveyKeys.all, "notes", id, responseId] as const,
  results: (id: number) => [...surveyKeys.all, "results", id] as const,
  summary: (id: number) => [...surveyKeys.all, "summary", id] as const,
}

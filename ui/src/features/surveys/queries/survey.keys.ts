export const surveyKeys = {
  all: ["surveys"] as const,
  list: (scope: string, page: number) => [...surveyKeys.all, "list", scope, page] as const,
  detail: (id: string) => [...surveyKeys.all, "detail", id] as const,
  form: (id: string) => [...surveyKeys.all, "form", id] as const,
  responses: (id: string, page: number) => [...surveyKeys.all, "responses", id, page] as const,
  response: (id: string, responseId: string) => [...surveyKeys.all, "response", id, responseId] as const,
  notes: (id: string, responseId: string) => [...surveyKeys.all, "notes", id, responseId] as const,
  results: (id: string) => [...surveyKeys.all, "results", id] as const,
  summary: (id: string) => [...surveyKeys.all, "summary", id] as const,
}

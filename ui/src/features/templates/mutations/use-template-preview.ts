import { useMutation } from "@tanstack/react-query"
import { templatesApi } from "../api/templates.api"
import type { TemplatePreviewRequest } from "../types/template.dto"

export function useTemplatePreview() {
  return useMutation({ mutationFn: (request: TemplatePreviewRequest) => templatesApi.preview(request) })
}

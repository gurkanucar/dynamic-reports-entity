import { useMutation, useQueryClient } from "@tanstack/react-query"
import { surveysApi } from "../api/surveys.api"
import { surveyKeys } from "../queries/survey.keys"
import type { Answers, FollowUpChange, SurveyRequest } from "../types/survey.dto"

/** Creates a survey, or replaces it when `id` and `version` (the ETag) are given. */
export function useSaveSurvey() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, version, request }: { id?: number; version?: number; request: SurveyRequest }) =>
      id !== undefined && version !== undefined ? surveysApi.replace(id, version, request) : surveysApi.create(request),
    onSuccess: (saved) => {
      queryClient.setQueryData(surveyKeys.detail(saved.id), saved)
      return queryClient.invalidateQueries({ queryKey: surveyKeys.all })
    },
  })
}

export function useDeleteSurvey() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => surveysApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: surveyKeys.all }),
  })
}

/** Changes a response's follow-up; the list and the survey's counts refresh. */
export function useFollowUp(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ responseId, change }: { responseId: number; change: FollowUpChange }) =>
      surveysApi.followUp(id, responseId, change),
    onSuccess: (row) => {
      queryClient.setQueryData(surveyKeys.response(id, row.id), row)
      return queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] === "surveys"
        && (query.queryKey[1] === "responses" || query.queryKey[1] === "detail") })
    },
  })
}

export function useResponseNote(id: number, responseId: number) {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] === "surveys"
    && (query.queryKey[1] === "notes" || query.queryKey[1] === "responses") })
  return {
    add: useMutation({ mutationFn: (text: string) => surveysApi.addNote(id, responseId, text), onSuccess: refresh }),
    remove: useMutation({ mutationFn: (noteId: number) => surveysApi.deleteNote(id, responseId, noteId), onSuccess: refresh }),
  }
}

export function useSubmitResponse(id: number, invitation?: string | null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (answers: Answers) => surveysApi.submit(id, answers, invitation),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: surveyKeys.all }),
  })
}

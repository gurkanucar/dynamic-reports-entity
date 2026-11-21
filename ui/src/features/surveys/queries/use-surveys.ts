import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { surveysApi } from "../api/surveys.api"
import { surveyKeys } from "./survey.keys"

export function useSurveys(scope: "mine" | "open", page = 1,
  filters: { q?: string; status?: import("../types/survey.dto").SurveyStatus[]; sort?: string; size?: number } = {}) {
  return useQuery({
    queryKey: [...surveyKeys.list(scope, page), filters],
    queryFn: () => surveysApi.list(scope, page, filters.size ?? 20, filters),
    placeholderData: keepPreviousData,
  })
}

export function useSurvey(id: string | null) {
  return useQuery({
    queryKey: surveyKeys.detail(id ?? ""),
    queryFn: () => surveysApi.get(id!),
    enabled: id !== null,
  })
}

export function useSurveyForm(id: string, invitation?: string | null) {
  return useQuery({ queryKey: [...surveyKeys.form(id), invitation ?? null], queryFn: () => surveysApi.form(id, invitation),
    retry: false })
}

/** The counted results for respondents; only fetched when the form says there are some to show. */
export function useSurveyResults(id: string, enabled: boolean) {
  return useQuery({ queryKey: surveyKeys.results(id), queryFn: () => surveysApi.results(id), enabled, retry: false })
}

export function useSurveyResponse(id: string, responseId: string | null) {
  return useQuery({ queryKey: surveyKeys.response(id, responseId ?? ""), queryFn: () => surveysApi.response(id, responseId!),
    enabled: responseId !== null })
}

export function useResponseNotes(id: string, responseId: string | null) {
  return useQuery({ queryKey: surveyKeys.notes(id, responseId ?? ""), queryFn: () => surveysApi.notes(id, responseId!),
    enabled: responseId !== null })
}

export function useSurveyResponses(id: string, page: number, filters?: Partial<import("../types/survey.dto").ResponseFilters> & { size?: number }) {
  return useQuery({
    queryKey: [...surveyKeys.responses(id, page), filters ?? null],
    queryFn: () => surveysApi.responses(id, page, filters?.size ?? 20, filters),
    placeholderData: keepPreviousData,
  })
}

export function useSurveySummary(id: string) {
  return useQuery({ queryKey: surveyKeys.summary(id), queryFn: () => surveysApi.summary(id) })
}

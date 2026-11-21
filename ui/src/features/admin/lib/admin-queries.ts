import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { adminApi } from "../api/admin.api"
import type { Versioned } from "../api/admin.types"

type Params = Record<string, string | number | null | undefined | (string | number)[]>

/** A server-side list (filters, sort and page in `params`); keeps the previous page while the next loads. */
export function useAdminList<T>(resource: string, path: string, params: Params, enabled = true) {
  return useQuery({
    queryKey: ["admin", resource, "list", params],
    queryFn: () => adminApi.list<T>(path, params),
    placeholderData: keepPreviousData,
    enabled,
  })
}

/** Lookups for pickers (departments, trainings, skills): small, cached for a minute. */
export function useLookup<T>(name: string, fetch: () => Promise<T>, enabled = true) {
  return useQuery({ queryKey: ["admin", "lookup", name], queryFn: fetch, staleTime: 60_000, enabled })
}

/**
 * Create (no `existing`) or replace (`existing` gives the id and the version for If-Match) on `path`; every admin
 * query refreshes afterwards, since lists show counts of each other (members, records ...).
 */
export function useAdminSave<T, B>(path: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ existing, body }: { existing?: Versioned | null; body: B }) => existing
      ? adminApi.replace<T>(`${path}/${existing.id}`, existing.version, body)
      : adminApi.create<T>(path, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin"] }),
  })
}

export function useAdminDelete(path: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => adminApi.remove(`${path}/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin"] }),
  })
}

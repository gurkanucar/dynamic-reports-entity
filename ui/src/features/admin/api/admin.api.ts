import { http } from "@/shared/api/http-client"
import { queryString } from "@/shared/lib/list-state"
import type { Envelope } from "@/shared/types/api.types"
import type { PersonnelDetail, Ref, SkillRef } from "./admin.types"

type Params = Record<string, string | number | null | undefined | (string | number)[]>

/** The same calls for every master-data resource: paged list, create, replace (If-Match), delete. */
export const adminApi = {
  list: <T>(path: string, params: Params) => http.get<Envelope<T[]>>(`${path}${queryString(params)}`),
  get: async <T>(path: string) => (await http.get<Envelope<T>>(path)).data,
  create: async <T>(path: string, body: unknown) => (await http.send<Envelope<T>>("POST", path, body)).data,
  replace: async <T>(path: string, version: number, body: unknown) =>
    (await http.send<Envelope<T>>("PUT", path, body, { "If-Match": `"${version}"` })).data,
  remove: (path: string) => http.send<void>("DELETE", path),

  departments: async () => (await http.get<Envelope<Ref[]>>("/api/departments/lookup")).data,
  people: async (q: string, ids: number[] = []) =>
    (await http.get<Envelope<Ref[]>>(`/api/personnel/lookup${queryString({ q: q.trim().length >= 2 ? q.trim() : undefined, id: ids })}`)).data,
  person: async (id: number) => (await http.get<Envelope<PersonnelDetail>>(`/api/personnel/${id}`)).data,
  setSkills: async (id: number, skillIds: number[]) =>
    (await http.send<Envelope<SkillRef[]>>("PUT", `/api/personnel/${id}/skills`, { skillIds })).data,
  skills: async () => (await http.get<Envelope<SkillRef[]>>("/api/skills/lookup")).data,
  skillCategories: async () => (await http.get<Envelope<string[]>>("/api/skills/categories")).data,
  trainings: async () => (await http.get<Envelope<Ref[]>>("/api/trainings/lookup")).data,
  trainingCategories: async () => (await http.get<Envelope<string[]>>("/api/trainings/categories")).data,
}

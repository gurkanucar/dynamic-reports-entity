export const datasetKeys = {
  all: ["datasets"] as const,
  catalog: () => [...datasetKeys.all, "catalog"] as const,
  saved: () => [...datasetKeys.all, "saved"] as const,
  savedOne: (name: string) => [...datasetKeys.all, "saved", name] as const,
}

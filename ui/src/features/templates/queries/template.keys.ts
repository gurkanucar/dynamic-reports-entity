export const templateKeys = {
  all: ["templates"] as const,
  list: () => [...templateKeys.all, "list"] as const,
  detail: (id: number) => [...templateKeys.all, "detail", id] as const,
  sampleModel: () => [...templateKeys.all, "sample-model"] as const,
}

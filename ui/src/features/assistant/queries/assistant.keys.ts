export const assistantKeys = {
  all: ["assistant"] as const,
  settings: () => [...assistantKeys.all, "settings"] as const,
  suggestion: (id: number) => [...assistantKeys.all, "suggestion", id] as const,
}

export const flowKeys = {
  all: ["flows"] as const,
  list: (page: number) => [...flowKeys.all, "list", page] as const,
  detail: (id: string) => [...flowKeys.all, "detail", id] as const,
  schedules: (flowId: string) => [...flowKeys.all, "schedules", flowId] as const,
  triggers: (flowId: string) => [...flowKeys.all, "triggers", flowId] as const,
  runsOfFlow: (flowId: string, page: number) => [...flowKeys.all, "runs-of-flow", flowId, page] as const,
  runs: (statuses: string[], page: number) => [...flowKeys.all, "runs", statuses.join(","), page] as const,
  run: (runId: string) => [...flowKeys.all, "run", runId] as const,
  fileContent: (fileId: string) => [...flowKeys.all, "file-content", fileId] as const,
}

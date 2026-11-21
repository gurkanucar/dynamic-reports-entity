export const flowKeys = {
  all: ["flows"] as const,
  list: (page: number) => [...flowKeys.all, "list", page] as const,
  detail: (id: number) => [...flowKeys.all, "detail", id] as const,
  schedules: (flowId: number) => [...flowKeys.all, "schedules", flowId] as const,
  triggers: (flowId: number) => [...flowKeys.all, "triggers", flowId] as const,
  runsOfFlow: (flowId: number, page: number) => [...flowKeys.all, "runs-of-flow", flowId, page] as const,
  runs: (statuses: string[], page: number) => [...flowKeys.all, "runs", statuses.join(","), page] as const,
  run: (runId: number) => [...flowKeys.all, "run", runId] as const,
  fileContent: (fileId: number) => [...flowKeys.all, "file-content", fileId] as const,
}

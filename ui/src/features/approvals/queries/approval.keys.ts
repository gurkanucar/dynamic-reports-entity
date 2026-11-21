export const approvalKeys = {
  all: ["approvals"] as const,
  groups: () => [...approvalKeys.all, "groups"] as const,
  requests: (scope: string, statuses: string[], page: number) =>
    [...approvalKeys.all, "requests", scope, statuses.join(","), page] as const,
  request: (id: string) => [...approvalKeys.all, "request", id] as const,
}

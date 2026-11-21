import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { approvalsApi } from "../api/approvals.api"
import type { ApprovalStatus } from "../types/approval.dto"
import { approvalKeys } from "./approval.keys"

/** Requests of the groups the user is in (or all, for admins); refreshed every 15 seconds. */
export function useApprovalRequests(scope: "mine" | "all", statuses: ApprovalStatus[], page: number,
  filters: { q?: string; groupId?: number[]; sort?: string; size?: number } = {}) {
  return useQuery({
    queryKey: [...approvalKeys.requests(scope, statuses, page), filters],
    queryFn: () => approvalsApi.requests(scope, statuses, page, filters.size ?? 20, filters),
    placeholderData: keepPreviousData,
    refetchInterval: 15_000,
  })
}

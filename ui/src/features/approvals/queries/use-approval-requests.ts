import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { approvalsApi } from "../api/approvals.api"
import type { ApprovalStatus } from "../types/approval.dto"
import { approvalKeys } from "./approval.keys"

/** Requests of the groups the user is in (or all, for admins); refreshed every 15 seconds. */
export function useApprovalRequests(scope: "mine" | "all", statuses: ApprovalStatus[], page: number) {
  return useQuery({
    queryKey: approvalKeys.requests(scope, statuses, page),
    queryFn: () => approvalsApi.requests(scope, statuses, page),
    placeholderData: keepPreviousData,
    refetchInterval: 15_000,
  })
}

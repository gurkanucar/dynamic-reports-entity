import { useQuery } from "@tanstack/react-query"
import { approvalsApi } from "../api/approvals.api"
import { approvalKeys } from "./approval.keys"

export function useApprovalRequest(id: number | null) {
  return useQuery({
    queryKey: approvalKeys.request(id ?? 0),
    queryFn: () => approvalsApi.request(id!),
    enabled: id !== null,
  })
}

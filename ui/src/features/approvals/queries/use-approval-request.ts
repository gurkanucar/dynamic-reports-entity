import { useQuery } from "@tanstack/react-query"
import { approvalsApi } from "../api/approvals.api"
import { approvalKeys } from "./approval.keys"

export function useApprovalRequest(id: string | null) {
  return useQuery({
    queryKey: approvalKeys.request(id ?? ""),
    queryFn: () => approvalsApi.request(id!),
    enabled: id !== null,
  })
}

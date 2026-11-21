import { useQuery } from "@tanstack/react-query"
import { approvalsApi } from "../api/approvals.api"
import { approvalKeys } from "./approval.keys"

export function useApprovalGroups() {
  return useQuery({ queryKey: approvalKeys.groups(), queryFn: approvalsApi.groups })
}

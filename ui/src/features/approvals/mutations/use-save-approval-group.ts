import { useMutation, useQueryClient } from "@tanstack/react-query"
import { approvalsApi } from "../api/approvals.api"
import { approvalKeys } from "../queries/approval.keys"
import type { ApprovalGroupRequest } from "../types/approval.dto"

/** Creates a group, or replaces it when `id` and `version` (the ETag) are given. */
export function useSaveApprovalGroup() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, version, request }: { id?: string; version?: number; request: ApprovalGroupRequest }) =>
      id !== undefined && version !== undefined ? approvalsApi.replaceGroup(id, version, request)
        : approvalsApi.createGroup(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: approvalKeys.groups() }),
  })
}

import { useMutation, useQueryClient } from "@tanstack/react-query"
import { approvalsApi } from "../api/approvals.api"
import { approvalKeys } from "../queries/approval.keys"

export function useDeleteApprovalGroup() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => approvalsApi.removeGroup(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: approvalKeys.groups() }),
  })
}

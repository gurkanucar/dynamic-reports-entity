import { useMutation, useQueryClient } from "@tanstack/react-query"
import { flowKeys } from "@/features/flows/queries/flow.keys"
import { approvalsApi } from "../api/approvals.api"
import { approvalKeys } from "../queries/approval.keys"

export function useDecideApproval() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, approve, comment }: { id: string; approve: boolean; comment: string }) =>
      approvalsApi.decide(id, approve, { comment: comment.trim() || null }),
    onSuccess: () => Promise.all([
      queryClient.invalidateQueries({ queryKey: approvalKeys.all }),
      queryClient.invalidateQueries({ queryKey: flowKeys.all }),
    ]),
  })
}

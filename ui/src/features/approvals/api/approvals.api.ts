import { http } from "@/shared/api/http-client"
import type { Envelope } from "@/shared/types/api.types"
import type {
  ApprovalGroupRequest, ApprovalGroupResponse, ApprovalRequestResponse, ApprovalStatus, DecisionRequest,
} from "../types/approval.dto"

export const approvalsApi = {
  groups: async () => (await http.get<Envelope<ApprovalGroupResponse[]>>("/api/approval-groups")).data,
  createGroup: async (request: ApprovalGroupRequest) =>
    (await http.send<Envelope<ApprovalGroupResponse>>("POST", "/api/approval-groups", request)).data,
  replaceGroup: async (id: number, version: number, request: ApprovalGroupRequest) =>
    (await http.send<Envelope<ApprovalGroupResponse>>("PUT", `/api/approval-groups/${id}`, request,
      { "If-Match": `"${version}"` })).data,
  removeGroup: (id: number) => http.send<void>("DELETE", `/api/approval-groups/${id}`),

  requests: (scope: "mine" | "all", statuses: ApprovalStatus[], page: number, size = 20) =>
    http.get<Envelope<ApprovalRequestResponse[]>>(`/api/approval-requests?scope=${scope}&page=${page}&size=${size}${
      statuses.map((s) => `&status=${s}`).join("")}`),
  request: async (id: number) =>
    (await http.get<Envelope<ApprovalRequestResponse>>(`/api/approval-requests/${id}`)).data,
  decide: async (id: number, approve: boolean, request: DecisionRequest) =>
    (await http.send<Envelope<ApprovalRequestResponse>>("POST",
      `/api/approval-requests/${id}/${approve ? "approve" : "reject"}`, request)).data,
}

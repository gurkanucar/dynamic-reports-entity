import type { UserRef } from "@/shared/types/api.types"

export interface GroupMemberDto {
  username: string
  email: string | null
}

// ---- /api/approval-groups ----

export interface ApprovalGroupRequest {
  name: string
  description?: string | null
  members: GroupMemberDto[]
}

export interface ApprovalGroupResponse {
  id: string
  name: string
  description: string | null
  members: GroupMemberDto[]
  version: number
  updatedAt: string
  updatedBy: UserRef
}

// ---- /api/approval-requests ----

export type ApprovalStatus = "pending" | "approved" | "rejected" | "timed_out" | "cancelled"

/** What waits for the decision (e.g. a flow step): a label for people and an optional UI path. */
export interface ApprovalSourceDto {
  type: string
  id: string
  label: string
  link: string | null
}

export interface ApprovalRequestResponse {
  id: string
  source: ApprovalSourceDto
  group: { id: string; name: string }
  title: string
  description: string | null
  status: ApprovalStatus
  requestedBy: string
  onBehalfOf: UserRef
  createdAt: string
  dueAt: string
  decidedBy: string | null
  decidedAt: string | null
  comment: string | null
  /** The current user may approve or reject it now. */
  canDecide: boolean
  /** Why not: decided, not_member (not in the group), own_request (four-eyes rule); null when they can. */
  decisionBlocker: "decided" | "not_member" | "own_request" | null
  allowSelfApproval: boolean
}

export interface DecisionRequest {
  comment?: string | null
}

import type { ReactNode } from "react"
import { createBrowserRouter, Navigate, useLocation } from "react-router"
import { Spinner } from "@/components/ui/spinner"
import { useCurrentUser } from "@/features/auth/queries/use-current-user"
import { ApprovalGroupsPage } from "@/features/approvals/pages/approval-groups-page"
import { ApprovalsPage } from "@/features/approvals/pages/approvals-page"
import { DashboardPage } from "@/features/dashboard/pages/dashboard-page"
import { LoginPage } from "@/features/auth/pages/login-page"
import { FlowDetailPage } from "@/features/flows/pages/flow-detail-page"
import { FlowEditorPage } from "@/features/flows/pages/flow-editor-page"
import { FlowsPage } from "@/features/flows/pages/flows-page"
import { RunsPage } from "@/features/flows/pages/runs-page"
import { DatasetBuilderPage } from "@/features/datasets/pages/dataset-builder-page"
import { SavedDatasetsPage } from "@/features/datasets/pages/saved-datasets-page"
import { TemplatesPage } from "@/features/templates/pages/templates-page"
import { AppLayout, NotFound } from "./app-layout"

function RequireSession({ children }: { children: ReactNode }) {
  const user = useCurrentUser()
  const location = useLocation()
  if (user.isPending) {
    return <div className="flex min-h-svh items-center justify-center"><Spinner /></div>
  }
  if (!user.data) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    element: <RequireSession><AppLayout /></RequireSession>,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: "datasets", element: <SavedDatasetsPage /> },
      { path: "datasets/new", element: <DatasetBuilderPage /> },
      { path: "datasets/:name", element: <DatasetBuilderPage /> },
      { path: "reports", element: <Navigate to="/datasets/new" replace /> },   // old links
      { path: "flows", element: <FlowsPage /> },
      { path: "flows/new", element: <FlowEditorPage /> },
      { path: "flows/:flowId", element: <FlowDetailPage /> },
      { path: "flows/:flowId/edit", element: <FlowEditorPage /> },
      { path: "approvals", element: <ApprovalsPage /> },
      { path: "approval-groups", element: <ApprovalGroupsPage /> },
      { path: "runs", element: <RunsPage /> },
      { path: "templates", element: <TemplatesPage /> },
      { path: "*", element: <NotFound /> },
    ],
  },
])

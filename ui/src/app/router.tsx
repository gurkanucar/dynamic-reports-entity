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
import { FlowImportPage } from "@/features/flows/pages/flow-import-page"
import { TemplateEditorPage } from "@/features/templates/pages/template-editor-page"
import { TemplatesPage } from "@/features/templates/pages/templates-page"
import { DepartmentsPage } from "@/features/admin/pages/departments-page"
import { PersonnelPage } from "@/features/admin/pages/personnel-page"
import { ProjectsPage } from "@/features/admin/pages/projects-page"
import { SkillsPage } from "@/features/admin/pages/skills-page"
import { TrainingRecordsPage } from "@/features/admin/pages/training-records-page"
import { TrainingsPage } from "@/features/admin/pages/trainings-page"
import { SurveyBuilderPage } from "@/features/surveys/pages/survey-builder-page"
import { SurveyFillPage } from "@/features/surveys/pages/survey-fill-page"
import { SurveyResultsPage } from "@/features/surveys/pages/survey-results-page"
import { SurveysPage } from "@/features/surveys/pages/surveys-page"
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
  // a survey to fill in: a page of its own (no menu), still behind the login
  { path: "/s/:surveyId", element: <RequireSession><SurveyFillPage /></RequireSession> },
  {
    element: <RequireSession><AppLayout /></RequireSession>,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: "datasets", element: <SavedDatasetsPage /> },
      { path: "datasets/new", element: <DatasetBuilderPage /> },
      { path: "datasets/:datasetId", element: <DatasetBuilderPage /> },
      { path: "reports", element: <Navigate to="/datasets/new" replace /> },   // old links
      { path: "flows", element: <FlowsPage /> },
      { path: "flows/new", element: <FlowEditorPage /> },
      { path: "flows/import", element: <FlowImportPage /> },
      { path: "flows/:flowId", element: <FlowDetailPage /> },
      { path: "flows/:flowId/edit", element: <FlowEditorPage /> },
      { path: "approvals", element: <ApprovalsPage /> },
      { path: "approval-groups", element: <ApprovalGroupsPage /> },
      { path: "runs", element: <RunsPage /> },
      { path: "templates", element: <TemplatesPage /> },
      { path: "templates/new", element: <TemplateEditorPage /> },
      { path: "templates/:templateId", element: <TemplateEditorPage /> },
      { path: "surveys", element: <SurveysPage /> },
      { path: "surveys/new", element: <SurveyBuilderPage /> },
      { path: "surveys/:surveyId", element: <SurveyResultsPage /> },
      { path: "surveys/:surveyId/edit", element: <SurveyBuilderPage /> },
      { path: "admin", element: <Navigate to="/admin/personnel" replace /> },
      { path: "admin/personnel", element: <PersonnelPage /> },
      { path: "admin/departments", element: <DepartmentsPage /> },
      { path: "admin/projects", element: <ProjectsPage /> },
      { path: "admin/trainings", element: <TrainingsPage /> },
      { path: "admin/training-records", element: <TrainingRecordsPage /> },
      { path: "admin/skills", element: <SkillsPage /> },
      { path: "*", element: <NotFound /> },
    ],
  },
])

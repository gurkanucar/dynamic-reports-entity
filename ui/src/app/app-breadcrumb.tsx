import { Fragment } from "react"
import { Link, useLocation } from "react-router"
import {
  Breadcrumb, BreadcrumbItem, BreadcrumbLink, BreadcrumbList, BreadcrumbPage, BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { useSavedDataset } from "@/features/datasets/queries/use-saved-datasets"
import { useFlow } from "@/features/flows/queries/use-flow"
import { useSurvey } from "@/features/surveys/queries/use-surveys"
import { shortId } from "@/shared/lib/format"

const SECTIONS: Record<string, string> = {
  datasets: "Veri setleri",
  flows: "Akışlar",
  runs: "Çalışmalar",
  approvals: "Onaylarım",
  "approval-groups": "Onay grupları",
  templates: "Mail şablonları",
  surveys: "Anketler",
  admin: "Kayıtlar",
}

/** Second level of /admin/... */
const ADMIN_PAGES: Record<string, string> = {
  personnel: "Personel", departments: "Departmanlar", projects: "Projeler", trainings: "Eğitimler",
  "training-records": "Eğitim kayıtları", skills: "Beceriler",
}

interface Crumb {
  label: string
  to?: string
}

/**
 * Where the page is: "Akışlar › Satın alma talebi › Düzenle". Names of flows and datasets come from the same queries
 * the pages use (already cached), so this costs no extra request.
 */
export function AppBreadcrumb() {
  const [section, id, action] = useLocation().pathname.split("/").filter(Boolean)
  const flowId = section === "flows" && id && id !== "new" ? id : ""
  const datasetName = section === "datasets" && id && id !== "new" ? decodeURIComponent(id) : undefined
  const flow = useFlow(flowId)
  const dataset = useSavedDataset(datasetName)
  const surveyId = section === "surveys" && id && id !== "new" ? id : null
  const survey = useSurvey(surveyId)

  if (!section || !SECTIONS[section]) return null
  const crumbs: Crumb[] = [{ label: SECTIONS[section], to: id ? `/${section}` : undefined }]
  if (section === "admin" && id && ADMIN_PAGES[id]) crumbs.push({ label: ADMIN_PAGES[id] })
  else if (id === "new") crumbs.push({ label: section === "flows" ? "Yeni akış" : section === "surveys" ? "Yeni anket" : "Yeni veri seti" })
  else if (surveyId) {
    crumbs.push({ label: survey.data?.title ?? `#${shortId(surveyId)}`, to: action ? `/surveys/${surveyId}` : undefined })
    if (action === "edit") crumbs.push({ label: "Düzenle" })
  }
  else if (flowId) {
    crumbs.push({ label: flow.data?.name ?? `#${shortId(flowId)}`, to: action ? `/flows/${flowId}` : undefined })
    if (action === "edit") crumbs.push({ label: "Düzenle" })
  } else if (datasetName) crumbs.push({ label: dataset.data?.label ?? datasetName })

  return (
    <Breadcrumb className="min-w-0">
      <BreadcrumbList className="flex-nowrap">
        {crumbs.map((crumb, index) => (
          <Fragment key={index}>
            {index > 0 && <BreadcrumbSeparator />}
            <BreadcrumbItem className="min-w-0">
              {crumb.to ? (
                <BreadcrumbLink asChild><Link to={crumb.to}>{crumb.label}</Link></BreadcrumbLink>
              ) : (
                <BreadcrumbPage className="truncate">{crumb.label}</BreadcrumbPage>
              )}
            </BreadcrumbItem>
          </Fragment>
        ))}
      </BreadcrumbList>
    </Breadcrumb>
  )
}

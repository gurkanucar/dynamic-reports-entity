import { ArrowLeftIcon, Trash2Icon } from "lucide-react"
import { Link, useNavigate, useParams } from "react-router"
import { toast } from "sonner"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { errorMessage } from "@/shared/api/http-client"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { TemplateEditor } from "../components/template-editor"
import { DEFAULT_DATASET_TEMPLATE } from "../lib/default-template"
import { useDeleteTemplate } from "../mutations/use-delete-template"
import { useTemplate } from "../queries/use-template"

/** /templates/new and /templates/:templateId: one template on its own page, with room for the editor and preview. */
export function TemplateEditorPage() {
  const id = useParams().templateId ?? null
  const navigate = useNavigate()
  const template = useTemplate(id)
  const remove = useDeleteTemplate()
  const back = (
    <Button variant="ghost" asChild><Link to="/templates"><ArrowLeftIcon data-icon="inline-start" />Şablonlar</Link></Button>
  )

  if (id === null) {
    return (
      <div className="flex flex-col gap-6">
        <PageHeader title="Yeni şablon" description="Hazır veri seti şablonundan başlar; asistana da yazdırabilirsiniz."
          actions={back} />
        <TemplateEditor key="new" template={null} initial={DEFAULT_DATASET_TEMPLATE}
          onSaved={(saved) => navigate(`/templates/${saved.id}`, { replace: true })} />
      </div>
    )
  }
  if (template.isPending) return <Skeleton className="h-96 w-full" />
  if (template.isError) return <ErrorAlert error={template.error} />
  const data = template.data
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={data.name} description={data.description ?? "Akışların gönderdiği mail şablonu."}
        actions={(
          <>
            {back}
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button variant="outline" disabled={remove.isPending}><Trash2Icon data-icon="inline-start" />Sil</Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>“{data.name}” silinsin mi?</AlertDialogTitle>
                  <AlertDialogDescription>Bir akış kullanıyorsa silinemez; önce o akışları değiştirin.</AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Vazgeç</AlertDialogCancel>
                  <AlertDialogAction onClick={() => remove.mutate(data.id, {
                    onSuccess: () => { toast.success("Şablon silindi"); navigate("/templates") },
                    onError: (error) => toast.error("Silinemedi", { description: errorMessage(error) }),
                  })}>Sil</AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </>
        )} />
      <TemplateEditor key={`${data.id}-${data.version}`} template={data} initial={data} onSaved={() => undefined} />
    </div>
  )
}

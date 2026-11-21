import { DatabaseIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { Link, useNavigate } from "react-router"
import { toast } from "sonner"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { useCurrentUser } from "@/features/auth/queries/use-current-user"
import { errorMessage } from "@/shared/api/http-client"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { formatDateTime } from "@/shared/lib/format"
import { useDeleteDataset } from "../mutations/use-save-dataset"
import { useSavedDatasets } from "../queries/use-saved-datasets"

/** /datasets: saved datasets, usable by name in flows. */
export function SavedDatasetsPage() {
  const datasets = useSavedDatasets()
  const remove = useDeleteDataset()
  const navigate = useNavigate()
  const user = useCurrentUser().data
  const admin = user?.authorities.includes("PLATFORM_ADMIN") ?? false

  return (
    <div className="flex min-w-0 flex-col gap-6">
      <PageHeader title="Veri setleri"
        description="Kaydettiğiniz veri setleri akışlarda adıyla, bir değişken gibi kullanılır."
        actions={<Button asChild><Link to="/datasets/new"><PlusIcon data-icon="inline-start" />Yeni veri seti</Link></Button>} />
      {datasets.isPending && <Skeleton className="h-40 w-full" />}
      {datasets.isError && <ErrorAlert error={datasets.error} />}
      {datasets.data?.length === 0 && (
        <Empty className="border">
          <EmptyHeader>
            <EmptyMedia variant="icon"><DatabaseIcon /></EmptyMedia>
            <EmptyTitle>Henüz kayıtlı veri seti yok</EmptyTitle>
            <EmptyDescription>Bir veri seti tasarlayıp “Kaydet” ile adlandırın; örneklerden de başlayabilirsiniz.</EmptyDescription>
          </EmptyHeader>
          <EmptyContent>
            <Button asChild><Link to="/datasets/new">Yeni veri seti</Link></Button>
          </EmptyContent>
        </Empty>
      )}
      {datasets.data && datasets.data.length > 0 && (
        <Card>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Ad</TableHead>
                  <TableHead>Kayıt türü</TableHead>
                  <TableHead>Sahibi</TableHead>
                  <TableHead>Güncellendi</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {datasets.data.map((dataset) => (
                  <TableRow key={dataset.name} className="cursor-pointer" onClick={() => navigate(`/datasets/${dataset.name}`)}>
                    <TableCell className="max-w-80">
                      <div className="truncate font-medium">{dataset.label ?? dataset.name}</div>
                      <div className="truncate font-mono text-xs text-muted-foreground">{dataset.name}</div>
                    </TableCell>
                    <TableCell>{dataset.definition.root}</TableCell>
                    <TableCell>{dataset.createdBy}</TableCell>
                    <TableCell className="tabular-nums">{formatDateTime(dataset.updatedAt)}</TableCell>
                    <TableCell onClick={(event) => event.stopPropagation()}>
                      {(admin || dataset.createdBy === user?.username) && (
                        <AlertDialog>
                          <AlertDialogTrigger asChild>
                            <Button variant="ghost" size="icon" aria-label="Sil"><Trash2Icon /></Button>
                          </AlertDialogTrigger>
                          <AlertDialogContent>
                            <AlertDialogHeader>
                              <AlertDialogTitle>“{dataset.label ?? dataset.name}” silinsin mi?</AlertDialogTitle>
                              <AlertDialogDescription>Bir akış kullanıyorsa silinemez; önce o akışları değiştirin.</AlertDialogDescription>
                            </AlertDialogHeader>
                            <AlertDialogFooter>
                              <AlertDialogCancel>Vazgeç</AlertDialogCancel>
                              <AlertDialogAction onClick={() => remove.mutate(dataset.name, {
                                onSuccess: () => toast.success("Veri seti silindi"),
                                onError: (error) => toast.error("Silinemedi", { description: errorMessage(error) }),
                              })}>Sil</AlertDialogAction>
                            </AlertDialogFooter>
                          </AlertDialogContent>
                        </AlertDialog>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

import { ChevronDownIcon, DownloadIcon, PlayIcon, RotateCcwIcon } from "lucide-react"
import { useCallback, useState } from "react"
import { useNavigate, useParams } from "react-router"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { ButtonGroup } from "@/components/ui/button-group"
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { errorMessage } from "@/shared/api/http-client"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { AssistantPanel } from "@/features/assistant/components/assistant-panel"
import { useAssistantSettings } from "@/features/assistant/queries/use-assistant-settings"
import { ScheduleDatasetDialog } from "@/features/flows/components/schedule-dataset-dialog"
import { DefinitionEditor } from "../components/definition-editor"
import { ExamplesPicker } from "../components/examples-picker"
import { SaveDatasetDialog } from "../components/save-dataset-dialog"
import { ResultsTable } from "../components/results-table"
import { cleanDefinition, emptyDefinition, isRunnable } from "../lib/definition"
import { useExportDataset } from "../mutations/use-export-dataset"
import { useRunDataset } from "../mutations/use-run-dataset"
import { useCatalog } from "../queries/use-catalog"
import { useSavedDataset } from "../queries/use-saved-datasets"
import type { SavedDatasetResponse } from "../types/saved-dataset.dto"
import type { DatasetDefinition, ExportFormat } from "../types/dataset.types"

const PAGE_SIZE = 50

/** /datasets/new and /datasets/:name (a saved one, updated in place). */
export function DatasetBuilderPage() {
  const name = useParams().name
  const saved = useSavedDataset(name)
  if (!name) return <DatasetBuilder key="new" saved={null} />
  if (saved.isPending) return <Skeleton className="h-96 w-full" />
  if (saved.isError) return <ErrorAlert error={saved.error} title="Veri seti yüklenemedi" />
  return <DatasetBuilder key={saved.data.name} saved={saved.data} />
}

/** Builds a dataset (a sample of records with chosen columns, filters and sorting), previews, saves and exports it. */
function DatasetBuilder({ saved }: { saved: SavedDatasetResponse | null }) {
  const navigate = useNavigate()
  const catalog = useCatalog()
  const assistant = useAssistantSettings()
  const run = useRunDataset()
  const exportFile = useExportDataset()
  const [definition, setDefinition] = useState<DatasetDefinition>(saved?.definition ?? emptyDefinition(""))
  const [title, setTitle] = useState(saved?.label ?? saved?.name ?? "Yeni veri seti")

  const execute = useCallback((target: DatasetDefinition, page = 0) => {
    if (isRunnable(target)) run.mutate({ definition: cleanDefinition(target), page, size: PAGE_SIZE })
  }, [run])

  /** A definition from the assistant replaces the editor content and runs at once (rows stay in the browser). */
  const applySuggestion = useCallback((suggested: DatasetDefinition) => {
    setDefinition(suggested)
    execute(suggested)
    toast.success("Asistanın önerisi uygulandı")
  }, [execute])

  const download = (format: ExportFormat) => exportFile.mutate(
    { definition: cleanDefinition(definition), fileName: `${fileNameFrom(title)}.${format}`, format },
    { onError: (error) => toast.error("İndirilemedi", { description: errorMessage(error) }) })

  const assistantEnabled = assistant.data?.enabled === true
  const cleaned = cleanDefinition(definition)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={saved ? title : "Yeni veri seti"}
        description={saved ? `Kayıtlı veri seti · akışlarda “${saved.name}” adıyla kullanılır`
          : "Kayıt türünü, kolonları ve filtreleri seçin ya da asistana ne istediğinizi yazın."}
        actions={(
          <>
            <ExamplesPicker onPick={(example) => {
              setDefinition(example.definition)
              setTitle(example.title)
              execute(example.definition)
            }} />
            <Button variant="ghost" onClick={() => { setDefinition(emptyDefinition("")); run.reset() }}>
              <RotateCcwIcon data-icon="inline-start" />Temizle
            </Button>
            <ButtonGroup aria-label="İndir">
              <Button variant="outline" disabled={!isRunnable(definition) || exportFile.isPending}
                onClick={() => download("xlsx")}>
                {exportFile.isPending ? <Spinner data-icon="inline-start" /> : <DownloadIcon data-icon="inline-start" />}Excel
              </Button>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="outline" size="icon" aria-label="Diğer biçimler" disabled={!isRunnable(definition)}>
                    <ChevronDownIcon />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuGroup>
                    <DropdownMenuItem onSelect={() => download("xlsx")}>Excel (.xlsx)</DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => download("csv")}>CSV (.csv)</DropdownMenuItem>
                  </DropdownMenuGroup>
                </DropdownMenuContent>
              </DropdownMenu>
            </ButtonGroup>
            <SaveDatasetDialog definition={cleaned} defaultLabel={title} saved={saved} disabled={!isRunnable(definition)}
              onSaved={(result) => { if (!saved) navigate(`/datasets/${result.name}`, { replace: true }) }} />
            <ScheduleDatasetDialog definition={cleaned} savedName={saved?.name} defaultName={title} disabled={!isRunnable(definition)} />
            <Button disabled={!isRunnable(definition) || run.isPending} onClick={() => execute(definition)}>
              {run.isPending ? <Spinner data-icon="inline-start" /> : <PlayIcon data-icon="inline-start" />}Çalıştır
            </Button>
          </>
        )} />

      <div className={assistantEnabled ? "grid items-start gap-6 xl:grid-cols-[minmax(20rem,26rem)_minmax(0,1fr)]" : "flex flex-col gap-6"}>
        {assistantEnabled && (
          <AssistantPanel definition={isRunnable(definition) ? cleaned : null} model={assistant.data?.model ?? null}
            onApply={(suggested) => { applySuggestion(suggested); setTitle(titleFor(suggested, catalog.data?.entities)) }} />
        )}
        <div className="flex min-w-0 flex-col gap-6">
          <Card>
            <CardHeader>
              <CardTitle>Tasarım</CardTitle>
              <CardDescription>Gördüğünüz alanlar ve satırlar yetkilerinize göre belirlenir.</CardDescription>
            </CardHeader>
            <CardContent>
              {catalog.isPending && <Skeleton className="h-40 w-full" />}
              {catalog.isError && <ErrorAlert error={catalog.error} title="Katalog yüklenemedi" />}
              {catalog.data && <DefinitionEditor catalog={catalog.data} definition={definition} onChange={setDefinition} />}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Önizleme</CardTitle>
              <CardDescription>İlk {PAGE_SIZE} satır; tamamı için Excel olarak indirin.</CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              {run.isError && <ErrorAlert error={run.error} title="Veri seti oluşturulamadı" />}
              {run.isPending && <Skeleton className="h-32 w-full" />}
              {run.data && !run.isPending && <ResultsTable result={run.data} onPage={(page) => execute(definition, page)} />}
              {!run.data && !run.isPending && !run.isError && (
                <p className="text-sm text-muted-foreground">Veri setini görmek için “Çalıştır”a basın.</p>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}

/** "Zorunlu eğitim uyumu" → "zorunlu-egitim-uyumu" for the downloaded file. */
function fileNameFrom(title: string): string {
  return title.toLocaleLowerCase("tr-TR").normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/ı/g, "i")
    .replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 60) || "veri-seti"
}

function titleFor(definition: DatasetDefinition, entities?: { name: string; label: string }[]): string {
  const root = entities?.find((entity) => entity.name === definition.root)?.label ?? definition.root
  return `${root} veri seti`
}

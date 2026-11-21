import { MailIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { useState } from "react"
import { useSearchParams } from "react-router"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Item, ItemContent, ItemDescription, ItemGroup, ItemMedia, ItemTitle } from "@/components/ui/item"
import { cn } from "@/lib/utils"
import { formatDateTime } from "@/shared/lib/format"
import { errorMessage } from "@/shared/api/http-client"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { SearchInput } from "@/shared/components/search-input"
import { useListState } from "@/shared/lib/list-state"
import { TemplateEditor } from "../components/template-editor"
import { DEFAULT_DATASET_TEMPLATE } from "../lib/default-template"
import { useDeleteTemplate } from "../mutations/use-delete-template"
import { useTemplate } from "../queries/use-template"
import { useTemplatePage } from "../queries/use-templates"
import { Pager } from "@/shared/components/pager"

const NEW = "new"

export function TemplatesPage() {
  const list = useListState("name")
  const page = useTemplatePage(list.page, list.size, list.query || undefined)
  const templates = { ...page, data: page.data?.data }
  const [params] = useSearchParams()
  // "?new=1" (the help chat hands a request over) opens a new template with its assistant
  const [selected, setSelected] = useState<string | null>(params.get("new") ? NEW : null)
  const selectedId = selected && selected !== NEW ? Number(selected) : null
  const template = useTemplate(selectedId)
  const remove = useDeleteTemplate()

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Mail şablonları" description="Akışların gönderdiği maillerin konusu ve içeriği."
        actions={(
          <>
            {selectedId !== null && (
              <Button variant="ghost" disabled={remove.isPending} onClick={() => remove.mutate(selectedId, {
                onSuccess: () => { toast.success("Şablon silindi"); setSelected(null) },
                onError: (error) => toast.error("Silinemedi", { description: errorMessage(error) }),
              })}><Trash2Icon data-icon="inline-start" />Sil</Button>
            )}
            <Button onClick={() => setSelected(NEW)}><PlusIcon data-icon="inline-start" />Yeni şablon</Button>
          </>
        )} />
      <SearchInput value={list.search} onChange={list.setSearch} placeholder="Ad, konu veya açıklama ara…" />
      {templates.isPending && <Skeleton className="h-10 w-full" />}
      {templates.data && templates.data.length === 0 && list.query && (
        <p className="text-sm text-muted-foreground">“{list.query}” ile eşleşen şablon yok.</p>
      )}
      {templates.isError && <ErrorAlert error={templates.error} />}
      {templates.data && templates.data.length > 0 && (
        <ItemGroup className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
          {templates.data.map((item) => {
            const active = selected === item.id.toString()
            return (
              <Item key={item.id} variant={active ? "muted" : "outline"} size="sm" asChild
                className={cn("text-left", active && "border-primary/40")}>
                <button type="button" aria-pressed={active} onClick={() => setSelected(item.id.toString())}>
                  <ItemMedia variant="icon"><MailIcon /></ItemMedia>
                  <ItemContent className="min-w-0">
                    <ItemTitle className="w-full truncate">{item.name}</ItemTitle>
                    <ItemDescription className="truncate">
                      {item.description ?? "Açıklama yok"} · {item.owner.username} · {formatDateTime(item.updatedAt)}
                    </ItemDescription>
                  </ItemContent>
                </button>
              </Item>
            )
          })}
        </ItemGroup>
      )}
      {page.data && <Pager meta={page.data.meta} onPage={list.setPage} onSize={list.setSize} />}
      {selected === null && (
        <Empty>
          <EmptyHeader>
            <EmptyMedia variant="icon"><MailIcon /></EmptyMedia>
            <EmptyTitle>Bir şablon seçin</EmptyTitle>
            <EmptyDescription>Ya da hazır veri seti şablonundan başlayan yeni bir şablon oluşturun.</EmptyDescription>
          </EmptyHeader>
        </Empty>
      )}
      {selected === NEW && (
        <TemplateEditor key="new" template={null} initial={DEFAULT_DATASET_TEMPLATE}
          onSaved={(saved) => setSelected(saved.id.toString())} />
      )}
      {selectedId !== null && template.isPending && <Skeleton className="h-96 w-full" />}
      {selectedId !== null && template.data && (
        <TemplateEditor key={`${template.data.id}-${template.data.version}`} template={template.data}
          initial={template.data} onSaved={(saved) => setSelected(saved.id.toString())} />
      )}
    </div>
  )
}

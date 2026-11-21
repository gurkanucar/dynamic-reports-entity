import { MailIcon, PlusIcon } from "lucide-react"
import { Link, Navigate, useSearchParams } from "react-router"
import { Button } from "@/components/ui/button"
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Item, ItemContent, ItemDescription, ItemGroup, ItemMedia, ItemTitle } from "@/components/ui/item"
import { Skeleton } from "@/components/ui/skeleton"
import { ErrorAlert } from "@/shared/components/error-alert"
import { PageHeader } from "@/shared/components/page-header"
import { Pager } from "@/shared/components/pager"
import { SearchInput } from "@/shared/components/search-input"
import { formatDateTime } from "@/shared/lib/format"
import { useListState } from "@/shared/lib/list-state"
import { useTemplatePage } from "../queries/use-templates"

/** /templates: the list; a template opens on its own page (/templates/:id, /templates/new). */
export function TemplatesPage() {
  const [params] = useSearchParams()
  const list = useListState("name")
  const page = useTemplatePage(list.page, list.size, list.query || undefined)

  // old links and the help chat's hand-over (?new=1&ask=...) go to the new-template page
  if (params.get("new")) {
    const ask = params.get("ask")
    return <Navigate to={`/templates/new${ask ? `?ask=${encodeURIComponent(ask)}` : ""}`} replace />
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Mail şablonları" description="Akışların gönderdiği maillerin konusu ve içeriği."
        actions={<Button asChild><Link to="/templates/new"><PlusIcon data-icon="inline-start" />Yeni şablon</Link></Button>} />
      <SearchInput value={list.search} onChange={list.setSearch} placeholder="Ad, konu veya açıklama ara…" />
      {page.isPending && <Skeleton className="h-24 w-full" />}
      {page.isError && <ErrorAlert error={page.error} />}
      {page.data && page.data.data.length === 0 && (
        <Empty className="border">
          <EmptyHeader>
            <EmptyMedia variant="icon"><MailIcon /></EmptyMedia>
            <EmptyTitle>{list.query ? `“${list.query}” ile eşleşen şablon yok` : "Henüz şablon yok"}</EmptyTitle>
            <EmptyDescription>Hazır veri seti şablonundan başlayan yeni bir şablon oluşturun.</EmptyDescription>
          </EmptyHeader>
          <EmptyContent><Button asChild><Link to="/templates/new">Yeni şablon</Link></Button></EmptyContent>
        </Empty>
      )}
      {page.data && page.data.data.length > 0 && (
        <ItemGroup className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3" data-agent-target="templates">
          {page.data.data.map((item) => (
            <Item key={item.id} variant="outline" size="sm" asChild className="text-left hover:border-primary/40"
              data-agent-row={item.id}>
              <Link to={`/templates/${item.id}`}>
                <ItemMedia variant="icon"><MailIcon /></ItemMedia>
                <ItemContent className="min-w-0">
                  <ItemTitle className="w-full truncate">{item.name}</ItemTitle>
                  <ItemDescription className="truncate">
                    {item.description ?? "Açıklama yok"} · {item.owner.username} · {formatDateTime(item.updatedAt)}
                  </ItemDescription>
                </ItemContent>
              </Link>
            </Item>
          ))}
        </ItemGroup>
      )}
      {page.data && <Pager meta={page.data.meta} onPage={list.setPage} onSize={list.setSize} />}
    </div>
  )
}

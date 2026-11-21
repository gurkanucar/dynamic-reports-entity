import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react"
import { Button } from "@/components/ui/button"
import type { PageMeta } from "@/shared/types/api.types"

/** Pages of the platform APIs are 1-based. */
export function Pager({ meta, onPage }: { meta: PageMeta | null | undefined; onPage: (page: number) => void }) {
  if (!meta || meta.totalPages <= 1) return null
  return (
    <div className="flex items-center justify-end gap-2 text-sm text-muted-foreground">
      <span>Sayfa {meta.page} / {meta.totalPages} · {meta.totalItems} kayıt</span>
      <Button variant="outline" size="icon" aria-label="Önceki sayfa" disabled={meta.page <= 1}
        onClick={() => onPage(meta.page - 1)}><ChevronLeftIcon /></Button>
      <Button variant="outline" size="icon" aria-label="Sonraki sayfa" disabled={meta.page >= meta.totalPages}
        onClick={() => onPage(meta.page + 1)}><ChevronRightIcon /></Button>
    </div>
  )
}

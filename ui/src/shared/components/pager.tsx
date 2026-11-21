import { ChevronLeftIcon, ChevronRightIcon, ChevronsLeftIcon, ChevronsRightIcon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { PageMeta } from "@/shared/types/api.types"

export const PAGE_SIZES = [20, 50, 100]

/**
 * Pages of the platform APIs (1-based): the total, the page size (20 / 50 / 100) and first / previous / next / last.
 * Shown whenever there is a list, also when it fits on one page, so the total is always visible.
 */
export function Pager({ meta, onPage, onSize }: {
  meta: PageMeta | null | undefined
  onPage: (page: number) => void
  /** Lets people choose the page size; without it the size is fixed. */
  onSize?: (size: number) => void
}) {
  if (!meta) return null
  const pages = Math.max(meta.totalPages, 1)
  const from = meta.totalItems === 0 ? 0 : (meta.page - 1) * meta.size + 1
  const to = Math.min(meta.page * meta.size, meta.totalItems)
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground">
      <span className="tabular-nums">
        Toplam <b className="text-foreground">{meta.totalItems.toLocaleString("tr-TR")}</b> kayıt
        {meta.totalItems > 0 && <> · {from.toLocaleString("tr-TR")}–{to.toLocaleString("tr-TR")} gösteriliyor</>}
      </span>
      <div className="flex items-center gap-2">
        {onSize && (
          <div className="flex items-center gap-2">
            <span className="hidden sm:inline">Sayfa başına</span>
            <Select value={String(meta.size)} onValueChange={(value) => onSize(Number(value))}>
              <SelectTrigger size="sm" className="w-20" aria-label="Sayfa başına kayıt"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {PAGE_SIZES.map((size) => <SelectItem key={size} value={String(size)}>{size}</SelectItem>)}
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>
        )}
        <span className="tabular-nums">Sayfa {meta.page} / {pages}</span>
        <div className="flex gap-1">
          <Button variant="outline" size="icon-sm" aria-label="İlk sayfa" disabled={meta.page <= 1} onClick={() => onPage(1)}>
            <ChevronsLeftIcon />
          </Button>
          <Button variant="outline" size="icon-sm" aria-label="Önceki sayfa" disabled={meta.page <= 1} onClick={() => onPage(meta.page - 1)}>
            <ChevronLeftIcon />
          </Button>
          <Button variant="outline" size="icon-sm" aria-label="Sonraki sayfa" disabled={meta.page >= pages} onClick={() => onPage(meta.page + 1)}>
            <ChevronRightIcon />
          </Button>
          <Button variant="outline" size="icon-sm" aria-label="Son sayfa" disabled={meta.page >= pages} onClick={() => onPage(pages)}>
            <ChevronsRightIcon />
          </Button>
        </div>
      </div>
    </div>
  )
}

/** Pages a list the server returns whole (approval groups, saved datasets): the same pager, in the browser. */
export function pageLocally<T>(items: T[], page: number, size: number): { rows: T[]; meta: PageMeta } {
  const totalPages = Math.max(1, Math.ceil(items.length / size))
  const current = Math.min(page, totalPages)
  return {
    rows: items.slice((current - 1) * size, current * size),
    meta: { page: current, size, totalItems: items.length, totalPages },
  }
}

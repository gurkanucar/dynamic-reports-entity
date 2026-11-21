import { ArrowDownIcon, ArrowUpDownIcon, ArrowUpIcon } from "lucide-react"
import type { ReactNode } from "react"
import { TableHead } from "@/components/ui/table"
import { cn } from "@/lib/utils"

/**
 * A column header that sorts the server-side list: `sort` is the API's `sort` parameter (`-createdAt`). The first
 * click sorts descending for dates and numbers (`descFirst`), ascending for text.
 */
export function SortableHead({ field, sort, onSort, descFirst = false, className, children }: {
  field: string
  sort: string
  onSort: (sort: string) => void
  descFirst?: boolean
  className?: string
  children: ReactNode
}) {
  const active = sort === field || sort === `-${field}`
  const descending = sort === `-${field}`
  const next = active ? (descending ? field : `-${field}`) : (descFirst ? `-${field}` : field)
  const Icon = !active ? ArrowUpDownIcon : descending ? ArrowDownIcon : ArrowUpIcon
  return (
    <TableHead className={className} aria-sort={active ? (descending ? "descending" : "ascending") : "none"}>
      <button type="button" onClick={() => onSort(next)}
        className={cn("-mx-1 inline-flex items-center gap-1 rounded px-1 hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none",
          active ? "text-foreground" : "text-muted-foreground")}>
        {children}
        <Icon className={cn("size-3.5", !active && "opacity-50")} />
      </button>
    </TableHead>
  )
}

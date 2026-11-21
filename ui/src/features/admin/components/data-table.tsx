import type { ReactNode } from "react"
import { Card, CardContent } from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { cn } from "@/lib/utils"
import { ErrorAlert } from "@/shared/components/error-alert"
import { Pager } from "@/shared/components/pager"
import { SortableHead } from "@/shared/components/sortable-head"
import type { PageMeta } from "@/shared/types/api.types"
import type { LucideIcon } from "lucide-react"

export interface Column<T> {
  key: string
  header: string
  /** The API's sort field; absent = not sortable. */
  sort?: string
  /** Sort descending on the first click (dates, numbers). */
  descFirst?: boolean
  className?: string
  cell: (row: T) => ReactNode
}

/**
 * A server-side list: sortable headers, empty and loading states, pagination. Rows open `onOpen` (the edit
 * sheet) on click.
 */
export function DataTable<T extends { id: number }>({ columns, data, meta, loading, error, sort, onSort, onPage, onSize,
  onOpen, filtered, empty }: {
  columns: Column<T>[]
  data: T[] | undefined
  meta: PageMeta | null | undefined
  loading: boolean
  error: unknown
  sort: string
  onSort: (sort: string) => void
  onPage: (page: number) => void
  onSize?: (size: number) => void
  onOpen?: (row: T) => void
  /** A filter or search is on: the empty state says so. */
  filtered: boolean
  empty: { icon: LucideIcon; title: string; description: string }
}) {
  if (error) return <ErrorAlert error={error} />
  if (loading && !data) return <Skeleton className="h-72 w-full" />
  if (data && data.length === 0) {
    return (
      <Empty className="border">
        <EmptyHeader>
          <EmptyMedia variant="icon"><empty.icon /></EmptyMedia>
          <EmptyTitle>{filtered ? "Bu filtreye uyan kayıt yok" : empty.title}</EmptyTitle>
          <EmptyDescription>{filtered ? "Aramayı ya da filtreleri değiştirin." : empty.description}</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }
  return (
    <div className="flex flex-col gap-3">
      <Card className={cn("py-0 transition-opacity", loading && "opacity-60")}>
        <CardContent className="overflow-x-auto px-0">
          <Table>
            <TableHeader>
              <TableRow>
                {columns.map((column, index) => {
                  const className = cn(column.className, index === 0 && "pl-4", index === columns.length - 1 && "pr-4")
                  return column.sort ? (
                    <SortableHead key={column.key} field={column.sort} sort={sort} onSort={onSort}
                      descFirst={column.descFirst} className={className}>{column.header}</SortableHead>
                  ) : <TableHead key={column.key} className={className}>{column.header}</TableHead>
                })}
              </TableRow>
            </TableHeader>
            <TableBody>
              {data?.map((row) => (
                <TableRow key={row.id} className={onOpen ? "cursor-pointer" : undefined} onClick={() => onOpen?.(row)}>
                  {columns.map((column, index) => (
                    <TableCell key={column.key} className={cn(column.className, index === 0 && "pl-4",
                      index === columns.length - 1 && "pr-4")}>{column.cell(row)}</TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      <Pager meta={meta} onPage={onPage} onSize={onSize} />
    </div>
  )
}

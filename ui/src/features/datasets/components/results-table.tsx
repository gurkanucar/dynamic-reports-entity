import { ChevronLeftIcon, ChevronRightIcon, TableIcon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { cn } from "@/lib/utils"
import { formatCell } from "@/shared/lib/format"
import type { DatasetResultResponse } from "../types/dataset.dto"

export function ResultsTable({ result, onPage }: { result: DatasetResultResponse; onPage: (page: number) => void }) {
  if (result.rows.length === 0) {
    return (
      <Empty>
        <EmptyHeader>
          <EmptyMedia variant="icon"><TableIcon /></EmptyMedia>
          <EmptyTitle>Kayıt yok</EmptyTitle>
          <EmptyDescription>Filtrelere uyan kayıt bulunamadı.</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }
  const first = result.page * result.size + 1
  // an empty cell in a column reached through relations: the chain stopped early, it is not an error
  const emptyRelation = result.columns.find((column, index) => column.field?.includes(".")
    && result.rows.some((row) => row[index] === null || row[index] === undefined))
  return (
    <div className="flex flex-col gap-2">
      <div className="overflow-x-auto rounded-md border">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/60 hover:bg-muted/60">
              {result.columns.map((column, index) => (
                <TableHead key={index} className={cn("border-r font-semibold last:border-r-0",
                  column.type === "NUMBER" && "text-right")}>{column.label}</TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {result.rows.map((row, rowIndex) => (
              <TableRow key={rowIndex}>
                {row.map((cell, cellIndex) => (
                  <TableCell key={cellIndex} className={cn("border-r last:border-r-0",
                    result.columns[cellIndex]?.type === "NUMBER" && "text-right tabular-nums")}>
                    {cell === null || cell === undefined
                      ? <span className="text-muted-foreground/60" title="Değer yok">—</span>
                      : formatCell(cell)}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      {emptyRelation && (
        <p className="text-xs text-muted-foreground">
          “—” hata değil: o kayıtta ilişki zinciri erken bitiyor, yani “{emptyRelation.label}” için bağlı bir kayıt yok.
          Yalnızca dolu olanları görmek için bu alana “dolu” filtresi ekleyin.
        </p>
      )}
      <div className="flex items-center justify-between gap-2 text-sm text-muted-foreground">
        <span>
          {first}–{first + result.count - 1}{result.total !== null ? ` / ${result.total.toLocaleString("tr-TR")}` : ""} kayıt
          {result.truncatedLists.length > 0 && ` · kısaltılan listeler: ${result.truncatedLists.join(", ")}`}
        </span>
        <div className="flex gap-1">
          <Button variant="outline" size="icon" aria-label="Önceki sayfa" disabled={result.page === 0}
            onClick={() => onPage(result.page - 1)}><ChevronLeftIcon /></Button>
          <Button variant="outline" size="icon" aria-label="Sonraki sayfa" disabled={!result.hasNext}
            onClick={() => onPage(result.page + 1)}><ChevronRightIcon /></Button>
        </div>
      </div>
    </div>
  )
}

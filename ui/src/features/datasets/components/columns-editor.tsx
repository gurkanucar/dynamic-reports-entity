import { GripVerticalIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { useState } from "react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { AGGREGATE_LABELS, DATE_GROUP_LABELS, findPath, type FieldPath } from "../lib/field-paths"
import type { Aggregate, DateGroup, DatasetColumn } from "../types/dataset.types"
import { FieldPicker } from "./field-picker"

const NONE = "__none__"

export function ColumnsEditor({ columns, paths, onChange }: {
  columns: DatasetColumn[]
  paths: FieldPath[]
  onChange: (columns: DatasetColumn[]) => void
}) {
  const update = (index: number, change: Partial<DatasetColumn>) =>
    onChange(columns.map((column, i) => {
      if (i !== index) return column
      const next: DatasetColumn = { ...column, ...change }
      Object.keys(next).forEach((key) => next[key] === undefined && delete next[key])
      return next
    }))

  // order by drag and drop (grip only, so typing in the row still works) or the arrow keys on the grip
  const [dragging, setDragging] = useState<number | null>(null)
  const [over, setOver] = useState<number | null>(null)
  const move = (from: number, to: number) => {
    if (from === to || to < 0 || to >= columns.length) return
    const next = [...columns]
    const [moved] = next.splice(from, 1)
    next.splice(to, 0, moved)
    onChange(next)
  }

  return (
    <div className="flex flex-col gap-2">
      {columns.map((column, index) => {
        const path = findPath(paths, column.field)
        const isDate = path?.type === "DATE" || path?.type === "DATE_TIME"
        return (
          <div key={index} draggable={dragging === index}
            onDragStart={(event) => { event.dataTransfer.effectAllowed = "move"; event.dataTransfer.setData("text/plain", String(index)) }}
            onDragOver={(event) => { if (dragging !== null) { event.preventDefault(); setOver(index) } }}
            onDrop={(event) => { event.preventDefault(); if (dragging !== null) move(dragging, index); setDragging(null); setOver(null) }}
            onDragEnd={() => { setDragging(null); setOver(null) }}
            className={cn("grid grid-cols-[auto_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_auto] gap-2 rounded-md @2xl:grid-cols-[auto_minmax(0,2fr)_9rem_8rem_minmax(0,1fr)_auto]",
              dragging === index && "opacity-50",
              over === index && dragging !== null && dragging !== index && (dragging < index
                ? "shadow-[0_2px_0_0_var(--primary)]" : "shadow-[0_-2px_0_0_var(--primary)]"))}>
            <Button variant="ghost" size="icon" className="cursor-grab active:cursor-grabbing row-span-2 @2xl:row-span-1"
              aria-label={`Kolon ${index + 1}: sürükleyin ya da ok tuşlarıyla taşıyın`} title="Sürükleyerek sırala"
              onPointerDown={() => setDragging(index)} onPointerUp={() => setDragging(null)}
              onKeyDown={(event) => {
                if (event.key === "ArrowUp") { event.preventDefault(); move(index, index - 1) }
                if (event.key === "ArrowDown") { event.preventDefault(); move(index, index + 1) }
              }}>
              <GripVerticalIcon />
            </Button>
            <FieldPicker paths={paths} value={column.field} allowEmpty="(alan yok — satır sayısı)" className="col-span-4 @2xl:col-span-1"
              onChange={(field) => update(index, { field, dateGroup: undefined })} />
            <Select value={column.aggregate ?? NONE}
              onValueChange={(value) => update(index, { aggregate: value === NONE ? undefined : value as Aggregate })}>
              <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem value={NONE}>Değer</SelectItem>
                  {Object.entries(AGGREGATE_LABELS).map(([value, label]) => (
                    <SelectItem key={value} value={value}>{label}</SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
            <Select value={column.dateGroup ?? NONE} disabled={!isDate}
              onValueChange={(value) => update(index, { dateGroup: value === NONE ? undefined : value as DateGroup })}>
              <SelectTrigger className="w-full"><SelectValue placeholder="Tarih" /></SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem value={NONE}>Tarih olduğu gibi</SelectItem>
                  {Object.entries(DATE_GROUP_LABELS).map(([value, label]) => (
                    <SelectItem key={value} value={value}>{label}</SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
            <Input placeholder="Başlık (isteğe bağlı)" value={column.label ?? ""}
              onChange={(e) => update(index, { label: e.target.value || undefined })} />
            <Button variant="ghost" size="icon" aria-label="Kolonu sil"
              onClick={() => onChange(columns.filter((_, i) => i !== index))}>
              <Trash2Icon />
            </Button>
          </div>
        )
      })}
      <div>
        <Button variant="outline" size="sm" onClick={() => onChange([...columns, {}])}>
          <PlusIcon data-icon="inline-start" />
          Kolon ekle
        </Button>
      </div>
    </div>
  )
}

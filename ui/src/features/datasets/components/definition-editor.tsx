import { Field, FieldDescription, FieldGroup, FieldLabel, FieldLegend, FieldSet } from "@/components/ui/field"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useMemo } from "react"
import { emptyDefinition, isGrouped } from "../lib/definition"
import { fieldPaths } from "../lib/field-paths"
import type { CatalogResponse } from "../types/dataset.dto"
import type { DatasetDefinition } from "../types/dataset.types"
import { ColumnsEditor } from "./columns-editor"
import { FiltersEditor } from "./filters-editor"
import { JsonEditor } from "./json-editor"
import { SortEditor } from "./sort-editor"

export function DefinitionEditor({ catalog, definition, onChange }: {
  catalog: CatalogResponse
  definition: DatasetDefinition
  onChange: (definition: DatasetDefinition) => void
}) {
  const paths = useMemo(() => fieldPaths(catalog, definition.root), [catalog, definition.root])
  return (
    // @container: rows lay out by the editor's own width (narrow next to the assistant, wide without it)
    <Tabs defaultValue="design" className="@container">
      <TabsList>
        <TabsTrigger value="design">Tasarım</TabsTrigger>
        <TabsTrigger value="json">JSON</TabsTrigger>
      </TabsList>
      <TabsContent value="design">
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="dataset-root">Veri seti hangi kayıtlardan oluşsun?</FieldLabel>
            <Select key={definition.root ? "chosen" : "empty"} value={definition.root || undefined} onValueChange={(root) => onChange(emptyDefinition(root))}>
              <SelectTrigger id="dataset-root" className="w-full @md:w-72"><SelectValue placeholder="Kayıt türü seçin" /></SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {catalog.entities.map((entity) => (
                    <SelectItem key={entity.name} value={entity.name}>{entity.label}</SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </Field>
          {definition.root && (
            <>
              <FieldSet>
                <FieldLegend>Kolonlar</FieldLegend>
                <ColumnsEditor columns={definition.columns} paths={paths}
                  onChange={(columns) => onChange({ ...definition, columns })} />
              </FieldSet>
              <FieldSet>
                <FieldLegend>Filtreler</FieldLegend>
                <FiltersEditor where={definition.where} paths={paths}
                  onChange={(where) => onChange({ ...definition, where })} />
              </FieldSet>
              {(isGrouped(definition) || definition.having) && (
                <FieldSet>
                  <FieldLegend>Grup filtreleri</FieldLegend>
                  <FieldDescription>
                    Gruplanmış sonuçlara uygulanır, ör. “Sayı büyüktür 10”: yalnızca 10'dan fazla kaydı olan gruplar.
                  </FieldDescription>
                  <FiltersEditor having where={definition.having} paths={paths}
                    onChange={(having) => onChange({ ...definition, having })} />
                </FieldSet>
              )}
              <FieldSet>
                <FieldLegend>Sıralama</FieldLegend>
                <SortEditor sort={definition.sort ?? []} paths={paths}
                  onChange={(sort) => onChange({ ...definition, sort })} />
              </FieldSet>
            </>
          )}
        </FieldGroup>
      </TabsContent>
      <TabsContent value="json">
        <JsonEditor definition={definition} onChange={onChange} />
      </TabsContent>
    </Tabs>
  )
}

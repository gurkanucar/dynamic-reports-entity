import { InfoIcon, ListPlusIcon, PlusIcon, Trash2Icon } from "lucide-react"
import { useId, useState } from "react"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Field, FieldDescription, FieldError } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { cn } from "@/lib/utils"
import {
  fromLogic, isGroupRule, MAX_GROUP_DEPTH, OPERATORS, toLogic, type RuleOperator, type SimpleCondition, type SimpleRule,
} from "../lib/json-logic"
import type { JsonLogicRule } from "../types/flow.dto"

/**
 * A rule (JSON Logic) as "all/any of: path · operator · value" rows, with the raw JSON one tab away for anything
 * the rows cannot express. `variables` are suggested paths (e.g. steps.dataset.rowCount, trigger.amount).
 */
export function JsonLogicEditor({ value, onChange, variables }: {
  value: JsonLogicRule
  onChange: (value: JsonLogicRule) => void
  variables: string[]
}) {
  const listId = useId()
  const simple = fromLogic(value)
  const [json, setJson] = useState(() => JSON.stringify(value, null, 2))
  const [jsonError, setJsonError] = useState<string | null>(null)
  const [shown, setShown] = useState(value)
  if (shown !== value) {   // follow changes made with the rows
    setShown(value)
    try {
      if (JSON.stringify(JSON.parse(json)) !== JSON.stringify(value)) setJson(JSON.stringify(value, null, 2))
    } catch {
      setJson(JSON.stringify(value, null, 2))
    }
  }

  const update = (condition: SimpleCondition) => onChange(toLogic(condition))

  return (
    <Tabs defaultValue={simple ? "rows" : "json"}>
      <TabsList>
        <TabsTrigger value="rows">Kurallar</TabsTrigger>
        <TabsTrigger value="json">JSON</TabsTrigger>
      </TabsList>
      <TabsContent value="rows" className="flex flex-col gap-2">
        <datalist id={listId}>{variables.map((path) => <option key={path} value={path} />)}</datalist>
        {!simple ? (
          <Alert>
            <InfoIcon />
            <AlertDescription>Bu kural satırlarla gösterilemiyor; JSON sekmesinden düzenleyin.</AlertDescription>
          </Alert>
        ) : (
          <RuleGroup group={simple} variables={variables} listId={listId} depth={1} onChange={update} />
        )}
      </TabsContent>
      <TabsContent value="json">
        <Field data-invalid={jsonError ? true : undefined}>
          <Textarea className="min-h-40 font-mono text-xs" spellCheck={false} value={json} aria-invalid={!!jsonError}
            onChange={(e) => {
              setJson(e.target.value)
              try {
                const parsed = JSON.parse(e.target.value) as JsonLogicRule
                setJsonError(null)
                setShown(parsed)
                onChange(parsed)
              } catch (error) {
                setJsonError((error as Error).message)
              }
            }} />
          {jsonError ? <FieldError>{jsonError}</FieldError>
            : <FieldDescription>JSON Logic (jsonlogic.com): ör. {`{"and": [{">": [{"var": "trigger.amount"}, 100]}]}`}</FieldDescription>}
        </Field>
      </TabsContent>
    </Tabs>
  )
}

/** "Hepsi / herhangi biri" of rows and nested groups. */
function RuleGroup({ group, variables, listId, depth, onChange, onRemove }: {
  group: SimpleCondition
  variables: string[]
  listId: string
  depth: number
  onChange: (group: SimpleCondition) => void
  onRemove?: () => void
}) {
  const set = (index: number, rule: SimpleRule | SimpleCondition) =>
    onChange({ ...group, rules: group.rules.map((r, i) => (i === index ? rule : r)) })
  const remove = (index: number) => onChange({ ...group, rules: group.rules.filter((_, i) => i !== index) })
  const newRule: SimpleRule = { path: variables[0] ?? "", op: "==", value: "" }
  return (
    <div className={cn("flex min-w-0 flex-col gap-2", depth > 1 && "rounded-md border border-dashed p-2")}>
      <div className="flex flex-wrap items-center gap-2">
        <ToggleGroup type="single" variant="outline" size="sm" className="max-w-full flex-wrap" value={group.logic}
          onValueChange={(logic) => logic && onChange({ ...group, logic: logic as "and" | "or" })}>
          <ToggleGroupItem value="and">Hepsi sağlansın</ToggleGroupItem>
          <ToggleGroupItem value="or">Herhangi biri</ToggleGroupItem>
        </ToggleGroup>
        {onRemove && (
          <Button variant="ghost" size="sm" className="ml-auto" onClick={onRemove}>
            <Trash2Icon data-icon="inline-start" />Grubu sil
          </Button>
        )}
      </div>
      {group.rules.map((rule, index) => isGroupRule(rule) ? (
        <RuleGroup key={index} group={rule} variables={variables} listId={listId} depth={depth + 1}
          onChange={(next) => set(index, next)} onRemove={() => remove(index)} />
      ) : (
        <div key={index} className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] gap-2">
          <Input className="col-span-full font-mono text-xs" list={listId} placeholder="steps.dataset.rowCount"
            value={rule.path} aria-label="Alan" onChange={(e) => set(index, { ...rule, path: e.target.value })} />
          <Select value={rule.op} onValueChange={(op) => set(index, { ...rule, op: op as RuleOperator })}>
            <SelectTrigger className="w-full" aria-label="Operatör"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {OPERATORS.map((op) => <SelectItem key={op.value} value={op.value}>{op.label}</SelectItem>)}
              </SelectGroup>
            </SelectContent>
          </Select>
          <Input value={rule.value} disabled={rule.op === "exists"} aria-label="Değer"
            placeholder={rule.op === "in" ? "a; b; c" : "değer"}
            onChange={(e) => set(index, { ...rule, value: e.target.value })} />
          <Button variant="ghost" size="icon" aria-label="Kuralı sil" onClick={() => remove(index)}>
            <Trash2Icon />
          </Button>
        </div>
      ))}
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" onClick={() => onChange({ ...group, rules: [...group.rules, newRule] })}>
          <PlusIcon data-icon="inline-start" />Kural ekle
        </Button>
        {depth < MAX_GROUP_DEPTH && (
          <Button variant="outline" size="sm" onClick={() => onChange({ ...group,
            rules: [...group.rules, { logic: group.logic === "and" ? "or" : "and", rules: [newRule] }] })}>
            <ListPlusIcon data-icon="inline-start" />Grup ekle
          </Button>
        )}
      </div>
    </div>
  )
}

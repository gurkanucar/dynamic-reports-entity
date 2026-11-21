import { PlusIcon, ShieldCheckIcon, Trash2Icon } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { freeId } from "../lib/field-types"
import type { SurveyField, SurveyRule } from "../types/survey.dto"
import { ConditionEditor } from "./condition-editor"

/**
 * Rules over all answers: a response that breaks one is refused with its message ("Bitiş tarihi başlangıçtan önce
 * olamaz"). Checked on the server when the response is sent.
 */
export function RulesCard({ rules, fields, problems, onChange }: {
  rules: SurveyRule[]
  fields: SurveyField[]
  /** Server problems by rule index. */
  problems: Record<number, string[]>
  onChange: (rules: SurveyRule[]) => void
}) {
  const update = (index: number, change: Partial<SurveyRule>) =>
    onChange(rules.map((rule, i) => (i === index ? { ...rule, ...change } : rule)))
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><ShieldCheckIcon />Kurallar</CardTitle>
        <CardDescription>
          Yanıtın tutarlı olması gereken durumlar. Koşul sağlanmazsa yanıt gönderilmez ve mesaj gösterilir.
          Örn. “Bitiş tarihi ≥ Başlangıç tarihi”, “Tutar 50.000’den büyükse gerekçe dolu”.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {rules.map((rule, index) => (
          <div key={index} className="flex flex-col gap-3 rounded-lg border p-3">
            <div className="flex items-start gap-2">
              <Field className="flex-1">
                <FieldLabel htmlFor={`rule-message-${index}`}>Kural bozulursa gösterilecek mesaj</FieldLabel>
                <Input id={`rule-message-${index}`} value={rule.message} maxLength={300}
                  onChange={(e) => update(index, { message: e.target.value })} />
              </Field>
              <Button type="button" variant="ghost" size="icon" className="mt-6" aria-label="Kuralı sil"
                onClick={() => onChange(rules.filter((_, i) => i !== index))}><Trash2Icon /></Button>
            </div>
            <Field>
              <FieldLabel>Geçerli olması için</FieldLabel>
              <ConditionEditor value={rule.logic} fields={fields} onChange={(logic) => update(index, { logic })} />
            </Field>
            {problems[index]?.map((problem) => <p key={problem} className="text-sm text-destructive">{problem}</p>)}
          </div>
        ))}
        <Button type="button" variant="outline" className="self-start" disabled={!fields.some((f) => f.type !== "section")}
          onClick={() => onChange([...rules, { id: freeId(rules.map((r) => r.id), "rule"), message: "", logic: undefined }])}>
          <PlusIcon data-icon="inline-start" />Kural ekle
        </Button>
      </CardContent>
    </Card>
  )
}

import {
  AlignLeftIcon, AtSignIcon, CalendarIcon, ChevronDownSquareIcon, CircleDotIcon, GaugeIcon, HashIcon, HeadingIcon,
  ListChecksIcon, PhoneIcon, StarIcon, TextCursorInputIcon, ToggleLeftIcon, type LucideIcon,
} from "lucide-react"
import type { FieldType, SurveyDefinition, SurveyField } from "../types/survey.dto"

export interface FieldTypeInfo {
  type: FieldType
  label: string
  icon: LucideIcon
  group: "Metin" | "Seçim" | "Ölçek" | "Diğer"
}

/** The palette, grouped the way people look for a question type. */
export const FIELD_TYPES: FieldTypeInfo[] = [
  { type: "shortText", label: "Kısa metin", icon: TextCursorInputIcon, group: "Metin" },
  { type: "longText", label: "Uzun metin", icon: AlignLeftIcon, group: "Metin" },
  { type: "email", label: "E-posta", icon: AtSignIcon, group: "Metin" },
  { type: "phone", label: "Telefon", icon: PhoneIcon, group: "Metin" },
  { type: "number", label: "Sayı", icon: HashIcon, group: "Metin" },
  { type: "singleChoice", label: "Tek seçim", icon: CircleDotIcon, group: "Seçim" },
  { type: "multipleChoice", label: "Çoklu seçim", icon: ListChecksIcon, group: "Seçim" },
  { type: "dropdown", label: "Açılır liste", icon: ChevronDownSquareIcon, group: "Seçim" },
  { type: "yesNo", label: "Evet / Hayır", icon: ToggleLeftIcon, group: "Seçim" },
  { type: "rating", label: "Yıldız puanı", icon: StarIcon, group: "Ölçek" },
  { type: "nps", label: "0-10 puan (NPS)", icon: GaugeIcon, group: "Ölçek" },
  { type: "date", label: "Tarih", icon: CalendarIcon, group: "Diğer" },
  { type: "section", label: "Bölüm başlığı", icon: HeadingIcon, group: "Diğer" },
]

export const typeInfo = (type: FieldType): FieldTypeInfo =>
  FIELD_TYPES.find((info) => info.type === type) ?? FIELD_TYPES[0]

export const CHOICE_TYPES: FieldType[] = ["singleChoice", "multipleChoice", "dropdown"]
export const TEXT_TYPES: FieldType[] = ["shortText", "longText", "email", "phone"]
export const NUMERIC_TYPES: FieldType[] = ["number", "rating", "nps"]

export const hasOptions = (type: FieldType) => CHOICE_TYPES.includes(type)
export const isAnswerable = (field: SurveyField) => field.type !== "section"

/** A fresh id like `q3` that no question uses yet. */
export function freeId(taken: Iterable<string>, prefix = "q"): string {
  const used = new Set(taken)
  for (let n = 1; ; n++) if (!used.has(`${prefix}${n}`)) return `${prefix}${n}`
}

export function newField(type: FieldType, fields: SurveyField[]): SurveyField {
  const id = freeId(fields.map((field) => field.id), type === "section" ? "s" : "q")
  const field: SurveyField = { id, type, label: type === "section" ? "Yeni bölüm" : "Yeni soru", required: false }
  if (hasOptions(type)) field.options = [{ id: "o1", label: "Seçenek 1" }, { id: "o2", label: "Seçenek 2" }]
  if (type === "rating") field.max = 5
  return field
}

/** Changing the type keeps what still makes sense (label, help, condition) and drops the rest. */
export function changeType(field: SurveyField, type: FieldType): SurveyField {
  const next: SurveyField = {
    id: field.id, type, label: field.label, help: field.help, visibleWhen: field.visibleWhen,
    required: type === "section" || type === "yesNo" ? false : field.required,
    width: type === "section" ? undefined : field.width,
  }
  if (hasOptions(type)) {
    next.options = field.options?.length ? field.options
      : [{ id: "o1", label: "Seçenek 1" }, { id: "o2", label: "Seçenek 2" }]
  }
  if (type === "rating") next.max = 5
  if (TEXT_TYPES.includes(type)) next.placeholder = field.placeholder
  return next
}

export const EMPTY_DEFINITION: SurveyDefinition = {
  submitLabel: "Gönder",
  successMessage: "Teşekkürler, yanıtınız alındı.",
  fields: [],
  rules: [],
}

/** Rows of the form: two `half` questions in a row share it; everything else takes a row. */
export function rows(fields: SurveyField[]): SurveyField[][] {
  const result: SurveyField[][] = []
  for (const field of fields) {
    const last = result.at(-1)
    const half = field.width === "half" && field.type !== "section"
    if (half && last && last.length === 1 && last[0].width === "half" && last[0].type !== "section") last.push(field)
    else result.push([field])
  }
  return result
}

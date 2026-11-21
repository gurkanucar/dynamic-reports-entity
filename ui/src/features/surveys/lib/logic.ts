import type { Answers, FieldType, Logic, SurveyField } from "../types/survey.dto"
import { CHOICE_TYPES, NUMERIC_TYPES, TEXT_TYPES } from "./field-types"

// ---- evaluation ---------------------------------------------------------------------------------------
// The same operators as the server (JsonLogic.java); the server decides, this only keeps the form in step.

const truthy = (value: unknown) =>
  !(value === null || value === undefined || value === false || value === 0 || value === ""
    || (Array.isArray(value) && value.length === 0))

const lookup = (data: unknown, path: string): unknown =>
  path.split(".").reduce<unknown>((current, key) =>
    current !== null && typeof current === "object" ? (current as Record<string, unknown>)[key] : undefined, data)

const toNumber = (value: unknown): number | null => {
  if (typeof value === "number") return value
  if (typeof value === "boolean") return value ? 1 : 0
  if (typeof value === "string" && value.trim() !== "" && !Number.isNaN(Number(value))) return Number(value)
  return null
}

function looseEquals(a: unknown, b: unknown): boolean {
  if (a === null || a === undefined || b === null || b === undefined) return (a ?? null) === (b ?? null)
  if (typeof a === "number" || typeof b === "number") {
    const x = toNumber(a)
    const y = toNumber(b)
    return x !== null && y !== null && x === y
  }
  if (typeof a === "boolean" || typeof b === "boolean") return truthy(a) === truthy(b)
  return String(a) === String(b)
}

function compare(a: unknown, b: unknown, op: string): boolean {
  let order: number
  if (typeof a === "string" && typeof b === "string" && (toNumber(a) === null || toNumber(b) === null)) {
    order = a < b ? -1 : a > b ? 1 : 0
  } else {
    const x = toNumber(a)
    const y = toNumber(b)
    if (x === null || y === null) return false
    order = x - y
  }
  return op === ">" ? order > 0 : op === ">=" ? order >= 0 : op === "<" ? order < 0 : order <= 0
}

const text = (value: unknown) => (value === null || value === undefined ? "" : String(value))

export function evaluate(rule: Logic, data: unknown, depth = 0): unknown {
  if (depth > 64) return null
  if (Array.isArray(rule)) return rule.map((item) => evaluate(item, data, depth + 1))
  if (rule === null || typeof rule !== "object") return rule
  const entries = Object.entries(rule as Record<string, unknown>)
  if (entries.length !== 1) return rule
  const [op, raw] = entries[0]
  const args = Array.isArray(raw) ? raw : [raw]
  const next = depth + 1
  if (op === "and" || op === "or") {
    let last: unknown = op === "and"
    for (const arg of args) {
      last = evaluate(arg, data, next)
      if (op === "and" ? !truthy(last) : truthy(last)) return last
    }
    return last
  }
  if (op === "if" || op === "?:") {
    let i = 0
    for (; i + 1 < args.length; i += 2) if (truthy(evaluate(args[i], data, next))) return evaluate(args[i + 1], data, next)
    return i < args.length ? evaluate(args[i], data, next) : null
  }
  const values = args.map((arg) => evaluate(arg, data, next))
  const [a, b] = values
  switch (op) {
    case "var": {
      if (a === null || a === undefined || a === "") return data
      const found = lookup(data, String(a))
      return found === undefined ? (b ?? null) : found
    }
    case "==": return looseEquals(a, b)
    case "!=": return !looseEquals(a, b)
    case "===": return a === b
    case "!==": return a !== b
    case "!": return !truthy(a)
    case "!!": return truthy(a)
    case ">": case ">=": return compare(a, b, op)
    case "<": case "<=": return values.length === 3 ? compare(a, b, op) && compare(b, values[2], op) : compare(a, b, op)
    case "in": return typeof b === "string" ? a !== null && a !== undefined && b.includes(text(a))
      : Array.isArray(b) ? b.some((item) => looseEquals(item, a)) : false
    case "+": return values.reduce<number>((sum, value) => sum + (toNumber(value) ?? 0), 0)
    case "*": return values.reduce<number>((product, value) => product * (toNumber(value) ?? 0), 1)
    case "-": return values.length === 1 ? -(toNumber(a) ?? 0) : (toNumber(a) ?? 0) - (toNumber(b) ?? 0)
    case "/": return (toNumber(a) ?? 0) / (toNumber(b) ?? 1)
    case "cat": return values.map(text).join("")
    case "length": return Array.isArray(a) ? a.length : text(a).length
    case "startsWith": return text(a).toLowerCase().startsWith(text(b).toLowerCase())
    case "endsWith": return text(a).toLowerCase().endsWith(text(b).toLowerCase())
    case "lower": return text(a).toLowerCase()
    case "upper": return text(a).toUpperCase()
    case "trim": return text(a).trim()
    default: return null
  }
}

export const test = (rule: Logic, data: unknown) => {
  try {
    return truthy(evaluate(rule, data))
  } catch {
    return false
  }
}

/**
 * The answers that count, in form order: questions whose condition is false are left out, as on the server (so a
 * hidden question cannot make a later one visible).
 */
export function visibleAnswers(fields: SurveyField[], answers: Answers): { visible: Set<string>; answers: Answers } {
  const visible = new Set<string>()
  const counted: Answers = {}
  for (const field of fields) {
    if (field.visibleWhen !== undefined && field.visibleWhen !== null && !test(field.visibleWhen, counted)) continue
    visible.add(field.id)
    const value = answers[field.id]
    if (field.type !== "section" && value !== undefined && value !== "" && !(Array.isArray(value) && value.length === 0)) {
      counted[field.id] = value
    }
  }
  return { visible, answers: counted }
}

// ---- the visual editor's model ------------------------------------------------------------------------

export type ConditionOp =
  | "eq" | "neq" | "oneOf" | "includes" | "excludes" | "isTrue" | "isFalse"
  | "gt" | "gte" | "lt" | "lte" | "contains" | "startsWith" | "endsWith" | "filled" | "empty"

/** One row: `question op value`, where the value may be another question (`otherField`). */
export interface Condition {
  fieldId: string
  op: ConditionOp
  value?: string
  values?: string[]
  otherField?: string
}

export interface ConditionGroup {
  combinator: "and" | "or"
  conditions: Condition[]
}

export const OP_LABELS: Record<ConditionOp, string> = {
  eq: "eşittir", neq: "eşit değildir", oneOf: "şunlardan biri", includes: "şunu içerir", excludes: "şunu içermez",
  isTrue: "evet", isFalse: "hayır", gt: "büyüktür", gte: "büyük veya eşit", lt: "küçüktür", lte: "küçük veya eşit",
  contains: "içerir", startsWith: "ile başlar", endsWith: "ile biter", filled: "cevaplandı", empty: "boş",
}

/** The tests that make sense for a question type. */
export function opsFor(type: FieldType): ConditionOp[] {
  if (type === "multipleChoice") return ["includes", "excludes", "filled", "empty"]
  if (CHOICE_TYPES.includes(type)) return ["eq", "neq", "oneOf", "filled", "empty"]
  if (type === "yesNo") return ["isTrue", "isFalse"]
  if (NUMERIC_TYPES.includes(type) || type === "date") return ["eq", "neq", "gt", "gte", "lt", "lte", "filled", "empty"]
  if (TEXT_TYPES.includes(type)) return ["eq", "neq", "contains", "startsWith", "endsWith", "filled", "empty"]
  return ["filled", "empty"]
}

/** Whether a test compares with a value (and may compare with another question). */
export const needsValue = (op: ConditionOp) => !["isTrue", "isFalse", "filled", "empty"].includes(op)
export const canCompareFields = (op: ConditionOp) => ["eq", "neq", "gt", "gte", "lt", "lte"].includes(op)

const literal = (field: SurveyField | undefined, value: string | undefined): unknown => {
  if (field && NUMERIC_TYPES.includes(field.type)) {
    const number = Number(value)
    return value !== undefined && value.trim() !== "" && !Number.isNaN(number) ? number : value ?? ""
  }
  return value ?? ""
}

export function conditionToLogic(condition: Condition, fields: SurveyField[]): Logic {
  const field = fields.find((item) => item.id === condition.fieldId)
  const ref = { var: condition.fieldId }
  const other = condition.otherField ? { var: condition.otherField } : literal(field, condition.value)
  switch (condition.op) {
    case "eq": return { "==": [ref, other] }
    case "neq": return { "!=": [ref, other] }
    case "gt": return { ">": [ref, other] }
    case "gte": return { ">=": [ref, other] }
    case "lt": return { "<": [ref, other] }
    case "lte": return { "<=": [ref, other] }
    case "oneOf": return { in: [ref, condition.values ?? []] }
    case "includes": return { in: [condition.value ?? "", ref] }
    case "excludes": return { "!": { in: [condition.value ?? "", ref] } }
    case "isTrue": return { "==": [ref, true] }
    case "isFalse": return { "!": ref }
    case "contains": return { in: [condition.value ?? "", ref] }
    case "startsWith": return { startsWith: [ref, condition.value ?? ""] }
    case "endsWith": return { endsWith: [ref, condition.value ?? ""] }
    case "filled": return { "!!": ref }
    case "empty": return { "!": ref }
  }
}

export function groupToLogic(group: ConditionGroup, fields: SurveyField[]): Logic | null {
  const parts = group.conditions.filter((condition) => condition.fieldId).map((condition) => conditionToLogic(condition, fields))
  if (parts.length === 0) return null
  return parts.length === 1 ? parts[0] : { [group.combinator]: parts }
}

const isVar = (value: unknown): value is { var: string } =>
  typeof value === "object" && value !== null && !Array.isArray(value) && Object.keys(value).length === 1
  && typeof (value as { var: unknown }).var === "string"

const single = (logic: unknown): [string, unknown[]] | null => {
  if (typeof logic !== "object" || logic === null || Array.isArray(logic)) return null
  const entries = Object.entries(logic)
  if (entries.length !== 1) return null
  const [op, raw] = entries[0]
  return [op, Array.isArray(raw) ? raw : [raw]]
}

const asText = (value: unknown) => (value === null || value === undefined ? "" : String(value))

/** Reads a condition back; null when the visual editor cannot show it (then it is edited as JSON). */
export function logicToCondition(logic: unknown, fields: SurveyField[]): Condition | null {
  const parsed = single(logic)
  if (!parsed) return null
  const [op, args] = parsed
  const fieldOf = (id: string) => fields.find((field) => field.id === id)
  const compareOps: Record<string, ConditionOp> = { "==": "eq", "!=": "neq", ">": "gt", ">=": "gte", "<": "lt", "<=": "lte" }
  if (op in compareOps && args.length === 2 && isVar(args[0])) {
    const field = fieldOf(args[0].var)
    if (field?.type === "yesNo" && op === "==" && typeof args[1] === "boolean") {
      return { fieldId: args[0].var, op: args[1] ? "isTrue" : "isFalse" }
    }
    if (isVar(args[1])) return { fieldId: args[0].var, op: compareOps[op], otherField: args[1].var }
    if (typeof args[1] === "object" && args[1] !== null) return null
    return { fieldId: args[0].var, op: compareOps[op], value: asText(args[1]) }
  }
  if (op === "in" && args.length === 2) {
    if (isVar(args[0]) && Array.isArray(args[1])) return { fieldId: args[0].var, op: "oneOf", values: args[1].map(asText) }
    if (isVar(args[1]) && (typeof args[0] === "string" || typeof args[0] === "number")) {
      const field = fieldOf(args[1].var)
      return { fieldId: args[1].var, op: field?.type === "multipleChoice" ? "includes" : "contains", value: asText(args[0]) }
    }
    return null
  }
  if ((op === "startsWith" || op === "endsWith") && args.length === 2 && isVar(args[0]) && typeof args[1] === "string") {
    return { fieldId: args[0].var, op, value: args[1] }
  }
  if (op === "!!" && args.length === 1 && isVar(args[0])) return { fieldId: args[0].var, op: "filled" }
  if (op === "!" && args.length === 1) {
    if (isVar(args[0])) {
      return { fieldId: args[0].var, op: fieldOf(args[0].var)?.type === "yesNo" ? "isFalse" : "empty" }
    }
    const inner = single(args[0])
    if (inner && inner[0] === "in" && inner[1].length === 2 && isVar(inner[1][1])) {
      return { fieldId: inner[1][1].var, op: "excludes", value: asText(inner[1][0]) }
    }
  }
  return null
}

export function logicToGroup(logic: unknown, fields: SurveyField[]): ConditionGroup | null {
  if (logic === null || logic === undefined || logic === true) return { combinator: "and", conditions: [] }
  const parsed = single(logic)
  if (parsed && (parsed[0] === "and" || parsed[0] === "or")) {
    const conditions = parsed[1].map((item) => logicToCondition(item, fields))
    return conditions.every((condition) => condition !== null)
      ? { combinator: parsed[0] as "and" | "or", conditions: conditions as Condition[] } : null
  }
  const condition = logicToCondition(logic, fields)
  return condition ? { combinator: "and", conditions: [condition] } : null
}

/** "Departman eşittir Bilgi Teknolojileri ve ..." for the field cards. */
export function describeLogic(logic: unknown, fields: SurveyField[]): string {
  const group = logicToGroup(logic, fields)
  if (!group) return "özel kural (JSON)"
  return group.conditions.map((condition) => {
    const field = fields.find((item) => item.id === condition.fieldId)
    const name = field?.label ?? condition.fieldId
    const optionLabel = (id: string) => field?.options?.find((option) => option.id === id)?.label ?? id
    const value = condition.otherField
      ? `“${fields.find((item) => item.id === condition.otherField)?.label ?? condition.otherField}”`
      : condition.values ? condition.values.map(optionLabel).join(", ")
        : condition.value !== undefined ? (field?.options ? optionLabel(condition.value) : condition.value) : ""
    return `${name} ${OP_LABELS[condition.op]}${value ? ` ${value}` : ""}`
  }).join(group.combinator === "and" ? " ve " : " veya ")
}

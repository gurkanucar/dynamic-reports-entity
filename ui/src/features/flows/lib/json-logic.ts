import type { JsonLogicRule } from "../types/flow.dto"

export type RuleOperator = "==" | "!=" | ">" | ">=" | "<" | "<=" | "in" | "contains" | "exists"

/** One comparison the visual editor can show: `path op value`. */
export interface SimpleRule {
  path: string
  op: RuleOperator
  value: string
}

/** All (and) or any (or) of its rules; a rule may be a group again ("IT ise 250.000, değilse 100.000"). */
export interface SimpleCondition {
  logic: "and" | "or"
  rules: (SimpleRule | SimpleCondition)[]
}

export const isGroupRule = (rule: SimpleRule | SimpleCondition): rule is SimpleCondition => "rules" in rule

/** Groups inside groups the rows show; deeper rules are edited as JSON. */
export const MAX_GROUP_DEPTH = 3

export const OPERATORS: { value: RuleOperator; label: string }[] = [
  { value: "==", label: "eşittir" },
  { value: "!=", label: "eşit değildir" },
  { value: ">", label: "büyüktür" },
  { value: ">=", label: "büyük veya eşit" },
  { value: "<", label: "küçüktür" },
  { value: "<=", label: "küçük veya eşit" },
  { value: "in", label: "şunlardan biri" },
  { value: "contains", label: "içerir" },
  { value: "exists", label: "dolu" },
]

/** Text from the form to a JSON value: numbers and true/false keep their type, lists are separated by ";". */
function parse(value: string, list: boolean): unknown {
  const one = (text: string): unknown => {
    const trimmed = text.trim()
    if (trimmed === "true" || trimmed === "false") return trimmed === "true"
    if (trimmed !== "" && !Number.isNaN(Number(trimmed))) return Number(trimmed)
    return trimmed
  }
  return list ? value.split(";").map(one).filter((item) => item !== "") : one(value)
}

function text(value: unknown): string {
  return Array.isArray(value) ? value.join("; ") : value === undefined || value === null ? "" : String(value)
}

function ruleToLogic(rule: SimpleRule): JsonLogicRule {
  const variable = { var: rule.path.trim() }
  switch (rule.op) {
    case "exists": return { "!!": [variable] }
    case "in": return { in: [variable, parse(rule.value, true)] }
    case "contains": return { in: [parse(rule.value, false), variable] }
    default: return { [rule.op]: [variable, parse(rule.value, false)] }
  }
}

export function toLogic(condition: SimpleCondition): JsonLogicRule {
  const rules = condition.rules
    .map((rule) => isGroupRule(rule) ? toLogic(rule) : rule.path.trim() ? ruleToLogic(rule) : true)
    .filter((rule) => rule !== true)
  if (rules.length === 0) return true
  return rules.length === 1 ? rules[0] : { [condition.logic]: rules }
}

const isVar = (value: unknown): value is { var: string } =>
  typeof value === "object" && value !== null && !Array.isArray(value) && Object.keys(value).length === 1
  && typeof (value as { var: unknown }).var === "string"

function logicToRule(logic: unknown): SimpleRule | null {
  if (typeof logic !== "object" || logic === null || Array.isArray(logic)) return null
  const entries = Object.entries(logic)
  if (entries.length !== 1) return null
  const [op, raw] = entries[0]
  const args = Array.isArray(raw) ? raw : [raw]
  if (op === "!!" && args.length === 1 && isVar(args[0])) return { path: args[0].var, op: "exists", value: "" }
  if (op === "in" && args.length === 2) {
    if (isVar(args[0]) && Array.isArray(args[1])) return { path: args[0].var, op: "in", value: text(args[1]) }
    if (isVar(args[1]) && !isVar(args[0])) return { path: args[1].var, op: "contains", value: text(args[0]) }
    return null
  }
  if (["==", "!=", ">", ">=", "<", "<="].includes(op) && args.length === 2 && isVar(args[0]) && !isVar(args[1])
    && (typeof args[1] !== "object" || args[1] === null)) {
    return { path: args[0].var, op: op as RuleOperator, value: text(args[1]) }
  }
  return null
}

function logicToGroup(logic: unknown, depth: number): SimpleCondition | null {
  if (depth > MAX_GROUP_DEPTH || typeof logic !== "object" || logic === null || Array.isArray(logic)
    || Object.keys(logic).length !== 1) return null
  const [group, items] = Object.entries(logic)[0]
  if ((group !== "and" && group !== "or") || !Array.isArray(items)) return null
  const rules = items.map((item) => logicToRule(item) ?? logicToGroup(item, depth + 1))
  return rules.every((rule) => rule !== null) ? { logic: group, rules: rules as (SimpleRule | SimpleCondition)[] } : null
}

/** The rule as rows for the visual editor, or null when it uses more than and/or of comparisons. */
export function fromLogic(logic: JsonLogicRule): SimpleCondition | null {
  if (logic === true || logic === undefined || logic === null) return { logic: "and", rules: [] }
  const single = logicToRule(logic)
  if (single) return { logic: "and", rules: [single] }
  return logicToGroup(logic, 1)
}

import type { FlowEdgeDto, FlowStepDto, PositionDto } from "../types/flow.dto"
import { stepType } from "./step-types"

export interface GraphModel {
  steps: FlowStepDto[]
  edges: FlowEdgeDto[]
  layout: Record<string, PositionDto>
}

/** Left to right: a column per depth, branches stacked in it. */
const COLUMN = 320
const ROW = 230

/**
 * Explicit edges for flows saved without any (they ran in list order): the same behaviour, now visible and
 * editable. A condition continues on "true", an approval on "approved", like the sequential engine does.
 */
export function withExplicitEdges(model: GraphModel): GraphModel {
  if (model.edges.length > 0 || model.steps.length < 2) return model
  const edges: FlowEdgeDto[] = []
  for (let i = 0; i + 1 < model.steps.length; i++) {
    const step = model.steps[i]
    const when = step.type === "condition" ? "true" : step.type === "approval" ? "approved" : null
    edges.push({ from: step.key, to: model.steps[i + 1].key, when })
  }
  return { ...model, edges }
}

const NODE_WIDTH = 240

/** About how tall a step is drawn (see StepNode): a row per outcome when it branches. */
function nodeHeight(step: FlowStepDto): number {
  const outcomes = stepType(step.type).outcomes.length
  return 90 + (outcomes > 1 ? 10 + (outcomes + 1) * 26 : 0)   // + the "Diğer" row
}

/** Whether stored positions put steps on top of each other (e.g. a layout saved top to bottom earlier). */
function overlaps(model: GraphModel): boolean {
  const placed = model.steps.filter((step) => model.layout[step.key])
    .map((step) => ({ ...model.layout[step.key], height: nodeHeight(step) }))
  return placed.some((a, i) => placed.slice(i + 1).some((b) => Math.abs(a.x - b.x) < NODE_WIDTH
    && (a.y <= b.y ? b.y - a.y < a.height : a.y - b.y < b.height)))
}

/**
 * Positions for steps the layout does not know: left to right by depth, branches one under the other. A stored
 * layout whose steps would overlap is replaced as a whole.
 */
export function withLayout(input: GraphModel): GraphModel {
  const model = overlaps(input) ? { ...input, layout: {} } : input
  const missing = model.steps.filter((step) => !model.layout[step.key])
  if (missing.length === 0) return model
  const incoming = new Map<string, number>()
  model.edges.forEach((edge) => incoming.set(edge.to, (incoming.get(edge.to) ?? 0) + 1))
  const depth = new Map<string, number>()
  const queue = model.steps.filter((step) => !incoming.get(step.key)).map((step) => step.key)
  queue.forEach((key) => depth.set(key, 0))
  while (queue.length > 0) {
    const key = queue.shift()!
    for (const edge of model.edges.filter((e) => e.from === key)) {
      const next = (depth.get(key) ?? 0) + 1
      if ((depth.get(edge.to) ?? -1) < next && next <= model.steps.length) {
        depth.set(edge.to, next)
        queue.push(edge.to)
      }
    }
  }
  const perRow = new Map<number, number>()
  const layout = { ...model.layout }
  model.steps.forEach((step, index) => {
    if (layout[step.key]) return
    const column = depth.get(step.key) ?? index
    const row = perRow.get(column) ?? 0
    perRow.set(column, row + 1)
    layout[step.key] = { x: column * COLUMN, y: row * ROW }
  })
  return { ...model, layout }
}

/** Renames a step everywhere it is referenced: edges, layout, mail attachments. */
export function renameStep(model: GraphModel, from: string, to: string): GraphModel {
  const layout = { ...model.layout }
  if (layout[from]) {
    layout[to] = layout[from]
    delete layout[from]
  }
  return {
    steps: model.steps.map((step) => {
      const renamed = step.key === from ? { ...step, key: to } : step
      return renamed.type === "mail" && renamed.attachments
        ? { ...renamed, attachments: renamed.attachments.map((key) => (key === from ? to : key)) } : renamed
    }),
    edges: model.edges.map((edge) => ({
      ...edge, from: edge.from === from ? to : edge.from, to: edge.to === from ? to : edge.to,
    })),
    layout,
  }
}

export function removeStep(model: GraphModel, key: string): GraphModel {
  const layout = { ...model.layout }
  delete layout[key]
  return {
    steps: model.steps.filter((step) => step.key !== key)
      .map((step) => step.type === "mail" && step.attachments
        ? { ...step, attachments: step.attachments.filter((attachment) => attachment !== key) } : step),
    edges: model.edges.filter((edge) => edge.from !== key && edge.to !== key),
    layout,
  }
}

/** Steps that run before `key` on some path (for attachments and variable hints). */
export function stepsBefore(model: GraphModel, key: string): FlowStepDto[] {
  const before = new Set<string>()
  const queue = [key]
  while (queue.length > 0) {
    const current = queue.shift()!
    for (const edge of model.edges.filter((e) => e.to === current)) {
      if (!before.has(edge.from)) {
        before.add(edge.from)
        queue.push(edge.from)
      }
    }
  }
  return model.steps.filter((step) => before.has(step.key))
}

/** Outcome label of an edge ("Evet", "Onaylandı" ...; "Diğer" for a branching step's default), empty otherwise. */
export function edgeLabel(model: GraphModel, edge: FlowEdgeDto): string {
  const from = model.steps.find((step) => step.key === edge.from)
  if (!from) return ""
  if (!edge.when) return stepType(from.type).outcomes.length > 1 ? "Diğer" : ""
  return stepType(from.type).outcomes.find((outcome) => outcome.id === edge.when)?.label ?? edge.when
}

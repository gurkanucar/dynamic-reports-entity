import "@xyflow/react/dist/style.css"
import {
  applyEdgeChanges, applyNodeChanges, Background, BackgroundVariant, ControlButton, Controls, MarkerType, ReactFlow,
  useReactFlow, type Connection, type Edge, type EdgeChange, type FitViewOptions, type NodeChange,
} from "@xyflow/react"
import { Maximize2Icon, Minimize2Icon } from "lucide-react"
import { useEffect, useMemo, useRef, useState } from "react"
import { useTheme } from "@/components/theme-provider"
import { cn } from "@/lib/utils"
import { edgeLabel, type GraphModel } from "../lib/flow-graph"
import type { FlowEdgeDto, StepStatus } from "../types/flow.dto"
import type { FlowIssue } from "../lib/flow-checks"
import { START_NODE } from "../lib/triggers"
import { StartNode, type StartLine, type StartNodeType } from "./start-node"
import { StepNode, type StepNodeType } from "./step-node"

const NODE_TYPES = { step: StepNode, start: StartNode }

type CanvasNode = StepNodeType | StartNodeType

export interface RunState {
  status: StepStatus
  outcome: string | null
}

const edgeId = (edge: FlowEdgeDto) => `${edge.from}|${edge.when ?? "out"}|${edge.to}`

/**
 * The flow as a graph, left to right. Editable: drag steps, connect an exit to another step's entry (an outcome may
 * have one edge); drag an edge's end onto another step to move it or onto empty canvas to remove it, or select it
 * and press Delete. Read-only with `runState`: which steps ran and how.
 * <p>
 * React Flow owns the nodes and edges while they are on screen (it measures them, drags and selects them); the model
 * is only told about finished changes (a drag that ended, a removed edge, a new connection).
 */
export function FlowCanvas({ model, editable = false, selected, onSelect, onChange, runState, invalidKeys, issues,
  start, fitSignal, expanded: expandedByParent, onExpandedChange, className }: {
  model: GraphModel
  editable?: boolean
  selected?: string | null
  onSelect?: (key: string | null) => void
  onChange?: (model: GraphModel) => void
  runState?: Record<string, RunState>
  invalidKeys?: Set<string>
  /** The checklist's findings, shown on their steps (and the start node). */
  issues?: FlowIssue[]
  /** What starts the flow: drawn as the first node, connected to the start step. */
  start?: StartLine[]
  /** Changing it fits the flow into view again (e.g. after an automatic layout). */
  fitSignal?: number
  /**
   * Full window, controlled by the parent (it then enlarges itself, e.g. the editor with its panels); without
   * `onExpandedChange` the canvas enlarges on its own.
   */
  expanded?: boolean
  onExpandedChange?: (expanded: boolean) => void
  className?: string
}) {
  // read-only views (a run) stay readable: wide flows are panned instead of shrunk until nothing can be read
  const fitOptions = useMemo<FitViewOptions>(() => ({ padding: 0.2, maxZoom: 1, minZoom: editable ? undefined : 0.6 }),
    [editable])
  const { theme } = useTheme()
  const [ownExpanded, setOwnExpanded] = useState(false)
  const controlled = onExpandedChange !== undefined
  const expanded = controlled ? expandedByParent === true : ownExpanded
  const setExpanded = controlled ? onExpandedChange : setOwnExpanded

  const messages = (target: string, severity: FlowIssue["severity"]) =>
    (issues ?? []).filter((issue) => issue.target === target && issue.severity === severity).map((issue) => issue.message)

  // the step without an incoming edge: where the start node leads
  const startStep = useMemo(() => {
    const incoming = new Set(model.edges.map((edge) => edge.to))
    return model.steps.find((step) => !incoming.has(step.key))
  }, [model])

  const builtNodes: CanvasNode[] = useMemo(() => {
    const steps: CanvasNode[] = model.steps.map((step) => ({
      id: step.key,
      type: "step",
      position: model.layout[step.key] ?? { x: 0, y: 0 },
      selected: step.key === selected,
      draggable: editable,
      deletable: false,   // removed from its settings panel, so the model always knows
      data: {
        step,
        status: runState?.[step.key]?.status,
        outcome: runState?.[step.key]?.outcome,
        skipped: runState !== undefined && runState[step.key] === undefined,
        invalid: invalidKeys?.has(step.key),
        errors: messages(step.key, "error"),
        warnings: messages(step.key, "warning"),
        editable,
      },
    }))
    if (!start) return steps
    const anchor = startStep ? model.layout[startStep.key] : undefined
    const node: StartNodeType = {
      id: START_NODE,
      type: "start",
      position: model.layout[START_NODE] ?? { x: (anchor?.x ?? 0) - 320, y: anchor?.y ?? 0 },
      selected: selected === START_NODE,
      draggable: editable,
      deletable: false,
      data: { lines: start, errors: messages(START_NODE, "error") },
    }
    return [node, ...steps]
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [model, selected, runState, invalidKeys, editable, issues, start, startStep])

  const builtEdges: Edge[] = useMemo(() => [...(start && startStep ? [{
    id: `${START_NODE}|${startStep.key}`,
    source: START_NODE,
    target: startStep.key,
    type: "smoothstep",
    animated: true,
    deletable: false,
    selectable: false,
    reconnectable: false,
    markerEnd: { type: MarkerType.ArrowClosed, width: 18, height: 18 },
  } satisfies Edge] : []), ...model.edges.map((edge) => {
    const taken = runState ? runState[edge.from]?.status === "completed" && runState[edge.to] !== undefined : false
    return {
      id: edgeId(edge),
      source: edge.from,
      sourceHandle: edge.when ?? "out",
      target: edge.to,
      type: "smoothstep",
      label: edgeLabel(model, edge) || undefined,
      labelBgPadding: [6, 3] as [number, number],
      labelBgBorderRadius: 6,
      markerEnd: { type: MarkerType.ArrowClosed, width: 18, height: 18 },
      animated: taken,
      className: cn(runState && !taken && "opacity-35"),
    }
  })], [model, runState, start, startStep])

  // On-screen state, kept in step with the model; what React Flow measured is kept, or nodes would hide again.
  const [nodes, setNodes] = useState(builtNodes)
  const [edges, setEdges] = useState(builtEdges)
  const [shownNodes, setShownNodes] = useState(builtNodes)
  const [shownEdges, setShownEdges] = useState(builtEdges)
  if (shownNodes !== builtNodes) {
    setShownNodes(builtNodes)
    setNodes((current) => builtNodes.map((node) => {
      const onScreen = current.find((existing) => existing.id === node.id)
      return onScreen ? { ...node, measured: onScreen.measured, width: onScreen.width, height: onScreen.height } : node
    }))
  }
  if (shownEdges !== builtEdges) {
    setShownEdges(builtEdges)
    setEdges((current) => builtEdges.map((edge) => ({
      ...edge, selected: current.find((existing) => existing.id === edge.id)?.selected,
    })))
  }

  const onNodesChange = (changes: NodeChange<CanvasNode>[]) => {
    const allowed = editable ? changes : changes.filter((change) => change.type === "dimensions" || change.type === "select")
    setNodes((current) => applyNodeChanges(allowed, current))
    if (!editable || !onChange) return
    // a finished drag: positions go to the model (saved with the flow)
    const dropped = changes.filter((change) => change.type === "position" && !change.dragging && change.position)
    if (dropped.length > 0) {
      const layout = { ...model.layout }
      for (const change of dropped) {
        if (change.type === "position" && change.position) {
          layout[change.id] = { x: Math.round(change.position.x), y: Math.round(change.position.y) }
        }
      }
      onChange({ ...model, layout })
    }
  }

  const onEdgesChange = (changes: EdgeChange[]) => {
    setEdges((current) => applyEdgeChanges(editable ? changes : changes.filter((c) => c.type === "select"), current))
    if (!editable || !onChange) return
    const removed = new Set(changes.filter((change) => change.type === "remove").map((change) => change.id))
    if (removed.size > 0) {
      onChange({ ...model, edges: model.edges.filter((edge) => !removed.has(edgeId(edge))) })
    }
  }

  /** The edges with `connection` added; one edge per step and outcome, so it replaces that outcome's old one. */
  const connect = (edges: FlowEdgeDto[], connection: Connection): FlowEdgeDto[] | null => {
    if (!connection.source || !connection.target || connection.source === connection.target) return null
    const when = !connection.sourceHandle || connection.sourceHandle === "out" ? null : connection.sourceHandle
    const kept = edges.filter((edge) => !(edge.from === connection.source && (edge.when ?? null) === when))
    return [...kept, { from: connection.source, to: connection.target, when }]
  }

  const onConnect = (connection: Connection) => {
    const edges = connect(model.edges, connection)
    if (onChange && edges) onChange({ ...model, edges })
  }

  // Dragging an end of an edge: dropped on another step it moves there, dropped on empty canvas it is removed.
  const reconnected = useRef(true)
  const onReconnectStart = () => {
    reconnected.current = false
  }
  const onReconnect = (old: Edge, connection: Connection) => {
    reconnected.current = true
    const edges = connect(model.edges.filter((edge) => edgeId(edge) !== old.id), connection)
    if (onChange && edges) onChange({ ...model, edges })
  }
  const onReconnectEnd = (_: unknown, old: Edge) => {
    if (!reconnected.current && onChange) {
      onChange({ ...model, edges: model.edges.filter((edge) => edgeId(edge) !== old.id) })
    }
    reconnected.current = true
  }

  return (
    <div className={cn("overflow-hidden rounded-xl border bg-background", className,
      expanded && !controlled && "fixed inset-0 z-50 size-auto min-h-0 rounded-none border-0")}>
      <ReactFlow nodes={nodes} edges={edges} nodeTypes={NODE_TYPES}
        colorMode={theme === "system" ? "system" : theme}
        onNodesChange={onNodesChange} onEdgesChange={onEdgesChange} onConnect={onConnect}
        edgesReconnectable={editable} reconnectRadius={14}
        onReconnectStart={onReconnectStart} onReconnect={onReconnect} onReconnectEnd={onReconnectEnd}
        onNodeClick={(_, node) => onSelect?.(node.id)} onPaneClick={() => onSelect?.(null)}
        nodesDraggable={editable} nodesConnectable={editable} edgesFocusable={editable} elementsSelectable
        deleteKeyCode={editable ? ["Backspace", "Delete"] : null}
        fitView fitViewOptions={fitOptions} minZoom={0.2} maxZoom={2}
        defaultEdgeOptions={{ type: "smoothstep" }} snapToGrid snapGrid={[10, 10]}>
        <FitOnSignal signal={fitSignal} options={fitOptions} />
        <Background variant={BackgroundVariant.Dots} gap={18} size={1} />
        <Controls showInteractive={false} position="bottom-left">
          <FullscreenButton expanded={expanded} onExpanded={setExpanded} options={fitOptions} />
        </Controls>
      </ReactFlow>
    </div>
  )
}

/** Fits the flow into view whenever `signal` changes (not on the first render: `fitView` does that). */
function FitOnSignal({ signal, options }: { signal?: number; options: FitViewOptions }) {
  const { fitView } = useReactFlow()
  const first = useRef(signal)
  useEffect(() => {
    if (signal !== first.current) {
      window.setTimeout(() => void fitView({ ...options, duration: 300 }), 50)
    }
  }, [signal, fitView, options])
  return null
}

/** Whether an open popup (select list, menu, dialog) should get Escape instead of the full screen. */
function popupOpen(): boolean {
  return document.querySelector('[role="dialog"], [role="listbox"], [role="menu"]') !== null
}

/**
 * Enlarges to the whole window (Esc leaves). Not the browser's fullscreen: select lists, dialogs and sheets open
 * outside the enlarged element and would stay invisible there. The flow is fitted into the new size.
 */
function FullscreenButton({ expanded, onExpanded, options }: {
  expanded: boolean
  onExpanded: (expanded: boolean) => void
  options: FitViewOptions
}) {
  const { fitView } = useReactFlow()
  const first = useRef(true)
  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    const timer = window.setTimeout(() => void fitView({ ...options, duration: 200 }), 80)
    return () => window.clearTimeout(timer)
  }, [expanded, fitView, options])
  useEffect(() => {
    if (!expanded) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !popupOpen()) onExpanded(false)
    }
    // capture: runs before the popups' own Escape handling, while they are still open
    window.addEventListener("keydown", onKey, true)
    return () => window.removeEventListener("keydown", onKey, true)
  }, [expanded, onExpanded])
  const label = expanded ? "Tam ekrandan çık" : "Tam ekran"
  return (
    <ControlButton title={label} aria-label={label} onClick={() => onExpanded(!expanded)}>
      {expanded ? <Minimize2Icon /> : <Maximize2Icon />}
    </ControlButton>
  )
}

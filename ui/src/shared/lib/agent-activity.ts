import { useSyncExternalStore } from "react"

/**
 * Whether a browser agent (WebMCP) is working on the page right now, and on what. Tools call {@link startAgentWork}
 * while they run; the app shows a glow around the screen meanwhile (AgentGlow), so the user always sees that the
 * agent, not they, is moving things.
 */
let running: { id: number; label: string }[] = []
let lingerUntil = 0
let next = 0
const listeners = new Set<() => void>()
let snapshot: { active: boolean; label: string | null } = { active: false, label: null }

function publish() {
  const active = running.length > 0 || Date.now() < lingerUntil
  const label = running.at(-1)?.label ?? (active ? snapshot.label : null)
  if (active !== snapshot.active || label !== snapshot.label) snapshot = { active, label }
  listeners.forEach((listener) => listener())
}

/** Marks the agent busy with `label` until the returned function is called (the glow stays a moment longer). */
export function startAgentWork(label: string): () => void {
  const id = ++next
  running = [...running, { id, label }]
  publish()
  return () => {
    running = running.filter((item) => item.id !== id)
    lingerUntil = Date.now() + 1800
    publish()
    window.setTimeout(publish, 1900)
  }
}

export function useAgentActivity() {
  return useSyncExternalStore(
    (listener) => { listeners.add(listener); return () => { listeners.delete(listener) } },
    () => snapshot,
  )
}

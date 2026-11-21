import { toast } from "sonner"

/**
 * Shows what a browser agent (WebMCP) is doing on the page: the area it is working with is scrolled into view and
 * outlined with a pulsing ring and a label for a few seconds, and a short notice says what happened. Areas are
 * marked with `data-agent-target="<name>"`, rows with `data-agent-row="<record id>"`.
 */
export interface AgentFocus {
  /** `data-agent-target` of an area, e.g. "flows", "runs", "approvals". */
  target?: string
  /** `data-agent-row`: one record inside it, preferred when it is on screen. */
  row?: string
  /** Shown on the outline, e.g. "Asistan: yeni çalışma". */
  label: string
}

const HIGHLIGHT_MS = 3600

/** Waits (up to 6 s, the page may still be loading) for the row or the area, then highlights it. */
export async function highlight(focus: AgentFocus): Promise<boolean> {
  const selectors = [
    focus.row ? `[data-agent-row="${CSS.escape(focus.row)}"]` : null,
    focus.target ? `[data-agent-target="${CSS.escape(focus.target)}"]` : null,
  ].filter((selector): selector is string => selector !== null)
  const deadline = Date.now() + 6000
  while (Date.now() < deadline) {
    for (const selector of selectors) {
      const element = document.querySelector<HTMLElement>(selector)
      if (element && element.getClientRects().length > 0) {   // also fixed elements (dialogs)
        mark(element, focus.label)
        return true
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 150))
  }
  return false
}

function mark(element: HTMLElement, label: string) {
  // a tall area (a long table) is aligned at its top, so its label stays on screen
  const tall = element.getBoundingClientRect().height > window.innerHeight * 0.6
  element.scrollIntoView({ behavior: "smooth", block: tall ? "start" : "center" })
  element.dataset.agentLabel = label
  // the label is placed against the element; a dialog or sticky panel already has its own position
  if (getComputedStyle(element).position === "static") element.dataset.agentStatic = ""
  element.classList.remove("agent-highlight")
  void element.offsetWidth   // restarts the animation when the same element is shown again
  element.classList.add("agent-highlight")
  window.setTimeout(() => {
    element.classList.remove("agent-highlight")
    delete element.dataset.agentLabel
    delete element.dataset.agentStatic
  }, HIGHLIGHT_MS)
}

/** A short notice that the agent did something, so the user is never surprised by a page change. */
export function agentNotice(title: string, description?: string) {
  toast(title, { description, icon: "✨", duration: 4000 })
}

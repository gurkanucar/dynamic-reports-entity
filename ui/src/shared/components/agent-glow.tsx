import { SparklesIcon } from "lucide-react"
import { cn } from "@/lib/utils"
import { useAgentActivity } from "@/shared/lib/agent-activity"

/**
 * A glowing, slowly turning frame around the whole screen while a browser agent (WebMCP) works, with a small label
 * of what it is doing. It never takes clicks: the page stays usable underneath.
 */
export function AgentGlow() {
  const { active, label } = useAgentActivity()
  return (
    <div aria-hidden={!active} className={cn("agent-glow", active && "agent-glow-on")}>
      <div className="agent-glow-frame" />
      <div className="agent-glow-haze" />
      {label && (
        <div role="status" className="agent-glow-label">
          <SparklesIcon className="size-3.5" />
          <span>Asistan: {label}</span>
        </div>
      )}
    </div>
  )
}

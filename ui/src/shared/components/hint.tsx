import type { ReactElement, ReactNode } from "react"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"

/**
 * A short explanation on hover and keyboard focus (instead of the browser's slow `title`). Nothing when `text` is
 * empty. A disabled button gets no pointer events, so wrap it (`wrap`) to still explain why it is disabled.
 */
export function Hint({ text, children, side = "top", wrap = false }: {
  text: ReactNode
  children: ReactElement
  side?: "top" | "right" | "bottom" | "left"
  wrap?: boolean
}) {
  if (!text) return children
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        {wrap ? <span tabIndex={0} className="inline-flex">{children}</span> : children}
      </TooltipTrigger>
      <TooltipContent side={side} className="max-w-72 whitespace-pre-line">{text}</TooltipContent>
    </Tooltip>
  )
}

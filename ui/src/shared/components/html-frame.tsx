import { cn } from "@/lib/utils"

/**
 * Shows an e-mail body as recipients see it, in a sandboxed frame: no scripts, no forms, no access to this page.
 * Always on white, like most mail clients, also in dark mode.
 */
export function HtmlFrame({ html, title, className }: { html: string; title: string; className?: string }) {
  return (
    <iframe title={title} sandbox="" srcDoc={html} referrerPolicy="no-referrer"
      className={cn("min-h-96 w-full rounded-md border bg-white", className)} />
  )
}

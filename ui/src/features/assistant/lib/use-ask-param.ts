import { useSearchParams } from "react-router"

/** A request handed over by the help chat (`?ask=...`): the specialist assistant starts with it in its input. */
export function useAskParam(): string | undefined {
  const [params] = useSearchParams()
  return params.get("ask")?.slice(0, 4000) || undefined
}

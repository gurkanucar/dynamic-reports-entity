import { AlertCircleIcon } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { ApiError, errorMessage } from "@/shared/api/http-client"

/** Shows an API error with its message and field problems; the trace id helps support find it in the logs. */
export function ErrorAlert({ error, title = "İşlem başarısız" }: { error: unknown; title?: string }) {
  if (!error) return null
  return (
    <Alert variant="destructive">
      <AlertCircleIcon />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>
        <p className="whitespace-pre-line">{errorMessage(error)}</p>
        {error instanceof ApiError && error.traceId && (
          <p className="text-xs opacity-70">Hata kodu: {error.code} · İz: {error.traceId}</p>
        )}
      </AlertDescription>
    </Alert>
  )
}

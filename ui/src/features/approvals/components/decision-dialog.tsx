import { CheckIcon, XIcon } from "lucide-react"
import { useState } from "react"
import { Link } from "react-router"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldLabel } from "@/components/ui/field"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { ErrorAlert } from "@/shared/components/error-alert"
import { formatDateTime } from "@/shared/lib/format"
import { useDecideApproval } from "../mutations/use-decide-approval"
import { useApprovalRequest } from "../queries/use-approval-request"
import { ApprovalStatusBadge } from "./approval-status-badge"

/** Details of one request with approve / reject; read-only once decided or when the user may not decide. */
export function DecisionDialog({ requestId, onClose }: { requestId: number | null; onClose: () => void }) {
  const request = useApprovalRequest(requestId)
  const decide = useDecideApproval()
  const [comment, setComment] = useState("")
  const data = request.data

  const submit = (approve: boolean) => data && decide.mutate({ id: data.id, approve, comment }, {
    onSuccess: () => {
      toast.success(approve ? "Onaylandı" : "Reddedildi")
      setComment("")
      onClose()
    },
  })

  return (
    <Dialog open={requestId !== null} onOpenChange={(open) => { if (!open) { decide.reset(); onClose() } }}>
      <DialogContent className="max-h-[calc(100svh-2rem)] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="break-words">{data?.title ?? "Onay talebi"}</DialogTitle>
          <DialogDescription>
            {data ? <>
              {data.source.link
                ? <Link className="underline underline-offset-4" to={data.source.link}>{data.source.label}</Link>
                : data.source.label} · {data.group.name}
            </> : "Yükleniyor…"}
          </DialogDescription>
        </DialogHeader>
        {request.isPending && <Skeleton className="h-32 w-full" />}
        {request.isError && <ErrorAlert error={request.error} />}
        {data && (
          <div className="flex flex-col gap-3 text-sm">
            <div className="flex flex-wrap items-center gap-2">
              <ApprovalStatusBadge status={data.status} />
              <span className="text-muted-foreground">
                {data.requestedBy} başlattı · {formatDateTime(data.createdAt)} · son tarih {formatDateTime(data.dueAt)}
              </span>
            </div>
            {data.description && <p className="whitespace-pre-wrap break-words rounded-md bg-muted p-3">{data.description}</p>}
            {data.decidedBy && (
              <p className="text-muted-foreground">
                {data.decidedBy} · {formatDateTime(data.decidedAt)}{data.comment ? `: “${data.comment}”` : ""}
              </p>
            )}
            {data.canDecide && (
              <Field>
                <FieldLabel htmlFor="decision-comment">Yorum (isteğe bağlı)</FieldLabel>
                <Textarea id="decision-comment" maxLength={1000} value={comment} onChange={(e) => setComment(e.target.value)} />
              </Field>
            )}
            {data.status === "pending" && !data.canDecide && (
              <p className="text-muted-foreground">Bu talebe karar veremezsiniz (başlatan kişi olabilirsiniz).</p>
            )}
            {decide.isError && <ErrorAlert error={decide.error} title="Kaydedilemedi" />}
          </div>
        )}
        {data?.canDecide && (
          <DialogFooter>
            <Button variant="outline" disabled={decide.isPending} onClick={() => submit(false)}>
              <XIcon data-icon="inline-start" />Reddet
            </Button>
            <Button disabled={decide.isPending} onClick={() => submit(true)}>
              {decide.isPending ? <Spinner data-icon="inline-start" /> : <CheckIcon data-icon="inline-start" />}Onayla
            </Button>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  )
}

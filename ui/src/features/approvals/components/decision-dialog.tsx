import { CheckIcon, ExternalLinkIcon, InfoIcon, XIcon } from "lucide-react"
import { useState, type ReactNode } from "react"
import { Link } from "react-router"
import { toast } from "sonner"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldLabel } from "@/components/ui/field"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { ErrorAlert } from "@/shared/components/error-alert"
import { Hint } from "@/shared/components/hint"
import { formatDateTime } from "@/shared/lib/format"
import { useDecideApproval } from "../mutations/use-decide-approval"
import { useApprovalRequest } from "../queries/use-approval-request"
import type { ApprovalRequestResponse } from "../types/approval.dto"
import { ApprovalStatusBadge } from "./approval-status-badge"
import { DueBadge, RequesterBadge } from "./approval-badges"

const BLOCKERS: Record<"not_member" | "own_request", { title: string; text: string }> = {
  not_member: {
    title: "Bu talebin onay grubunda değilsiniz",
    text: "Yalnızca talebin gittiği grubun üyeleri karar verebilir. Grubu “Onay grupları” sayfasından düzenleyebilirsiniz.",
  },
  own_request: {
    title: "Bu talep sizin adınıza açıldı",
    text: "Dört göz kuralı: kendi talebinize karar veremezsiniz; grubun başka bir üyesi karar vermeli. Akıştaki onay "
      + "adımında “kendi talebini onaylayabilir” seçeneği açılırsa bu kural kalkar.",
  },
}

/**
 * One request with everything needed to decide it; `intent` comes from the mail's "Onayla" / "Reddet" button and
 * puts that decision first (rejecting asks for a reason). The decision is still a click here: mail scanners open
 * links on their own. When the user may not decide, the buttons stay visible and say why.
 */
export function DecisionDialog({ requestId, intent, onClose }: {
  requestId: string | null
  intent?: "approve" | "reject" | null
  onClose: () => void
}) {
  const request = useApprovalRequest(requestId)
  const decide = useDecideApproval()
  const [comment, setComment] = useState("")
  const data = request.data
  const pending = data?.status === "pending"
  const blocker = data?.decisionBlocker === "not_member" || data?.decisionBlocker === "own_request"
    ? BLOCKERS[data.decisionBlocker] : null

  const submit = (approve: boolean) => data && decide.mutate({ id: data.id, approve, comment }, {
    onSuccess: () => {
      toast.success(approve ? "Talep onaylandı" : "Talep reddedildi")
      setComment("")
      onClose()
    },
  })

  return (
    <Dialog open={requestId !== null} onOpenChange={(open) => { if (!open) { decide.reset(); onClose() } }}>
      <DialogContent className="max-h-[calc(100svh-2rem)] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <div className="flex flex-wrap items-center gap-2">
            {data && <ApprovalStatusBadge status={data.status} />}
            {data && pending && <DueBadge dueAt={data.dueAt} />}
          </div>
          <DialogTitle className="text-lg break-words">{data?.title ?? "Onay talebi"}</DialogTitle>
          <DialogDescription>{data ? `${data.group.name} grubuna gelen talep` : "Yükleniyor…"}</DialogDescription>
        </DialogHeader>
        {request.isPending && <Skeleton className="h-40 w-full" />}
        {request.isError && <ErrorAlert error={request.error} />}
        {data && (
          <div className="flex flex-col gap-4 text-sm">
            {data.description && (
              <div className="rounded-lg border bg-muted/40 p-4 break-words whitespace-pre-wrap">{data.description}</div>
            )}
            <Details request={data} />
            {data.decidedBy && (
              <Alert>
                <InfoIcon />
                <AlertTitle>{data.status === "approved" ? "Onaylandı" : data.status === "rejected" ? "Reddedildi" : "Karar"}: {data.decidedBy}</AlertTitle>
                <AlertDescription>
                  {formatDateTime(data.decidedAt)}{data.comment ? ` · “${data.comment}”` : " · yorum yok"}
                </AlertDescription>
              </Alert>
            )}
            {pending && blocker && (
              <Alert>
                <InfoIcon />
                <AlertTitle>{blocker.title}</AlertTitle>
                <AlertDescription>{blocker.text}</AlertDescription>
              </Alert>
            )}
            {data.canDecide && (
              <Field>
                <FieldLabel htmlFor="decision-comment">
                  {intent === "reject" ? "Reddetme gerekçesi" : "Yorum (isteğe bağlı)"}
                </FieldLabel>
                <Textarea id="decision-comment" maxLength={1000} value={comment} autoFocus={intent === "reject"}
                  placeholder={intent === "reject" ? "Talep edene neden reddettiğinizi yazın"
                    : "Karar maillerinde ve akışta görünür"} onChange={(e) => setComment(e.target.value)} />
              </Field>
            )}
            {decide.isError && <ErrorAlert error={decide.error} title="Kaydedilemedi" />}
          </div>
        )}
        {data && pending && (
          <DialogFooter className="gap-2">
            <Hint text={blocker?.title} wrap>
              <Button variant={intent === "reject" ? "destructive" : "outline"} disabled={!data.canDecide || decide.isPending}
                onClick={() => submit(false)}>
                <XIcon data-icon="inline-start" />Reddet
              </Button>
            </Hint>
            <Hint text={blocker?.title} wrap>
              <Button variant={intent === "reject" ? "outline" : "default"} disabled={!data.canDecide || decide.isPending}
                autoFocus={intent === "approve"} onClick={() => submit(true)}>
                {decide.isPending ? <Spinner data-icon="inline-start" /> : <CheckIcon data-icon="inline-start" />}Onayla
              </Button>
            </Hint>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  )
}

function Details({ request }: { request: ApprovalRequestResponse }) {
  const rows: [string, ReactNode][] = [
    ["Kaynak", request.source.link ? (
      <Link className="inline-flex items-center gap-1 underline underline-offset-4" to={request.source.link}>
        {request.source.label}<ExternalLinkIcon className="size-3.5" />
      </Link>
    ) : request.source.label],
    ["Başlatan", <RequesterBadge key="r" requestedBy={request.requestedBy} />],
    ["Kimin adına", request.onBehalfOf.username === request.requestedBy ? "—" : request.onBehalfOf.username],
    ["Onay grubu", request.group.name],
    ["Oluşturuldu", formatDateTime(request.createdAt)],
    ["Son tarih", formatDateTime(request.dueAt)],
    ["Kendi talebini onaylama", request.allowSelfApproval ? "İzinli" : "Kapalı (dört göz)"],
  ]
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-2 rounded-lg border p-4 sm:grid-cols-[max-content_minmax(0,1fr)]">
      {rows.map(([label, value]) => (
        <div key={label} className="contents">
          <dt className="text-muted-foreground">{label}</dt>
          <dd className="min-w-0 break-words">{value}</dd>
        </div>
      ))}
    </dl>
  )
}

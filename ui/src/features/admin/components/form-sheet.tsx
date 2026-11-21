import { Trash2Icon } from "lucide-react"
import type { FormEvent, ReactNode } from "react"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Spinner } from "@/components/ui/spinner"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { ApiError } from "@/shared/api/http-client"
import { ErrorAlert } from "@/shared/components/error-alert"

/** Readable names of the API's fields, for the error list (fields may sit on another tab of the form). */
const FIELD_NAMES: Record<string, string> = {
  name: "Ad", fullName: "Ad soyad", email: "E-posta", username: "Kullanıcı adı", departmentId: "Departman",
  managerId: "Yönetici", parentId: "Üst departman", hireDate: "İşe giriş", salary: "Maaş", nationalId: "TC kimlik no",
  "profile.bloodType": "Kan grubu", code: "Kod", status: "Durum", budget: "Bütçe", endDate: "Bitiş", startDate: "Başlangıç",
  personnelId: "Kişi", trainingId: "Eğitim", completedOn: "Tamamlanma", nextRenewalDate: "Yenileme", score: "Puan",
  validityMonths: "Geçerlilik", category: "Kategori", allocationPercent: "Pay", costCenter: "Masraf merkezi",
}

/**
 * The side panel every master-data record is created and edited in: the form, server errors not tied to a field,
 * save, and delete behind a confirmation (the server refuses records still in use and says by what).
 */
export function FormSheet({ open, onClose, title, description, saving, error, onSubmit, onDelete, deleting,
  deleteText, wide, children }: {
  open: boolean
  onClose: () => void
  title: string
  description?: ReactNode
  saving: boolean
  /** Errors without a field (version mismatch, in use); field errors are shown by the inputs. */
  error?: unknown
  onSubmit: () => void
  onDelete?: () => void
  deleting?: boolean
  deleteText?: string
  wide?: boolean
  children: ReactNode
}) {
  const submit = (event: FormEvent) => {
    event.preventDefault()
    onSubmit()
  }
  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className={wide ? "w-full data-[side=right]:sm:max-w-2xl" : "w-full data-[side=right]:sm:max-w-lg"}>
        <form onSubmit={submit} className="flex h-full min-h-0 flex-col">
          <SheetHeader>
            <SheetTitle>{title}</SheetTitle>
            {description && <SheetDescription>{description}</SheetDescription>}
          </SheetHeader>
          <div className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto px-4 pb-4">
            {children}
            {error instanceof ApiError && error.errors.length > 0 ? (
              <Alert variant="destructive">
                <AlertTitle>Kaydedilemedi: düzeltilmesi gerekenler</AlertTitle>
                <AlertDescription>
                  <ul className="list-disc pl-4">
                    {error.errors.map((problem) => <li key={problem.field}><b>{FIELD_NAMES[problem.field] ?? problem.field}</b>: {problem.message}</li>)}
                  </ul>
                </AlertDescription>
              </Alert>
            ) : error !== undefined && error !== null && <ErrorAlert error={error} title="Kaydedilemedi" />}
          </div>
          <SheetFooter className="flex-row border-t">
            {onDelete && (
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button type="button" variant="ghost" className="text-destructive" disabled={deleting}>
                    {deleting ? <Spinner data-icon="inline-start" /> : <Trash2Icon data-icon="inline-start" />}Sil
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>Silinsin mi?</AlertDialogTitle>
                    <AlertDialogDescription>{deleteText ?? "Bu kayıt kalıcı olarak silinir."}</AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>Vazgeç</AlertDialogCancel>
                    <AlertDialogAction variant="destructive" onClick={onDelete}>Sil</AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            )}
            <Button type="button" variant="outline" className="ml-auto" onClick={onClose}>Vazgeç</Button>
            <Button type="submit" disabled={saving}>{saving && <Spinner data-icon="inline-start" />}Kaydet</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}

import { SaveIcon } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from "@/components/ui/dialog"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { Textarea } from "@/components/ui/textarea"
import { ErrorAlert } from "@/shared/components/error-alert"
import { DATASET_NAME_PATTERN, datasetNameFrom } from "../lib/dataset-name"
import { useSaveDataset } from "../mutations/use-save-dataset"
import type { DatasetDefinition } from "../types/dataset.types"
import type { SavedDatasetResponse } from "../types/saved-dataset.dto"

/**
 * Saves the dataset under a name, so flows (and everybody) can use it like a variable. A saved one is updated in
 * place; its name stays.
 */
export function SaveDatasetDialog({ definition, defaultLabel, saved, disabled, onSaved }: {
  definition: DatasetDefinition
  defaultLabel: string
  saved: SavedDatasetResponse | null
  disabled?: boolean
  onSaved: (saved: SavedDatasetResponse) => void
}) {
  const save = useSaveDataset()
  const [open, setOpen] = useState(false)
  const [label, setLabel] = useState(saved?.label ?? defaultLabel)
  const [name, setName] = useState(saved?.name ?? datasetNameFrom(defaultLabel))
  const [nameTouched, setNameTouched] = useState(false)
  const [description, setDescription] = useState(saved?.description ?? "")
  const nameInvalid = !DATASET_NAME_PATTERN.test(name)

  const reset = (next: boolean) => {
    if (next) {   // fresh values each time it opens
      setLabel(saved?.label ?? defaultLabel)
      setName(saved?.name ?? datasetNameFrom(defaultLabel))
      setNameTouched(false)
      setDescription(saved?.description ?? "")
      save.reset()
    }
    setOpen(next)
  }

  const submit = () => save.mutate({
    existing: saved?.id,
    request: {
      name, label: label.trim() || null, description: description.trim() || null, definition,
    },
  }, {
    onSuccess: (result) => {
      toast.success(saved ? "Veri seti güncellendi" : "Veri seti kaydedildi", { description: result.label ?? result.name })
      setOpen(false)
      onSaved(result)
    },
  })

  return (
    <Dialog open={open} onOpenChange={reset}>
      <DialogTrigger asChild>
        <Button variant="outline" disabled={disabled}><SaveIcon data-icon="inline-start" />{saved ? "Kaydet" : "Kaydet…"}</Button>
      </DialogTrigger>
      <DialogContent className="max-h-[calc(100svh-2rem)] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{saved ? "Veri setini güncelle" : "Veri setini kaydet"}</DialogTitle>
          <DialogDescription>
            Kayıtlı veri setleri akışlarda adıyla, bir değişken gibi kullanılır; değiştirdiğinizde onu kullanan her akış
            yeni hâli kullanır.
          </DialogDescription>
        </DialogHeader>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="dataset-label">Ad</FieldLabel>
            <Input id="dataset-label" value={label} maxLength={255} onChange={(e) => {
              setLabel(e.target.value)
              if (!saved && !nameTouched) setName(datasetNameFrom(e.target.value))
            }} />
          </Field>
          <Field data-invalid={nameInvalid ? true : undefined}>
            <FieldLabel htmlFor="dataset-name">Sistem adı</FieldLabel>
            <Input id="dataset-name" className="font-mono" value={name} disabled={!!saved} aria-invalid={nameInvalid}
              onChange={(e) => { setName(e.target.value); setNameTouched(true) }} />
            {nameInvalid
              ? <FieldError>2–40 karakter: küçük harf, rakam ve _; harfle başlar.</FieldError>
              : <FieldDescription>Akışlar veri setini bu adla bulur{saved ? "; kaydedildikten sonra değişmez" : ""}.</FieldDescription>}
          </Field>
          <Field>
            <FieldLabel htmlFor="dataset-description">Açıklama</FieldLabel>
            <Textarea id="dataset-description" className="min-h-16" maxLength={1000} value={description}
              onChange={(e) => setDescription(e.target.value)} />
          </Field>
        </FieldGroup>
        {save.isError && <ErrorAlert error={save.error} title="Kaydedilemedi" />}
        <DialogFooter>
          <Button disabled={save.isPending || nameInvalid || !label.trim()} onClick={submit}>
            {save.isPending && <Spinner data-icon="inline-start" />}{saved ? "Güncelle" : "Kaydet"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

import { PlusIcon, Trash2Icon } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { ErrorAlert } from "@/shared/components/error-alert"
import { useSaveApprovalGroup } from "../mutations/use-save-approval-group"
import type { ApprovalGroupResponse, GroupMemberDto } from "../types/approval.dto"

/** Create (group = null) or edit a group. Mount with a key so the form starts fresh. */
export function GroupDialog({ group, open, onOpenChange, onSaved }: {
  group: ApprovalGroupResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
  /** E.g. the flow editor selects a group created from its approval step. */
  onSaved?: (group: ApprovalGroupResponse) => void
}) {
  const save = useSaveApprovalGroup()
  const [name, setName] = useState(group?.name ?? "")
  const [description, setDescription] = useState(group?.description ?? "")
  const [members, setMembers] = useState<GroupMemberDto[]>(group?.members ?? [{ username: "", email: "" }])

  const submit = () => save.mutate({
    id: group?.id, version: group?.version,
    request: {
      name: name.trim(), description: description.trim() || null,
      members: members.filter((m) => m.username.trim())
        .map((m) => ({ username: m.username.trim(), email: m.email?.trim() || null })),
    },
  }, { onSuccess: (saved) => { toast.success("Grup kaydedildi"); onSaved?.(saved); onOpenChange(false) } })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[calc(100svh-2rem)] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{group ? "Grubu düzenle" : "Yeni onay grubu"}</DialogTitle>
          <DialogDescription>Akıştaki onay adımı bu gruptaki herhangi birinin kararını bekler.</DialogDescription>
        </DialogHeader>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="group-name">Ad</FieldLabel>
            <Input id="group-name" maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="group-description">Açıklama</FieldLabel>
            <Input id="group-description" maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <Field>
            <FieldLabel>Üyeler</FieldLabel>
            {members.map((member, index) => (
              <div key={index} className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] gap-2">
                <Input aria-label="Kullanıcı adı" placeholder="kullanıcı adı" value={member.username}
                  onChange={(e) => setMembers(members.map((m, i) => i === index ? { ...m, username: e.target.value } : m))} />
                <Input aria-label="E-posta" type="email" placeholder="e-posta (bildirim)" value={member.email ?? ""}
                  onChange={(e) => setMembers(members.map((m, i) => i === index ? { ...m, email: e.target.value } : m))} />
                <Button variant="ghost" size="icon" aria-label="Üyeyi çıkar"
                  onClick={() => setMembers(members.filter((_, i) => i !== index))}>
                  <Trash2Icon />
                </Button>
              </div>
            ))}
            <Button variant="outline" size="sm" className="self-start"
              onClick={() => setMembers([...members, { username: "", email: "" }])}>
              <PlusIcon data-icon="inline-start" />Üye ekle
            </Button>
            <FieldDescription>Kullanıcı adı sistemde kayıtlı olmalı; e-posta verilirse yeni taleplerde bildirim gider.</FieldDescription>
          </Field>
        </FieldGroup>
        {save.isError && <ErrorAlert error={save.error} title="Kaydedilemedi" />}
        <DialogFooter>
          <Button disabled={save.isPending || !name.trim()} onClick={submit}>
            {save.isPending && <Spinner data-icon="inline-start" />}Kaydet
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

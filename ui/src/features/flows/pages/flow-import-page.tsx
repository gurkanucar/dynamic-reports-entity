import { ArrowLeftIcon, CheckCircle2Icon, FileJsonIcon, UploadIcon } from "lucide-react"
import { useState, type ChangeEvent } from "react"
import { Link, useNavigate } from "react-router"
import { toast } from "sonner"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectSeparator, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Spinner } from "@/components/ui/spinner"
import { Switch } from "@/components/ui/switch"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Textarea } from "@/components/ui/textarea"
import { useQueryClient } from "@tanstack/react-query"
import { useCurrentUser } from "@/features/auth/queries/use-current-user"
import { errorMessage } from "@/shared/api/http-client"
import { PageHeader } from "@/shared/components/page-header"
import { StatusBadge } from "@/shared/components/status-badge"
import {
  applyImport, defaultDecision, KIND_LABELS, parseBundle, planImport, type Decision, type Dependency, type FlowBundle,
  type ImportResult,
} from "../transfer/flow-bundle"

const STATUS: Record<Dependency["status"], { label: string; tone: "success" | "warning" | "info" | "danger" }> = {
  same: { label: "Aynısı var", tone: "success" },
  different: { label: "Aynı adla farklı içerik", tone: "warning" },
  missing: { label: "Bu ortamda yok", tone: "info" },
  "not-exported": { label: "Dosyada yok", tone: "danger" },
}
const CREATE = "__create__"

/**
 * /flows/import: a migration assistant for a flow exported as JSON. It reads the file, checks every template, group,
 * survey and dataset the flow uses against this environment and asks what to do with each (reuse the identical one,
 * pick another, or create a copy); nothing is created until "İçe aktar".
 */
export function FlowImportPage() {
  const admin = useCurrentUser().data?.authorities.includes("PLATFORM_ADMIN") ?? false
  const [bundle, setBundle] = useState<FlowBundle | null>(null)
  const [dependencies, setDependencies] = useState<Dependency[] | null>(null)
  const [decisions, setDecisions] = useState<Record<string, Decision>>({})
  const [names, setNames] = useState<Record<string, string>>({})
  const [name, setName] = useState("")
  const [enableSchedules, setEnableSchedules] = useState(false)
  const [enableTriggers, setEnableTriggers] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)
  const [busy, setBusy] = useState<"plan" | "apply" | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [pasted, setPasted] = useState("")
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const load = async (text: string) => {
    setProblem(null)
    setBusy("plan")
    try {
      const parsed = parseBundle(text)
      const planned = await planImport(parsed, admin)
      setBundle(parsed)
      setDependencies(planned)
      setName(parsed.flow.name)
      const initial: Record<string, Decision> = {}
      const initialNames: Record<string, string> = {}
      for (const dependency of planned) {
        const decision = defaultDecision(dependency)
        if (decision) initial[dependency.key] = decision
        if (decision?.action === "create") initialNames[dependency.key] = decision.name
      }
      setDecisions(initial)
      setNames(initialNames)
    } catch (error) {
      setProblem(errorMessage(error))
    } finally {
      setBusy(null)
    }
  }
  const pick = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) await load(await file.text())
    event.target.value = ""
  }
  const run = async () => {
    if (!bundle || !dependencies) return
    setProblem(null)
    setBusy("apply")
    try {
      const final = Object.fromEntries(Object.entries(decisions).map(([key, decision]) =>
        [key, decision.action === "create" ? { action: "create", name: (names[key] ?? decision.name).trim() } : decision]))
      const done = await applyImport(bundle, dependencies, final as Record<string, Decision>,
        { name: name.trim() || bundle.flow.name, enableSchedules, enableTriggers })
      await queryClient.invalidateQueries()
      setResult(done)
      toast.success("Akış içe aktarıldı")
    } catch (error) {
      setProblem(errorMessage(error))
    } finally {
      setBusy(null)
    }
  }
  const unresolved = dependencies?.filter((dependency) => !decisions[dependency.key]) ?? []

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Akış içe aktar"
        description="Başka bir ortamdan dışa aktarılan akışı getirir; kullandığı şablon, grup, anket ve veri setlerini burada eşleştirirsiniz."
        actions={<Button variant="ghost" asChild><Link to="/flows"><ArrowLeftIcon data-icon="inline-start" />Akışlar</Link></Button>} />

      {problem && (
        <Alert variant="destructive">
          <AlertTitle>İçe aktarılamadı</AlertTitle>
          <AlertDescription className="whitespace-pre-line">{problem}</AlertDescription>
        </Alert>
      )}

      {result ? (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><CheckCircle2Icon className="text-success" />“{name}” içe aktarıldı</CardTitle>
            <CardDescription>
              {result.created.length > 0
                ? `Oluşturulanlar: ${result.created.map((item) => `${KIND_LABELS[item.kind]} “${item.name}”`).join(", ")}.`
                : "Var olan kayıtlar kullanıldı; yeni kayıt oluşturulmadı."}
            </CardDescription>
          </CardHeader>
          {result.webhooks > 0 && (
            <CardContent>
              <Alert>
                <AlertTitle>Webhook gizli anahtarları yeni</AlertTitle>
                <AlertDescription>
                  {result.webhooks} webhook tetikleyicisi yeni adres ve gizli anahtarla oluşturuldu; çağıran sisteme yenilerini verin
                  (akış sayfasında “Anahtarı yenile”).
                </AlertDescription>
              </Alert>
            </CardContent>
          )}
          <CardFooter className="gap-2">
            <Button onClick={() => navigate(`/flows/${result.flowId}`)}>Akışı aç</Button>
            <Button variant="outline" onClick={() => { setResult(null); setBundle(null); setDependencies(null) }}>Başka bir dosya</Button>
          </CardFooter>
        </Card>
      ) : !bundle ? (
        <Card>
          <CardHeader>
            <CardTitle>1. Dosyayı seçin</CardTitle>
            <CardDescription>Bir akışın sayfasındaki “Dışa aktar” ile indirilen .json dosyası.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <label className="flex cursor-pointer flex-col items-center gap-2 rounded-lg border border-dashed p-8 text-sm text-muted-foreground hover:bg-muted/40">
              {busy === "plan" ? <Spinner className="size-6" /> : <FileJsonIcon className="size-8" />}
              <span>{busy === "plan" ? "Dosya inceleniyor…" : "Dosya seçmek için tıklayın"}</span>
              <input type="file" accept="application/json,.json" className="sr-only" onChange={pick} disabled={busy !== null} />
            </label>
            <Field>
              <FieldLabel htmlFor="pasted">… ya da JSON'u yapıştırın</FieldLabel>
              <Textarea id="pasted" className="min-h-28 font-mono text-xs" value={pasted} onChange={(e) => setPasted(e.target.value)} />
            </Field>
          </CardContent>
          <CardFooter>
            <Button variant="outline" disabled={!pasted.trim() || busy !== null} onClick={() => load(pasted)}>
              <UploadIcon data-icon="inline-start" />İncele
            </Button>
          </CardFooter>
        </Card>
      ) : (
        <>
          <Card>
            <CardHeader>
              <CardTitle>2. Akış</CardTitle>
              <CardDescription>
                {bundle.flow.steps.length} adım · {bundle.schedules.length} zamanlama · {bundle.triggers.length} tetikleyici
                {bundle.exportedBy && ` · ${bundle.exportedBy} dışa aktardı`}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="flow-name">Akışın adı</FieldLabel>
                  <Input id="flow-name" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
                </Field>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field orientation="horizontal">
                    <Switch id="schedules" checked={enableSchedules} onCheckedChange={setEnableSchedules} disabled={bundle.schedules.length === 0} />
                    <div className="flex flex-col gap-1">
                      <FieldLabel htmlFor="schedules">Zamanlamaları açık getir</FieldLabel>
                      <FieldDescription>Kapalı gelir: kopyası kendiliğinden mail atmaya başlamasın.</FieldDescription>
                    </div>
                  </Field>
                  <Field orientation="horizontal">
                    <Switch id="triggers" checked={enableTriggers} onCheckedChange={setEnableTriggers} disabled={bundle.triggers.length === 0} />
                    <div className="flex flex-col gap-1">
                      <FieldLabel htmlFor="triggers">Tetikleyicileri açık getir</FieldLabel>
                      <FieldDescription>Webhook, Kafka ve anket tetikleyicileri; webhooklar yeni anahtar alır.</FieldDescription>
                    </div>
                  </Field>
                </div>
              </FieldGroup>
            </CardContent>
          </Card>

          <Card className="py-0">
            <CardHeader className="pt-6">
              <CardTitle>3. Kullandıkları</CardTitle>
              <CardDescription>
                {dependencies!.length === 0
                  ? "Akış başka bir kayda bağlı değil."
                  : "Her biri için var olanı kullanmayı ya da bu ortamda yenisini oluşturmayı seçin."}
              </CardDescription>
            </CardHeader>
            {dependencies!.length > 0 && (
              <CardContent className="px-0">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="pl-6">Kayıt</TableHead>
                      <TableHead>Bu ortamda</TableHead>
                      <TableHead className="w-[26rem] pr-6">Ne yapılsın?</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {dependencies!.map((dependency) => {
                      const decision = decisions[dependency.key]
                      const value = decision?.action === "create" ? CREATE : decision?.id
                      return (
                        <TableRow key={dependency.key}>
                          <TableCell className="max-w-72 pl-6">
                            <div className="text-xs text-muted-foreground">{KIND_LABELS[dependency.kind]}</div>
                            <div className="truncate font-medium">{dependency.name}</div>
                          </TableCell>
                          <TableCell>
                            <StatusBadge tone={STATUS[dependency.status].tone}>{STATUS[dependency.status].label}</StatusBadge>
                          </TableCell>
                          <TableCell className="pr-6">
                            <div className="flex flex-col gap-2">
                              <Select value={value} onValueChange={(next) => setDecisions((current) => ({
                                ...current,
                                [dependency.key]: next === CREATE
                                  ? { action: "create", name: names[dependency.key] ?? dependency.name }
                                  : { action: "use", id: next },
                              }))}>
                                <SelectTrigger className="w-full"><SelectValue placeholder="Seçin" /></SelectTrigger>
                                <SelectContent>
                                  {dependency.creatable && (
                                    <SelectGroup>
                                      <SelectItem value={CREATE}>Dosyadakinden yeni oluştur</SelectItem>
                                    </SelectGroup>
                                  )}
                                  {dependency.candidates.length > 0 && (
                                    <>
                                      <SelectSeparator />
                                      <SelectGroup>
                                        <SelectLabel>Aynı adlı</SelectLabel>
                                        {dependency.candidates.map((item) => (
                                          <SelectItem key={`c-${item.id}`} value={item.id}>Var olanı kullan: {item.name}</SelectItem>
                                        ))}
                                      </SelectGroup>
                                    </>
                                  )}
                                  {dependency.options.some((item) => !dependency.candidates.some((c) => c.id === item.id)) && (
                                    <>
                                      <SelectSeparator />
                                      <SelectGroup>
                                        <SelectLabel>Başka biriyle eşle</SelectLabel>
                                        {dependency.options.filter((item) => !dependency.candidates.some((c) => c.id === item.id))
                                          .map((item) => <SelectItem key={item.id} value={item.id}>{item.name}</SelectItem>)}
                                      </SelectGroup>
                                    </>
                                  )}
                                </SelectContent>
                              </Select>
                              {decision?.action === "create" && (
                                <Input aria-label="Yeni kaydın adı" value={names[dependency.key] ?? decision.name}
                                  onChange={(e) => setNames((current) => ({ ...current, [dependency.key]: e.target.value }))} />
                              )}
                              {!dependency.creatable && dependency.kind === "approval-group" && dependency.status !== "not-exported" && (
                                <span className="text-xs text-muted-foreground">Onay grubu yalnızca yöneticiler oluşturabilir; var olan bir grubu seçin.</span>
                              )}
                            </div>
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </CardContent>
            )}
            <CardFooter className="justify-between gap-2 border-t py-4">
              <span className="text-sm text-muted-foreground">
                {unresolved.length > 0
                  ? <Badge variant="destructive">{unresolved.length} kayıt için seçim gerekli</Badge>
                  : "Akış kaydedilemezse oluşturulan kayıtlar geri alınır."}
              </span>
              <div className="flex gap-2">
                <Button variant="outline" disabled={busy !== null} onClick={() => { setBundle(null); setDependencies(null) }}>Vazgeç</Button>
                <Button disabled={busy !== null || unresolved.length > 0} onClick={run}>
                  {busy === "apply" && <Spinner data-icon="inline-start" />}İçe aktar
                </Button>
              </div>
            </CardFooter>
          </Card>
        </>
      )}
    </div>
  )
}

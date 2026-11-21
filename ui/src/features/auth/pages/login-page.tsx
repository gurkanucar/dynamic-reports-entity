import { WorkflowIcon } from "lucide-react"
import { useState, type FormEvent } from "react"
import { Navigate, useLocation, useNavigate } from "react-router"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { ErrorAlert } from "@/shared/components/error-alert"
import { useLogin } from "../mutations/use-login"
import { useCurrentUser } from "../queries/use-current-user"

export function LoginPage() {
  const user = useCurrentUser()
  const login = useLogin()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const target = (location.state as { from?: string } | null)?.from ?? "/datasets"

  if (user.data) return <Navigate to={target} replace />

  const submit = (event: FormEvent) => {
    event.preventDefault()
    login.mutate({ username: username.trim(), password }, {
      onSuccess: () => navigate(target, { replace: true }),
      onSettled: () => setPassword(""),
    })
  }

  return (
    <div className="flex min-h-svh items-center justify-center bg-muted/40 p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <div className="flex items-center gap-2"><WorkflowIcon /><CardTitle>Veri & Akış</CardTitle></div>
          <CardDescription>Kurumsal hesabınızla giriş yapın.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="flex flex-col gap-4">
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="username">Kullanıcı adı veya e-posta</FieldLabel>
                <Input id="username" autoComplete="username" autoFocus value={username}
                  onChange={(e) => setUsername(e.target.value)} required />
              </Field>
              <Field>
                <FieldLabel htmlFor="password">Şifre</FieldLabel>
                <Input id="password" type="password" autoComplete="current-password" value={password}
                  onChange={(e) => setPassword(e.target.value)} required />
              </Field>
            </FieldGroup>
            {login.isError && <ErrorAlert error={login.error} title="Giriş yapılamadı" />}
            <Button type="submit" disabled={login.isPending}>
              {login.isPending && <Spinner data-icon="inline-start" />}
              Giriş yap
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

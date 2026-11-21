// Fetch wrapper for the Spring Boot API. The browser sends the HttpOnly session cookie by itself; requests that
// change something also send the CSRF token from the XSRF-TOKEN cookie. Errors arrive as RFC 9457 problems.

export interface FieldProblem {
  field: string
  code: string
  message: string
}

/** A failed request: `code` is the stable error code of the API (HTTP_<status> for older endpoints). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly errors: FieldProblem[]
  readonly traceId?: string

  constructor(status: number, code: string, message: string, errors: FieldProblem[] = [], traceId?: string) {
    super(message)
    this.status = status
    this.code = code
    this.errors = errors
    this.traceId = traceId
  }
}

let onUnauthorized: () => void = () => {}

/** Called when the session has ended (expired, logged out in another tab): the app shows the login page. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

function csrfToken(): string | null {
  const match = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(document.cookie)
  return match ? decodeURIComponent(match[1]) : null
}

function headers(method: string, extra?: HeadersInit): Headers {
  const result = new Headers(extra)
  // Without it Spring answers 401 with WWW-Authenticate and the browser shows its own login dialog.
  result.set("X-Requested-With", "XMLHttpRequest")
  const token = csrfToken()
  if (token && method !== "GET" && method !== "HEAD") result.set("X-XSRF-TOKEN", token)
  return result
}

async function toError(response: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {}
  try {
    body = await response.json()
  } catch {
    // not JSON
  }
  const detail = (body.detail as string) || (body.title as string) || response.statusText || "İstek başarısız"
  return new ApiError(response.status, (body.code as string) ?? `HTTP_${response.status}`, detail,
    (body.errors as FieldProblem[]) ?? [], body.traceId as string | undefined)
}

async function send(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase()
  const response = await fetch(path, { ...init, credentials: "same-origin", headers: headers(method, init.headers) })
  if (!response.ok) {
    const error = await toError(response)
    if (response.status === 401 && path !== "/api/session") onUnauthorized()
    throw error
  }
  return response
}

function jsonInit(method: string, body?: unknown, extra?: HeadersInit): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json", ...extra },
    body: body === undefined ? undefined : JSON.stringify(body),
  }
}

export const http = {
  raw: send,

  async get<T>(path: string): Promise<T> {
    return (await (await send(path)).json()) as T
  },

  async send<T>(method: "POST" | "PUT" | "DELETE", path: string, body?: unknown, extra?: HeadersInit): Promise<T> {
    const response = await send(path, jsonInit(method, body, extra))
    return response.status === 204 ? (undefined as T) : ((await response.json()) as T)
  },
}

/** Saves a response body as a file (CSV exports, files streamed by the API). */
export async function saveResponseAsFile(response: Response, fallbackName: string) {
  const disposition = response.headers.get("Content-Disposition") ?? ""
  const match = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(disposition)
  const name = match ? decodeURIComponent(match[1] ?? match[2]) : fallbackName
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement("a")
  link.href = url
  link.download = name
  link.click()
  URL.revokeObjectURL(url)
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const fields = error.errors.map((e) => `${e.field}: ${e.message}`).join("\n")
    return fields ? `${error.message}\n${fields}` : error.message
  }
  return error instanceof Error ? error.message : String(error)
}

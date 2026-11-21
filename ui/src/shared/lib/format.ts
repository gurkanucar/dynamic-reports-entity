const dateTime = new Intl.DateTimeFormat("tr-TR", { dateStyle: "short", timeStyle: "short" })

export function formatDateTime(value: string | null | undefined): string {
  return value ? dateTime.format(new Date(value)) : "—"
}

export function formatCell(value: unknown): string {
  if (value === null || value === undefined) return ""
  if (Array.isArray(value)) {
    return value.map((item) => (typeof item === "object" && item !== null
      ? Object.values(item as Record<string, unknown>).join(" · ") : String(item))).join("; ")
  }
  if (typeof value === "object") return JSON.stringify(value)
  if (typeof value === "number") return value.toLocaleString("tr-TR", { maximumFractionDigits: 2 })
  if (typeof value === "boolean") return value ? "Evet" : "Hayır"
  return String(value)
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

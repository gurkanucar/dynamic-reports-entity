import { ApiError } from "@/shared/api/http-client"

const money = new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY", maximumFractionDigits: 0 })
const date = new Intl.DateTimeFormat("tr-TR", { dateStyle: "medium" })

export const formatMoney = (value: number | null | undefined) => (value === null || value === undefined ? "—" : money.format(value))
export const formatDate = (value: string | null | undefined) => (value ? date.format(new Date(`${value}T00:00:00`)) : "—")

/** The server's field problems by field name (the first message each), to show under the inputs. */
export function fieldErrors(error: unknown): Record<string, string> {
  const errors: Record<string, string> = {}
  if (error instanceof ApiError) {
    for (const problem of error.errors) errors[problem.field] ??= problem.message
  }
  return errors
}

/** Empty inputs become null, numbers from text inputs become numbers. */
export const orNull = (value: string) => (value.trim() === "" ? null : value.trim())
export const numberOrNull = (value: string) => (value.trim() === "" ? null : Number(value.replace(",", ".")))

export const todayIso = () => new Date().toISOString().slice(0, 10)

import type { CronPreset } from "../types/flow.types"

export const CRON_PRESETS: CronPreset[] = [
  { value: "0 8 * * MON-FRI", label: "Hafta içi her gün 08:00" },
  { value: "0 8 * * MON", label: "Her pazartesi 08:00" },
  { value: "0 8 1 * *", label: "Her ayın 1'i 08:00" },
]

export const DEFAULT_TIME_ZONE = "Europe/Istanbul"

export function describeCron(cron: string): string {
  return CRON_PRESETS.find((preset) => preset.value === cron)?.label ?? cron
}

/** A rough check before the server's: 5 or 6 fields, or a macro such as @daily. */
export function looksLikeCron(cron: string): boolean {
  const value = cron.trim()
  return /^@(yearly|annually|monthly|weekly|daily|midnight|hourly)$/.test(value)
    || (value.split(/\s+/).length >= 5 && value.split(/\s+/).length <= 6)
}

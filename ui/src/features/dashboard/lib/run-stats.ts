import type { RunSummaryResponse } from "@/features/flows/types/flow.dto"

export interface DayRuns {
  day: string
  label: string
  completed: number
  failed: number
  other: number
}

const dayKey = (date: Date) => date.toLocaleDateString("sv-SE")   // YYYY-MM-DD in local time

/** Runs per day for the last `days` days (oldest first), split into completed / failed / the rest. */
export function runsPerDay(runs: RunSummaryResponse[], days: number, now = new Date()): DayRuns[] {
  const result: DayRuns[] = []
  for (let i = days - 1; i >= 0; i--) {
    const date = new Date(now)
    date.setDate(now.getDate() - i)
    result.push({ day: dayKey(date), label: date.toLocaleDateString("tr-TR", { day: "numeric", month: "short" }),
      completed: 0, failed: 0, other: 0 })
  }
  const byDay = new Map(result.map((entry) => [entry.day, entry]))
  for (const run of runs) {
    const entry = byDay.get(dayKey(new Date(run.createdAt)))
    if (!entry) continue
    if (run.status === "completed") entry.completed++
    else if (run.status === "failed") entry.failed++
    else entry.other++
  }
  return result
}

/** Runs created in the last `days` days. */
export function recent(runs: RunSummaryResponse[], days: number, now = new Date()): RunSummaryResponse[] {
  const since = now.getTime() - days * 24 * 3600_000
  return runs.filter((run) => new Date(run.createdAt).getTime() >= since)
}

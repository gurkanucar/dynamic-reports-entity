/** What the "schedule this dataset" dialog collects; turned into a flow (dataset + mail) and a schedule. */
export interface ScheduleDatasetForm {
  name: string
  templateId: string | null
  recipients: string
  cron: string
  timeZone: string
}

export interface CronPreset {
  value: string
  label: string
}

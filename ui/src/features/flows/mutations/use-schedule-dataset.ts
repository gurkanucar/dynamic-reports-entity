import { useMutation, useQueryClient } from "@tanstack/react-query"
import type { DatasetDefinition } from "@/features/datasets/types/dataset.types"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "../queries/flow.keys"
import type { FlowDetailResponse } from "../types/flow.dto"
import type { ScheduleDatasetForm } from "../types/flow.types"

/**
 * "Schedule this dataset": a flow that runs the dataset and mails it with the file attached, plus its cron schedule.
 * If the schedule cannot be saved, the just created flow is removed again, so nothing half-made stays behind.
 */
export function useScheduleDataset() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ form, definition, savedName }: {
      form: ScheduleDatasetForm
      definition: DatasetDefinition
      savedName?: string
    })
      : Promise<FlowDetailResponse> => {
      const flow = await flowsApi.create({
        name: form.name.trim(),
        steps: [
          savedName
            ? { type: "dataset", key: "dataset", datasetName: savedName, format: "xlsx", fileName: fileName(form.name) }
            : { type: "dataset", key: "dataset", definition, format: "xlsx", fileName: fileName(form.name) },
          {
            type: "mail", key: "mail", templateId: form.templateId!, attachments: ["dataset"],
            to: form.recipients.split(/[,;\s]+/).map((r) => r.trim()).filter(Boolean),
          },
        ],
      })
      try {
        await flowsApi.createSchedule(flow.id, { cron: form.cron, timeZone: form.timeZone, enabled: true })
      } catch (error) {
        await flowsApi.remove(flow.id).catch(() => undefined)
        throw error
      }
      return flow
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: flowKeys.all }),
  })
}

function fileName(name: string): string {
  const base = name.normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/ı/g, "i").replace(/[^\w .-]+/g, "")
    .trim().replace(/\s+/g, "-").toLowerCase()
  return `${base || "veri-seti"}.xlsx`
}

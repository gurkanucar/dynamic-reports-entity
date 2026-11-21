import { useMutation } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"

export function useDownloadFile() {
  return useMutation({ mutationFn: (fileId: number) => flowsApi.downloadFile(fileId) })
}

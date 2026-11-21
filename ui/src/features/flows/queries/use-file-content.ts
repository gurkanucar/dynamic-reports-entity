import { useQuery } from "@tanstack/react-query"
import { flowsApi } from "../api/flows.api"
import { flowKeys } from "./flow.keys"

/** A run file's text (a sent mail's HTML); files never change, so it is fetched once. */
export function useFileContent(fileId: string | null) {
  return useQuery({
    queryKey: flowKeys.fileContent(fileId ?? ""),
    queryFn: () => flowsApi.fileContent(fileId!),
    enabled: fileId !== null,
    staleTime: Infinity,
  })
}

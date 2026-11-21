import { useMutation, useQueryClient } from "@tanstack/react-query"
import { authApi } from "../api/auth.api"
import { sessionKeys } from "../queries/session.keys"
import type { Credentials } from "../types/auth.types"

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (credentials: Credentials) => authApi.login(credentials),
    onSuccess: (user) => {
      queryClient.clear()   // nothing cached for a previous user survives
      queryClient.setQueryData(sessionKeys.current, user)
    },
  })
}

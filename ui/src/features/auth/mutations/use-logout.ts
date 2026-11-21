import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useNavigate } from "react-router"
import { authApi } from "../api/auth.api"
import { sessionKeys } from "../queries/session.keys"

/**
 * Ends the session, then forgets the user and everything loaded for them and goes to the login page.
 * The session entry is set to null (not removed): `clear()` would detach the screens watching it, which then kept
 * showing the old user.
 */
export function useLogout() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: () => authApi.logout(),
    onSettled: () => {
      queryClient.setQueryData(sessionKeys.current, null)
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== sessionKeys.current[0] })
      queryClient.getMutationCache().clear()
      navigate("/login", { replace: true })
    },
  })
}

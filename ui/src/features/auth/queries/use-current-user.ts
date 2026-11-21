import { useQuery } from "@tanstack/react-query"
import { authApi } from "../api/auth.api"
import { sessionKeys } from "./session.keys"

/** Who is logged in (null = nobody). Asked once per page load; login and logout update it directly. */
export function useCurrentUser() {
  return useQuery({ queryKey: sessionKeys.current, queryFn: authApi.current, staleTime: Infinity, retry: false })
}

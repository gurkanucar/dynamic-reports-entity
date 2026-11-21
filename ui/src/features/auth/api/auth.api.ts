import { ApiError, http } from "@/shared/api/http-client"
import type { Credentials, SessionUser } from "../types/auth.types"

/** Browser session: an HttpOnly cookie shared by all tabs; the password is sent once and never stored. */
export const authApi = {
  login: async (credentials: Credentials) =>
    (await http.send<{ data: SessionUser }>("POST", "/api/session", credentials)).data,

  /** The logged-in user, or null without a valid session. */
  async current(): Promise<SessionUser | null> {
    try {
      return (await http.get<{ data: SessionUser }>("/api/session")).data
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) return null
      throw error
    }
  },

  logout: () => http.send<void>("DELETE", "/api/session"),
}

export interface Credentials {
  username: string
  password: string
}

/** GET /api/session: who is logged in. */
export interface SessionUser {
  username: string
  authorities: string[]
}

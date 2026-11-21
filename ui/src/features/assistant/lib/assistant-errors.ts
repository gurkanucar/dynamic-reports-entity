import type { ErrorInfo } from "@/shared/types/api.types"

/** What a failed suggestion means, in the user's words; unknown codes keep the server's message. */
const MESSAGES: Record<string, string> = {
  ASSISTANT_TIMEOUT: "Asistan zamanında cevap veremedi. Daha küçük bir değişiklik isteyin ya da biraz sonra tekrar deneyin.",
  ASSISTANT_CONNECTION_LOST: "Yapay zekâ servisiyle bağlantı koptu. Lütfen tekrar deneyin.",
  ASSISTANT_UNAVAILABLE: "Yapay zekâ servisine şu an ulaşılamıyor. Biraz sonra tekrar deneyin.",
  ASSISTANT_RATE_LIMITED: "Yapay zekâ servisi şu an yoğun. Birkaç saniye sonra tekrar deneyin.",
  SUGGESTION_TIMED_OUT: "Asistan zamanında bitiremedi. Daha küçük bir değişiklik isteyin.",
  ASSISTANT_GAVE_UP: "Asistan geçerli bir taslak çıkaramadı. İsteği biraz daha açık yazar mısınız?",
}

export function assistantErrorMessage(error: ErrorInfo | null | undefined, fallback: string): string {
  if (!error) return fallback
  return MESSAGES[error.code] ?? error.message ?? fallback
}

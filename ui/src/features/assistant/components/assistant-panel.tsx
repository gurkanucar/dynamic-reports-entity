import type { DatasetDefinition } from "@/features/datasets/types/dataset.types"
import { useAskParam } from "../lib/use-ask-param"
import { AssistantChat } from "./assistant-chat"

const EXAMPLES = [
  "Her departmanda kaç aktif çalışan var?",
  "Son 90 günde işe girenleri departmanlarıyla listele",
  "Fire Safety eğitimi olmayan aktif kişiler",
]

/** The dataset assistant next to the dataset editor; a reply with a dataset is applied to the editor right away. */
export function AssistantPanel({ definition, model, onApply }: {
  definition: DatasetDefinition | null
  model: string | null
  onApply: (definition: DatasetDefinition) => void
}) {
  const ask = useAskParam()
  return (
    // stays in view while the editor scrolls; the conversation scrolls inside it
    <AssistantChat kind="dataset" title="Veri seti asistanı" model={model} examples={EXAMPLES}
      description="Ne görmek istediğinizi yazın. Asistan yalnızca alan adlarını görür, verilerinizi görmez."
      placeholder={{ empty: "Nasıl bir veri seti istiyorsunuz?", change: "Veri setini nasıl değiştireyim?" }}
      busyLabel="Veri seti hazırlanıyor…" current={definition?.columns.length ? definition : null}
      applicable={(reply) => reply.definition !== null} onApply={(reply) => onApply(reply.definition!)}
      className="h-[min(42rem,calc(100svh-7rem))] xl:sticky xl:top-4" initialInput={ask} />
  )
}

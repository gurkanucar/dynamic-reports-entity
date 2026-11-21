import { useState } from "react"
import { Field, FieldDescription, FieldError } from "@/components/ui/field"
import { Textarea } from "@/components/ui/textarea"
import type { DatasetDefinition } from "../types/dataset.types"

/** The whole definition as JSON, for what the visual editor does not cover. Applied when it parses. */
export function JsonEditor({ definition, onChange }: {
  definition: DatasetDefinition
  onChange: (definition: DatasetDefinition) => void
}) {
  const [text, setText] = useState(() => JSON.stringify(definition, null, 2))
  const [error, setError] = useState<string | null>(null)
  const [shown, setShown] = useState(definition)

  // Follow outside changes (visual editor, assistant) while rendering, unless the text already says the same.
  if (shown !== definition) {
    setShown(definition)
    if (!sameDefinition(text, definition)) {
      setText(JSON.stringify(definition, null, 2))
      setError(null)
    }
  }

  const change = (value: string) => {
    setText(value)
    try {
      const parsed = JSON.parse(value) as DatasetDefinition
      if (typeof parsed.root !== "string" || !Array.isArray(parsed.columns)) {
        setError("\"root\" (metin) ve \"columns\" (liste) gerekli.")
        return
      }
      setError(null)
      onChange(parsed)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <Field data-invalid={error ? true : undefined}>
      <Textarea className="min-h-80 font-mono text-xs" spellCheck={false} value={text} aria-invalid={!!error}
        onChange={(e) => change(e.target.value)} />
      {error ? <FieldError>{error}</FieldError>
        : <FieldDescription>Biçim: http/datasets.http içindeki örneklerle aynı.</FieldDescription>}
    </Field>
  )
}

function sameDefinition(text: string, definition: DatasetDefinition): boolean {
  try {
    return JSON.stringify(JSON.parse(text)) === JSON.stringify(definition)
  } catch {
    return false
  }
}

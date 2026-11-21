import { LibraryIcon } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { DATASET_EXAMPLES, type DatasetExample } from "../lib/examples"

const GROUPS = [...new Set(DATASET_EXAMPLES.map((example) => example.group))]

/** Searchable list of ready-made datasets; picking one replaces the editor content. */
export function ExamplesPicker({ onPick }: { onPick: (example: DatasetExample) => void }) {
  const [open, setOpen] = useState(false)
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="outline"><LibraryIcon data-icon="inline-start" />Örnekler</Button>
      </PopoverTrigger>
      <PopoverContent className="flex max-h-(--radix-popover-content-available-height) w-[28rem] max-w-[calc(100vw-2rem)] flex-col p-0"
        align="end" collisionPadding={16}>
        <Command className="min-h-0">
          <CommandInput placeholder="Örnek ara…" />
          <CommandList className="max-h-[min(26rem,calc(var(--radix-popover-content-available-height)-3rem))]">
            <CommandEmpty>Örnek bulunamadı.</CommandEmpty>
            {GROUPS.map((group) => (
              <CommandGroup key={group} heading={group}>
                {DATASET_EXAMPLES.filter((example) => example.group === group).map((example) => (
                  <CommandItem key={example.id} value={`${example.title} ${example.definition.root}`}
                    onSelect={() => { onPick(example); setOpen(false) }}>
                    <span className="truncate">{example.title}</span>
                    <span className="ml-auto shrink-0 text-xs text-muted-foreground">{example.definition.root}</span>
                  </CommandItem>
                ))}
              </CommandGroup>
            ))}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}

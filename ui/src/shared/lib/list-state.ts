import { useEffect, useState } from "react"

/** A query string from parameters: lists repeat the name (`status=a&status=b`), empty values are left out. */
export function queryString(params: Record<string, string | number | null | undefined | (string | number)[]>): string {
  const search = new URLSearchParams()
  for (const [name, value] of Object.entries(params)) {
    if (Array.isArray(value)) value.forEach((item) => search.append(name, String(item)))
    else if (value !== null && value !== undefined && value !== "") search.set(name, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ""
}

function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return debounced
}

/**
 * Search text, sort and page of a server-side list (docs/REST API Tasarım Rehberi.md, 7). Changing the search or the
 * sort goes back to page 1; `query` is what to send (debounced, at least 2 characters).
 */
export function useListState(defaultSort: string) {
  const [search, setSearchText] = useState("")
  const [sort, setSortValue] = useState(defaultSort)
  const [page, setPage] = useState(1)
  const debounced = useDebounced(search.trim(), 300)
  const query = debounced.length >= 2 ? debounced : ""
  // a new search starts on page 1 (adjusted while rendering, not in an effect)
  const [searched, setSearched] = useState(query)
  if (searched !== query) {
    setSearched(query)
    setPage(1)
  }
  return {
    search,
    setSearch: setSearchText,
    query,
    sort,
    setSort: (next: string) => { setSortValue(next); setPage(1) },
    page,
    setPage,
    /** Call after changing another filter. */
    resetPage: () => setPage(1),
  }
}

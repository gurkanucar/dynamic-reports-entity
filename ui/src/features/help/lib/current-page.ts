/**
 * The page the user is on, as the help chat and browser agents (WebMCP) see it: the path (no query string, which may
 * carry ids or tokens) and the page's heading.
 */
export function currentPage(pathname: string): { path: string; title: string } {
  const heading = document.querySelector("main h1, [data-slot=sidebar-inset] h1")?.textContent?.trim() ?? ""
  return { path: pathname, title: heading.slice(0, 80) }
}

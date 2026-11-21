import {
  CheckCheckIcon, ClipboardListIcon, DatabaseIcon, LayoutDashboardIcon, HistoryIcon, LogOutIcon, MailIcon, MonitorIcon, MoonIcon, SunIcon, UsersIcon,
  WorkflowIcon,
} from "lucide-react"
import { useEffect } from "react"
import { NavLink, Outlet, useLocation } from "react-router"
import { useTheme } from "@/components/theme-provider"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuRadioGroup, DropdownMenuRadioItem, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent, SidebarHeader, SidebarInset,
  SidebarMenu, SidebarMenuButton, SidebarMenuItem, SidebarProvider, SidebarTrigger, useSidebar,
} from "@/components/ui/sidebar"
import { useLogout } from "@/features/auth/mutations/use-logout"
import { useCurrentUser } from "@/features/auth/queries/use-current-user"
import { Separator } from "@/components/ui/separator"
import { AppBreadcrumb } from "./app-breadcrumb"

const NAVIGATION = [
  { to: "/", label: "Pano", icon: LayoutDashboardIcon, end: true },
  { to: "/datasets", label: "Veri setleri", icon: DatabaseIcon },
  { to: "/flows", label: "Akışlar", icon: WorkflowIcon },
  { to: "/runs", label: "Çalışmalar", icon: HistoryIcon },
  { to: "/surveys", label: "Anketler", icon: ClipboardListIcon },
  { to: "/approvals", label: "Onaylarım", icon: CheckCheckIcon },
  { to: "/approval-groups", label: "Onay grupları", icon: UsersIcon },
  { to: "/templates", label: "Mail şablonları", icon: MailIcon },
]

export function AppLayout() {
  const user = useCurrentUser()
  const logout = useLogout()
  const location = useLocation()
  return (
    <SidebarProvider defaultOpen={sidebarRemembered()}>
      <CloseSidebarOnNavigate />
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <div className="flex items-center gap-2 px-1 py-1.5 group-data-[collapsible=icon]:px-0">
            <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
              <WorkflowIcon className="size-4" />
            </div>
            <span className="truncate font-semibold tracking-tight group-data-[collapsible=icon]:hidden">Veri & Akış</span>
          </div>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupContent>
              <SidebarMenu className="gap-1">
                {NAVIGATION.map((item) => (
                  <SidebarMenuItem key={item.to}>
                    <SidebarMenuButton asChild tooltip={item.label} isActive={item.end ? location.pathname === item.to : location.pathname.startsWith(item.to)}>
                      <NavLink to={item.to}>
                        <item.icon />
                        <span>{item.label}</span>
                      </NavLink>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
        <SidebarFooter>
          <SidebarMenu className="gap-1">
            <SidebarMenuItem>
              <ThemeMenu />
            </SidebarMenuItem>
            <SidebarMenuItem>
              <SidebarMenuButton tooltip="Çıkış yap" onClick={() => logout.mutate()}>
                <LogOutIcon />
                <span className="truncate">{user.data?.username} · Çıkış</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
      </Sidebar>
      <SidebarInset>
        {/* sticky: the sidebar toggle stays reachable on long pages (the flow editor) */}
        <header className="sticky top-0 z-20 flex h-12 shrink-0 items-center gap-2 border-b bg-background px-4">
          <SidebarTrigger />
          {/* full height: meets the bottom border instead of floating in the middle */}
          <Separator orientation="vertical" className="mr-1 self-stretch data-[orientation=vertical]:h-auto" />
          <AppBreadcrumb />
        </header>
        {/* SidebarInset is the page's <main> already */}
        <div className="flex min-w-0 flex-1 flex-col gap-6 p-4 md:p-6">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}

/** The sidebar's last state ("sidebar_state" cookie, written by the sidebar itself); open when never set. */
function sidebarRemembered(): boolean {
  return !document.cookie.split("; ").includes("sidebar_state=false")
}

/** On phones the sidebar is a drawer: close it once a page is chosen. */
function CloseSidebarOnNavigate() {
  const { isMobile, setOpenMobile } = useSidebar()
  const { pathname } = useLocation()
  useEffect(() => {
    if (isMobile) setOpenMobile(false)
  }, [pathname, isMobile, setOpenMobile])
  return null
}

const THEMES = [
  { value: "light", label: "Açık", icon: SunIcon },
  { value: "dark", label: "Koyu", icon: MoonIcon },
  { value: "system", label: "Sistem", icon: MonitorIcon },
] as const

/** Light, dark or the system's choice; remembered in the browser. */
function ThemeMenu() {
  const { theme, setTheme } = useTheme()
  const current = THEMES.find((option) => option.value === theme) ?? THEMES[2]
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <SidebarMenuButton tooltip="Tema">
          <current.icon />
          <span>Tema: {current.label}</span>
        </SidebarMenuButton>
      </DropdownMenuTrigger>
      <DropdownMenuContent side="top" align="start">
        <DropdownMenuGroup>
          <DropdownMenuRadioGroup value={theme} onValueChange={(value) => setTheme(value as typeof theme)}>
            {THEMES.map((option) => (
              <DropdownMenuRadioItem key={option.value} value={option.value}>
                <option.icon />{option.label}
              </DropdownMenuRadioItem>
            ))}
          </DropdownMenuRadioGroup>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export function NotFound() {
  return (
    <div className="flex flex-col items-start gap-2">
      <p>Sayfa bulunamadı.</p>
      <Button asChild variant="outline"><NavLink to="/">Ana sayfa</NavLink></Button>
    </div>
  )
}

import {
  AwardIcon, BriefcaseIcon, Building2Icon, CheckCheckIcon, ChevronRightIcon, DatabaseZapIcon, ClipboardListIcon, DatabaseIcon, GraduationCapIcon,
  HistoryIcon, LayoutDashboardIcon, LogOutIcon, MailIcon, MonitorIcon, MoonIcon, SparklesIcon, SunIcon, UserRoundIcon,
  UsersIcon, WorkflowIcon, type LucideIcon,
} from "lucide-react"
import { useEffect } from "react"
import { Link, NavLink, Outlet, useLocation } from "react-router"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { useTheme } from "@/components/theme-provider"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuRadioGroup, DropdownMenuRadioItem, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent, SidebarGroupLabel, SidebarHeader, SidebarInset,
  SidebarMenu, SidebarMenuButton, SidebarMenuItem, SidebarMenuSub, SidebarMenuSubButton, SidebarMenuSubItem, SidebarProvider,
  SidebarTrigger, useSidebar,
} from "@/components/ui/sidebar"
import { useLogout } from "@/features/auth/mutations/use-logout"
import { useCurrentUser } from "@/features/auth/queries/use-current-user"
import { Separator } from "@/components/ui/separator"
import { AppBreadcrumb } from "./app-breadcrumb"

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  end?: boolean
}

interface NavGroup {
  label?: string
  /** Shown only to platform admins. */
  admin?: boolean
  items: NavItem[]
  /** Folded into one expandable entry (with this label and icon): for sections that grow. */
  folder?: { label: string; icon: LucideIcon }
}

/** The menu, grouped by what people come to do: daily work first, setup and master data last. */
const NAVIGATION: NavGroup[] = [
  { items: [
    { to: "/", label: "Pano", icon: LayoutDashboardIcon, end: true },
    { to: "/approvals", label: "Onaylarım", icon: CheckCheckIcon },
  ] },
  { label: "Analiz ve otomasyon", items: [
    { to: "/datasets", label: "Veri setleri", icon: DatabaseIcon },
    { to: "/flows", label: "Akışlar", icon: WorkflowIcon },
    { to: "/runs", label: "Çalışmalar", icon: HistoryIcon },
  ] },
  { label: "Formlar ve iletişim", items: [
    { to: "/surveys", label: "Anketler", icon: ClipboardListIcon },
    { to: "/templates", label: "Mail şablonları", icon: MailIcon },
    { to: "/approval-groups", label: "Onay grupları", icon: UsersIcon },
  ] },
  { label: "Yönetim", admin: true, folder: { label: "Kayıtlar", icon: DatabaseZapIcon }, items: [
    { to: "/admin/personnel", label: "Personel", icon: UserRoundIcon },
    { to: "/admin/departments", label: "Departmanlar", icon: Building2Icon },
    { to: "/admin/projects", label: "Projeler", icon: BriefcaseIcon },
    { to: "/admin/trainings", label: "Eğitimler", icon: GraduationCapIcon },
    { to: "/admin/training-records", label: "Eğitim kayıtları", icon: AwardIcon },
    { to: "/admin/skills", label: "Beceriler", icon: SparklesIcon },
  ] },
]

/**
 * A section folded into one entry (e.g. "Kayıtlar"): closed by default, open while one of its pages is shown; the
 * chevron turns as it opens. Collapsed to icons, the entry links to the first page.
 */
function NavFolder({ folder, items }: { folder: NonNullable<NavGroup["folder"]>; items: NavItem[] }) {
  const location = useLocation()
  const { state, isMobile } = useSidebar()
  const inside = items.some((item) => location.pathname.startsWith(item.to))
  if (state === "collapsed" && !isMobile) {
    return (
      <SidebarMenuItem>
        <SidebarMenuButton asChild tooltip={folder.label} isActive={inside}>
          <NavLink to={items[0].to}><folder.icon /><span>{folder.label}</span></NavLink>
        </SidebarMenuButton>
      </SidebarMenuItem>
    )
  }
  return (
    <Collapsible asChild defaultOpen={inside} className="group/folder">
      <SidebarMenuItem>
        <CollapsibleTrigger asChild>
          <SidebarMenuButton tooltip={folder.label} >
            <folder.icon />
            <span>{folder.label}</span>
            <ChevronRightIcon className="ml-auto transition-transform duration-200 group-data-[state=open]/folder:rotate-90" />
          </SidebarMenuButton>
        </CollapsibleTrigger>
        <CollapsibleContent className="overflow-hidden data-[state=closed]:animate-collapsible-up data-[state=open]:animate-collapsible-down">
          <SidebarMenuSub>
            {items.map((item) => (
              <SidebarMenuSubItem key={item.to}>
                <SidebarMenuSubButton asChild isActive={location.pathname.startsWith(item.to)}>
                  <NavLink to={item.to}><item.icon /><span>{item.label}</span></NavLink>
                </SidebarMenuSubButton>
              </SidebarMenuSubItem>
            ))}
          </SidebarMenuSub>
        </CollapsibleContent>
      </SidebarMenuItem>
    </Collapsible>
  )
}

export function AppLayout() {
  const user = useCurrentUser()
  const logout = useLogout()
  const location = useLocation()
  const admin = user.data?.authorities.includes("PLATFORM_ADMIN") ?? false
  return (
    <SidebarProvider defaultOpen={sidebarRemembered()}>
      <CloseSidebarOnNavigate />
      <Sidebar collapsible="icon">
        <SidebarHeader>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton asChild size="lg" tooltip="Ana sayfa" className="group/brand">
                <Link to="/" aria-label="Veri & Akış ana sayfa">
                  <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground shadow-sm transition-transform duration-200 group-hover/brand:scale-110 group-hover/brand:-rotate-6 group-active/brand:scale-95">
                    <WorkflowIcon className="size-4" />
                  </div>
                  <span className="truncate font-semibold tracking-tight transition-colors group-hover/brand:text-sidebar-primary">Veri & Akış</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarHeader>
        <SidebarContent className="gap-0">
          {NAVIGATION.filter((group) => !group.admin || admin).map((group, index) => (
            <SidebarGroup key={group.label ?? index}>
              {group.label && <SidebarGroupLabel>{group.label}</SidebarGroupLabel>}
              <SidebarGroupContent>
                <SidebarMenu className="gap-1">
                  {group.folder ? <NavFolder folder={group.folder} items={group.items} /> : group.items.map((item) => (
                    <SidebarMenuItem key={item.to}>
                      <SidebarMenuButton asChild tooltip={item.label}
                        isActive={item.end ? location.pathname === item.to : location.pathname.startsWith(item.to)}>
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
          ))}
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

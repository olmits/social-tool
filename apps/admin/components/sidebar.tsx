"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { BarChart3, Check, ChevronsUpDown, ListChecks, LogOut, Plus, Radar, Users, CalendarDays } from "lucide-react";
import { useAccountState } from "@/context/account-context";
import { useAccountActions } from "@/lib/actions/useAccountActions";
import { ACCOUNTS_BY_PLATFORM, PLATFORM_META, type Platform } from "@/lib/mock-data";
import { cn } from "@/lib/utils";

const NAV_ITEMS = [
  { href: "/radar", label: "Trend Radar", icon: Radar, badge: "24", badgeTone: "warn" as const },
  { href: "/review", label: "Review & Approve", icon: ListChecks, badge: "6", badgeTone: "neutral" as const },
  { href: "/schedule", label: "Scheduling", icon: CalendarDays },
];

export function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const { platform, account } = useAccountState();
  const { setAccount } = useAccountActions();
  const [menuOpen, setMenuOpen] = useState(false);
  const selectedMeta = PLATFORM_META[platform];

  return (
    <aside className="hidden h-full w-[258px] shrink-0 flex-col border-r border-border bg-muted/40 md:flex">
      <div className="flex items-center gap-2.5 px-4 pb-3 pt-4">
        <div className="flex size-7 items-center justify-center rounded-lg bg-primary">
          <div className="size-2.5 rounded-full border-2 border-primary-foreground" />
        </div>
        <span className="text-[15px] font-semibold tracking-tight">Cadence</span>
      </div>

      <div className="relative px-3 pb-3">
        <button
          onClick={() => setMenuOpen((v) => !v)}
          className="flex w-full items-center gap-2.5 rounded-lg border border-border bg-background px-2.5 py-2 text-left hover:border-neutral-400/70 dark:hover:border-neutral-600"
        >
          <span className={cn("flex size-7 shrink-0 items-center justify-center rounded-md", selectedMeta.badgeBg)}>
            <span className="size-2 rounded-full" style={{ background: selectedMeta.dot }} />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-[12.5px] font-semibold">{account}</span>
            <span className="block text-[11px] text-muted-foreground">{platform}</span>
          </span>
          <ChevronsUpDown className="size-3.5 text-neutral-400" />
        </button>

        {menuOpen && (
          <div className="absolute left-3 right-3 top-[52px] z-40 max-h-[60vh] overflow-auto rounded-xl border border-border bg-popover p-1.5 shadow-lg">
            {(Object.entries(ACCOUNTS_BY_PLATFORM) as [Platform, string[]][]).map(([p, accounts]) => {
              const meta = PLATFORM_META[p];
              return (
                <div key={p}>
                  <div className="flex items-center gap-1.5 px-2 pb-1 pt-2 text-[10.5px] font-semibold uppercase tracking-wide text-muted-foreground">
                    <span className="size-1.5 rounded-full" style={{ background: meta.dot }} />
                    {p}
                    {accounts.length === 1 && (
                      <span className="font-medium normal-case tracking-normal text-neutral-300 dark:text-neutral-600">· single</span>
                    )}
                  </div>
                  {accounts.map((a) => {
                    const active = platform === p && account === a;
                    return (
                      <button
                        key={a}
                        onClick={() => {
                          setAccount(p, a);
                          setMenuOpen(false);
                        }}
                        className={cn(
                          "mb-0.5 flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-[12.5px] font-medium hover:bg-muted",
                          active && "bg-muted"
                        )}
                      >
                        <span className="min-w-0 flex-1 truncate">{a}</span>
                        {active && <Check className="size-3.5 shrink-0" />}
                      </button>
                    );
                  })}
                </div>
              );
            })}
            <div className="my-1 border-t border-border" />
            <button className="flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-[12.5px] font-medium text-neutral-600 hover:bg-muted dark:text-neutral-400">
              <Plus className="size-3.5" />
              Connect account…
            </button>
          </div>
        )}
      </div>

      <nav className="flex-1 overflow-auto px-3">
        <div className="px-2 pb-2 pt-1.5 text-[10.5px] font-semibold uppercase tracking-wide text-muted-foreground">Workspace</div>
        {NAV_ITEMS.map((item) => {
          const active = pathname?.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "mb-0.5 flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13.5px] font-medium",
                active ? "bg-accent text-accent-foreground font-semibold" : "text-neutral-600 hover:bg-muted dark:text-neutral-400"
              )}
            >
              <item.icon className="size-4" strokeWidth={1.8} />
              <span className="flex-1">{item.label}</span>
              {item.badge && (
                <span
                  className={cn(
                    "rounded-full px-1.5 text-[11px] font-semibold leading-5",
                    item.badgeTone === "warn" ? "bg-amber-500 text-white" : "bg-muted text-muted-foreground"
                  )}
                >
                  {item.badge}
                </span>
              )}
            </Link>
          );
        })}

        <div className="px-2 pb-2 pt-4 text-[10.5px] font-semibold uppercase tracking-wide text-muted-foreground">Insights</div>
        <div className="mb-0.5 flex cursor-not-allowed items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13.5px] font-medium text-neutral-300 dark:text-neutral-700">
          <BarChart3 className="size-4" strokeWidth={1.8} />
          <span className="flex-1">Analytics</span>
          <span className="rounded-full bg-muted px-1.5 text-[10px] font-semibold text-muted-foreground">Soon</span>
        </div>
        <div className="flex cursor-not-allowed items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13.5px] font-medium text-neutral-300 dark:text-neutral-700">
          <Users className="size-4" strokeWidth={1.8} />
          <span className="flex-1">Accounts</span>
        </div>
      </nav>

      <div className="flex items-center gap-2.5 border-t border-border p-3">
        <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary text-[12px] font-semibold text-primary-foreground">
          MC
        </div>
        <div className="min-w-0 flex-1">
          <div className="text-[12.5px] font-semibold">Maya Chen</div>
          <div className="text-[11px] text-muted-foreground">Admin</div>
        </div>
        <button
          onClick={() => router.push("/login")}
          title="Sign out"
          className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground"
        >
          <LogOut className="size-4" strokeWidth={1.9} />
        </button>
      </div>
    </aside>
  );
}

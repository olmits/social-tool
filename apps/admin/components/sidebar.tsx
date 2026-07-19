"use client";

import {
  BarChart3,
  CalendarDays,
  Check,
  ChevronsUpDown,
  ListChecks,
  Loader2,
  LogOut,
  Plus,
  Radar,
  TriangleAlert,
  Unlink,
  Users,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { useAccountState } from "@/context/account-context";
import { useAccountActions } from "@/lib/actions/useAccountActions";
import { disconnectAccountAction } from "@/lib/api/actions";
import { accountLabel, platformLabel } from "@/lib/api/mappers";
import { PLATFORM_META } from "@/lib/mock-data";
import type { Platform } from "@/lib/types";
import { cn } from "@/lib/utils";

const NAV_ITEMS = [
  {
    href: "/radar",
    label: "Trend Radar",
    icon: Radar,
    badge: "24",
    badgeTone: "warn" as const,
  },
  {
    href: "/review",
    label: "Review & Approve",
    icon: ListChecks,
    badge: "6",
    badgeTone: "neutral" as const,
  },
  { href: "/schedule", label: "Scheduling", icon: CalendarDays },
];

// Reddit is single-account only (see CLAUDE.md); flagged in the switcher.
const PLATFORM_ORDER: Platform[] = ["BLUESKY", "MASTODON", "REDDIT"];

export function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const { accounts, selectedId } = useAccountState();
  const { selectAccount } = useAccountActions();
  const [menuOpen, setMenuOpen] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const selected = accounts.find((a) => a.id === selectedId) ?? null;
  const selectedMeta = selected ? PLATFORM_META[selected.platform] : null;

  const grouped = PLATFORM_ORDER.map((platform) => ({
    platform,
    accounts: accounts.filter((a) => a.platform === platform),
  })).filter((group) => group.accounts.length > 0);

  function handleDisconnect(id: string) {
    setActionError(null);
    startTransition(async () => {
      const result = await disconnectAccountAction(id);
      if (!result.ok) {
        setActionError(result.message);
      }
    });
  }

  return (
    <aside className="hidden h-full w-[258px] shrink-0 flex-col border-r border-border bg-muted/40 md:flex">
      <div className="flex items-center gap-2.5 px-4 pb-3 pt-4">
        <div className="flex size-7 items-center justify-center rounded-lg bg-primary">
          <div className="size-2.5 rounded-full border-2 border-primary-foreground" />
        </div>
        <span className="text-[15px] font-semibold tracking-tight">
          Cadence
        </span>
      </div>

      <div className="relative px-3 pb-3">
        <button
          type="button"
          onClick={() => setMenuOpen((v) => !v)}
          className="flex w-full items-center gap-2.5 rounded-lg border border-border bg-background px-2.5 py-2 text-left hover:border-neutral-400/70 dark:hover:border-neutral-600"
        >
          <span
            className={cn(
              "flex size-7 shrink-0 items-center justify-center rounded-md",
              selectedMeta?.badgeBg ?? "bg-muted",
            )}
          >
            <span
              className="size-2 rounded-full"
              style={{ background: selectedMeta?.dot ?? "#a3a3a3" }}
            />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-[12.5px] font-semibold">
              {selected ? accountLabel(selected) : "No account"}
            </span>
            <span className="block text-[11px] text-muted-foreground">
              {selected
                ? platformLabel(selected.platform)
                : "Connect one to begin"}
            </span>
          </span>
          <ChevronsUpDown className="size-3.5 text-neutral-400" />
        </button>

        {menuOpen && (
          <div className="absolute left-3 right-3 top-[52px] z-40 max-h-[60vh] overflow-auto rounded-xl border border-border bg-popover p-1.5 shadow-lg">
            {grouped.length === 0 && (
              <div className="px-2 py-3 text-center text-[12px] text-muted-foreground">
                No accounts connected yet.
              </div>
            )}

            {grouped.map(({ platform, accounts: platformAccounts }) => {
              const meta = PLATFORM_META[platform];
              return (
                <div key={platform}>
                  <div className="flex items-center gap-1.5 px-2 pb-1 pt-2 text-[10.5px] font-semibold uppercase tracking-wide text-muted-foreground">
                    <span
                      className="size-1.5 rounded-full"
                      style={{ background: meta.dot }}
                    />
                    {platformLabel(platform)}
                    {platform === "REDDIT" && (
                      <span className="font-medium normal-case tracking-normal text-neutral-300 dark:text-neutral-600">
                        · single
                      </span>
                    )}
                  </div>
                  {platformAccounts.map((account) => {
                    const active = account.id === selectedId;
                    return (
                      <div
                        key={account.id}
                        className={cn(
                          "group mb-0.5 flex items-center rounded-md pr-1 hover:bg-muted",
                          active && "bg-muted",
                        )}
                      >
                        <button
                          type="button"
                          onClick={() => {
                            selectAccount(account.id);
                            setMenuOpen(false);
                          }}
                          className="flex min-w-0 flex-1 items-center gap-2 px-2 py-1.5 text-left text-[12.5px] font-medium"
                        >
                          <span className="min-w-0 flex-1 truncate">
                            {accountLabel(account)}
                          </span>
                          {account.status === "DISCONNECTED" && (
                            <span className="shrink-0 text-[10px] text-muted-foreground">
                              disconnected
                            </span>
                          )}
                          {active && <Check className="size-3.5 shrink-0" />}
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDisconnect(account.id)}
                          disabled={isPending}
                          title="Disconnect account"
                          className="flex size-6 shrink-0 items-center justify-center rounded text-muted-foreground opacity-0 hover:bg-background hover:text-red-600 focus-visible:opacity-100 group-hover:opacity-100 disabled:opacity-50"
                        >
                          {isPending ? (
                            <Loader2 className="size-3.5 animate-spin" />
                          ) : (
                            <Unlink className="size-3.5" />
                          )}
                        </button>
                      </div>
                    );
                  })}
                </div>
              );
            })}

            {actionError && (
              <div className="mx-1 mt-1 flex items-start gap-1.5 rounded-md bg-red-50 px-2 py-1.5 text-[11px] text-red-700 dark:bg-red-950/40 dark:text-red-400">
                <TriangleAlert className="mt-px size-3 shrink-0" />
                <span>{actionError}</span>
              </div>
            )}

            <div className="my-1 border-t border-border" />
            <Link
              href="/accounts/connect"
              onClick={() => setMenuOpen(false)}
              className="flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-[12.5px] font-medium hover:bg-muted"
            >
              <Plus className="size-3.5" />
              Connect account…
            </Link>
          </div>
        )}
      </div>

      <nav className="flex-1 overflow-auto px-3">
        <div className="px-2 pb-2 pt-1.5 text-[10.5px] font-semibold uppercase tracking-wide text-muted-foreground">
          Workspace
        </div>
        {NAV_ITEMS.map((item) => {
          const active = pathname?.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "mb-0.5 flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13.5px] font-medium",
                active
                  ? "bg-accent text-accent-foreground font-semibold"
                  : "text-neutral-600 hover:bg-muted dark:text-neutral-400",
              )}
            >
              <item.icon className="size-4" strokeWidth={1.8} />
              <span className="flex-1">{item.label}</span>
              {item.badge && (
                <span
                  className={cn(
                    "rounded-full px-1.5 text-[11px] font-semibold leading-5",
                    item.badgeTone === "warn"
                      ? "bg-amber-500 text-white"
                      : "bg-muted text-muted-foreground",
                  )}
                >
                  {item.badge}
                </span>
              )}
            </Link>
          );
        })}

        <div className="px-2 pb-2 pt-4 text-[10.5px] font-semibold uppercase tracking-wide text-muted-foreground">
          Insights
        </div>
        <div className="mb-0.5 flex cursor-not-allowed items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13.5px] font-medium text-neutral-300 dark:text-neutral-700">
          <BarChart3 className="size-4" strokeWidth={1.8} />
          <span className="flex-1">Analytics</span>
          <span className="rounded-full bg-muted px-1.5 text-[10px] font-semibold text-muted-foreground">
            Soon
          </span>
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
          type="button"
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

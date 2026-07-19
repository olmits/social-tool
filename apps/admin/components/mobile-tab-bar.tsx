"use client";

import { BarChart3, CalendarDays, ListChecks, Radar } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

const TAB_ITEMS = [
  { href: "/radar", label: "Radar", icon: Radar },
  { href: "/review", label: "Review", icon: ListChecks },
  { href: "/schedule", label: "Schedule", icon: CalendarDays },
];

export function MobileTabBar() {
  const pathname = usePathname();

  return (
    <nav className="fixed inset-x-0 bottom-0 z-30 flex items-start justify-around border-t border-border bg-background/95 pb-6 pt-2 backdrop-blur-sm md:hidden">
      {TAB_ITEMS.map((item) => {
        const active = pathname?.startsWith(item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            className={cn(
              "flex w-16 flex-col items-center gap-1 pt-1.5 text-neutral-400 dark:text-neutral-600",
              active && "text-foreground",
            )}
          >
            <item.icon className="size-[23px]" strokeWidth={active ? 2 : 1.8} />
            <span
              className={cn(
                "text-[10px]",
                active ? "font-semibold" : "font-medium",
              )}
            >
              {item.label}
            </span>
          </Link>
        );
      })}
      <div className="flex w-16 flex-col items-center gap-1 pt-1.5 text-neutral-300 dark:text-neutral-700">
        <BarChart3 className="size-[23px]" strokeWidth={1.8} />
        <span className="text-[10px] font-medium">Analytics</span>
      </div>
    </nav>
  );
}

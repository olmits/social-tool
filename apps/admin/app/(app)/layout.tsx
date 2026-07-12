import type { ReactNode } from "react";
import { AccountProvider } from "@/context/account-context";
import { Sidebar } from "@/components/sidebar";
import { MobileTabBar } from "@/components/mobile-tab-bar";

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <AccountProvider>
      <div className="flex h-screen w-full overflow-hidden">
        <Sidebar />
        <main className="flex min-w-0 flex-1 flex-col overflow-auto pb-16 md:pb-0">{children}</main>
      </div>
      <MobileTabBar />
    </AccountProvider>
  );
}

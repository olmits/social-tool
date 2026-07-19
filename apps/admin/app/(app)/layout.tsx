import type { ReactNode } from "react";
import { MobileTabBar } from "@/components/mobile-tab-bar";
import { Sidebar } from "@/components/sidebar";
import { AccountProvider } from "@/context/account-context";

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <AccountProvider>
      <div className="flex h-screen w-full overflow-hidden">
        <Sidebar />
        <main className="flex min-w-0 flex-1 flex-col overflow-auto pb-16 md:pb-0">
          {children}
        </main>
      </div>
      <MobileTabBar />
    </AccountProvider>
  );
}

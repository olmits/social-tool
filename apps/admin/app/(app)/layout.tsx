import type { ReactNode } from "react";
import { MobileTabBar } from "@/components/mobile-tab-bar";
import { Sidebar } from "@/components/sidebar";
import { ToastProvider } from "@/components/ui/toast";
import { AccountProvider } from "@/context/account-context";
import { listAccounts } from "@/lib/api/accounts";
import { toUiAccount } from "@/lib/api/mappers";

// Account data is mutable and per-request — render on demand rather than baking a
// snapshot at build time. Reads stay tag-cached ("accounts") so `updateTag` in the
// mutation actions gives read-your-own-writes after connect/disconnect.
export const dynamic = "force-dynamic";

// The account selector's data enters here: fetch on the server (BFF attaches the
// API key), map to UI shape, and seed the client AccountProvider. No try/catch —
// if the core API is unreachable this throws into `error.tsx`.
export default async function AppLayout({
  children,
  modal,
}: {
  children: ReactNode;
  /** Parallel-route slot for the intercepted connect modal (see @modal). */
  modal: ReactNode;
}) {
  const dtos = await listAccounts();
  const accounts = dtos.map(toUiAccount);

  return (
    <AccountProvider initialAccounts={accounts}>
      <ToastProvider>
        <div className="flex h-screen w-full overflow-hidden">
          <Sidebar />
          <main className="flex min-w-0 flex-1 flex-col overflow-auto pb-16 md:pb-0">
            {children}
          </main>
        </div>
        <MobileTabBar />
        {modal}
      </ToastProvider>
    </AccountProvider>
  );
}

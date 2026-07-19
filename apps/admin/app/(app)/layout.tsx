import type { ReactNode } from "react";
import { MobileTabBar } from "@/components/mobile-tab-bar";
import { Sidebar } from "@/components/sidebar";
import { AccountProvider } from "@/context/account-context";
import { listAccounts } from "@/lib/api/accounts";
import { ApiError } from "@/lib/api/client";
import { toUiAccount, type UiAccount } from "@/lib/api/mappers";

// Account data is mutable and per-request — render on demand rather than baking a
// snapshot at build time. Reads stay tag-cached ("accounts") so `updateTag` in the
// mutation actions gives read-your-own-writes after connect/disconnect.
export const dynamic = "force-dynamic";

// The account selector's data enters here: fetch on the server (BFF attaches the
// API key), map to UI shape, and seed the client AccountProvider. mock → real.
export default async function AppLayout({ children }: { children: ReactNode }) {
  let accounts: UiAccount[] = [];
  let error: string | null = null;

  try {
    const dtos = await listAccounts();
    accounts = dtos.map(toUiAccount);
  } catch (err) {
    error =
      err instanceof ApiError
        ? err.message
        : "Could not reach the API. Is the core service running?";
    console.error("Failed to load accounts:", err);
  }

  return (
    <AccountProvider initialAccounts={accounts} initialError={error}>
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

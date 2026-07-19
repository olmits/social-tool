import { ConnectStandalone } from "@/components/connect-account/connect-standalone";

// Full-page target for /accounts/connect (refresh / deep link / no-JS). On soft
// navigation the @modal slot intercepts this route and renders it as a dialog.
export default function ConnectAccountPage() {
  return <ConnectStandalone />;
}

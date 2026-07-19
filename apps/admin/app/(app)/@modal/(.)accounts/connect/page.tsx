import { ConnectModal } from "@/components/connect-account/ConnectModal";

// Intercepts /accounts/connect on soft navigation and renders it as a modal over
// the current page. `(.)` matches the `accounts` segment at the (app) level since
// `@modal` is a slot, not a route segment.
export default function InterceptedConnectPage() {
  return <ConnectModal />;
}

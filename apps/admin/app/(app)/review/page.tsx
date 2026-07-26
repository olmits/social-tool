import { Suspense } from "react";
import { ReviewBoard } from "@/components/review/ReviewBoard";
import { listDrafts } from "@/lib/api/drafts";

// Drafts are mutable per-request; render on demand. Reads are tag-cached
// ("drafts") so approve/schedule actions give read-your-own-writes via updateTag.
export const dynamic = "force-dynamic";

// Fetch every draft on the server (BFF attaches the API key); the board scopes
// to the selected account client-side, since selection lives in AccountProvider.
// No try/catch — an unreachable core API throws into error.tsx.
export default async function ReviewPage() {
  const drafts = await listDrafts();
  return (
    <Suspense>
      <ReviewBoard drafts={drafts} />
    </Suspense>
  );
}

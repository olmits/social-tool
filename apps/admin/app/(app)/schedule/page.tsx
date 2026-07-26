import { ScheduleCalendar } from "@/components/schedule/ScheduleCalendar";
import { listDrafts } from "@/lib/api/drafts";

// Scheduled drafts are mutable per-request; render on demand. Reads are
// tag-cached ("drafts") so scheduling from the review page reflects here.
export const dynamic = "force-dynamic";

// Every SCHEDULED draft, across accounts — the calendar is a queue overview. The
// client component groups them into the visible week. No try/catch — an
// unreachable core API throws into error.tsx.
export default async function SchedulePage() {
  const drafts = await listDrafts(undefined, "SCHEDULED");
  return <ScheduleCalendar drafts={drafts} />;
}

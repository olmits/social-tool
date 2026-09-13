import { TopicsView } from "@/components/topics/TopicsView";
import { listTopics } from "@/lib/api/topics";

// Topics are mutable per-request; render on demand. Reads are tag-cached
// ("topics") so the create/edit/toggle/delete actions give read-your-own-writes
// via updateTag.
export const dynamic = "force-dynamic";

// No try/catch — an unreachable core API throws into error.tsx, same as review.
export default async function TopicsPage() {
  const topics = await listTopics();
  return <TopicsView topics={topics} />;
}

import { VoiceView } from "@/components/voice/VoiceView";
import { listVoiceProfiles } from "@/lib/api/voiceProfiles";

// Profiles are mutable per-request; render on demand. Reads are tag-cached
// ("voice-profiles") so create/edit/toggle/delete give read-your-own-writes via
// updateTag.
export const dynamic = "force-dynamic";

// No try/catch — an unreachable core API throws into error.tsx, same as topics.
export default async function VoicePage() {
  const profiles = await listVoiceProfiles();
  return <VoiceView profiles={profiles} />;
}

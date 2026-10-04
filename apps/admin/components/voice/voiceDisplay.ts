// Voice-form copy and limits. Kept out of the form hook so the hook is logic
// only, and out of lib/api/mappers.ts because this is authoring guidance, not a
// mapping of anything the API returns.

/** Backend limits (VoiceProfile.MAX_NAME_LENGTH / MAX_INSTRUCTIONS_LENGTH). */
export const VOICE_NAME_MAX = 120;
export const VOICE_INSTRUCTIONS_MAX = 4000;

/**
 * What to actually write in the instructions box. The field reaches the drafting
 * system prompt verbatim, so the most useful thing a reader can know is that
 * pasting their own posts in works — that is a few-shot example set, and it beats
 * any amount of describing a tone in the abstract.
 */
export const INSTRUCTIONS_PLACEHOLDER = `Dry and concrete. First person, short sentences, no hype.
Never use emoji or hashtags. Lead with the specific thing that changed.

A couple of my own posts, for reference:
…`;

export const INSTRUCTIONS_HINT =
  "Goes into the prompt word for word. Pasting two or three of your own posts here works better than describing the tone.";

# `generation/` — turning a signal into a draft

The slice that closes the radar loop. Everything upstream of it answers "what is worth writing
about"; this is the part that writes something.

**This is a capability, not an aggregate.** It owns no entity and no table — it reads four
slices, calls one external API, and writes through `draft/`. It sits beside `draft/` rather
than inside it so the draft state machine never imports the Anthropic SDK, and so the whole
feature can be removed by deleting this directory. The dependency runs one way:
`generation/ → draft/`, never back.

```
POST /drafts/generate
  { accountId, signalId, platform, voiceProfileId? }
      │
      ├─ DraftCreationService.requireDraftable   ← account gate, BEFORE spending anything
      ├─ SignalService.get                       ← title, url, source, topicId
      ├─ TopicService.namesByIds                 ← the topic chip's name, if any
      ├─ VoiceProfileService                     ← named profile, or the default
      │
      ├─ PromptFactory.build             ← pure; system rules + voice, then the signal
      ├─ DraftGenerator.generate                 ← the only thing that costs money
      │     └─ over the platform limit? one corrective attempt, then keep what you get
      │
      └─ DraftCreationService.createGenerated    ← aiGenerated = true, linked to the signal
             → 201 DraftResponse (status = DRAFT)
```

## Why it is shaped like this

**`DraftGenerator` is an interface with one implementation.** Not ceremony: it is the seam
that `@MockitoBean` replaces, and without it `GenerationControllerIntegrationTest` would call
the real, paid API on every run. The same arrangement `PlatformAdapter` gives the publisher.

**`claude/` is the only package that knows the SDK exists.** `GeneratedPost` lives there and
never leaves, because its `@JsonPropertyDescription` annotations are Jackson 2 — what the SDK
derives JSON schemas with — while the rest of this service serializes with Jackson 3. Both are
on the classpath and both work; a Jackson 3 serializer would simply ignore those annotations.
`GeneratedDraft` is the slice's own outward shape.

**`GenerationService` is not `@Transactional`.** Every other service here is. A generation round
trip takes tens of seconds and the pool holds ten connections, so a transaction around this
would park one on a network call and exhaust the pool under the mildest concurrency. The reads
are independent and the single write is atomic on its own.

**The account gate runs first.** `requireDraftable` exists only so that a disconnected account
costs milliseconds instead of a paid round trip followed by a 409.

**Nothing is persisted unless generation succeeds.** The draft row is built from the result, so
a failure or a refusal leaves nothing to clean up and the author just clicks again.

## Character limits

`Platform.maxPostLength()` is the hard limit; the prompt asks for 93% of it. The headroom is
what keeps the corrective attempt rare — a model told "at most 300" lands just over it often
enough to matter, while one told "at most 279" overshoots into the margin.

Lengths are measured in **graphemes**, never `String.length()` (`PostLength`). An emoji is two
UTF-16 units and one grapheme; at a 300 limit that difference decides whether an approved draft
publishes or bounces at the Go worker.

If the corrective attempt is *also* over, the draft is kept anyway. Review is mandatory before
approval, the panel already renders an over-limit counter, and truncating a 300-character post
destroys its ending — which is the part the post was written for.

## Web fetch

On by default (`social-api.claude.fetch-article`). Drafting from a headline alone produces
"interesting read on X", which the author can write faster themselves; the value of the feature
is commentary on what the article actually says.

What that costs: the article's tokens, several seconds of latency, and the chance of a
`pause_turn` stop reason. A fetch failure is **not** an exception — the API answers 200 with an
error object inside the result block — so a paywalled or script-rendered page degrades to
drafting from the headline and logs a warning rather than failing the request.

The system prompt tells the model to treat any fetched page as untrusted data. An arbitrary
link from Hacker News is attacker-adjacent content; the mandatory human review gate is the real
mitigation and that line is the cheap one.

## Status codes

| Condition | Status |
|---|---|
| Unknown signal, voice profile, or account | 404 |
| Account disconnected, platform mismatch, voice profile disabled, no voice profile at all | 409 |
| The model declined (`stop_reason: refusal`) | 422 |
| API error, timeout, no API key, nothing usable returned | 502 |

A refusal is deliberately not a 502: nothing upstream failed, the request succeeded with a 200
and the model said no. That puts it next to `DisclosureRequiredException` — understood,
well-formed, and still not processable.

## Deferred

- **`pause_turn` is not resumed.** With one permitted fetch it is close to unreachable, and
  resuming properly means replaying the assistant turn, which cannot be tested here without a
  recorded fixture. It fails loudly instead of returning a half-written post.
- **`rationale` is not persisted.** The model returns one sentence on the angle it took; it is
  logged at DEBUG and dropped. A `drafts.generation_rationale` column is the upgrade.
- **No record of which voice wrote a draft.** Useful provenance, deliberately out of the first
  slice's scope.
- **`signal.rawPayload` is neither parsed nor sent.** Raw it is kilobytes of noise that invite
  quoting an irrelevant field; parsed it needs a per-source switch for marginal gain now that
  web fetch is on. dev.to's `description` is the one field worth adding first.

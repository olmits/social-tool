/**
 * The topic a signal was collected for. Renders nothing when there isn't one —
 * a null `topicId` means the signal predates topics, or its topic was deleted,
 * and an empty chip would imply a topic named "".
 */
export function TopicChip({ name }: { name: string | null }) {
  if (!name) return null;
  return (
    <span className="rounded-md bg-muted px-2.5 py-0.5 text-[11.5px] font-medium text-muted-foreground">
      {name}
    </span>
  );
}

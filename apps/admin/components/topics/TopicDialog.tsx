"use client";

import { Loader2 } from "lucide-react";
import { ControlledTextField } from "@/components/form/ControlledTextField";
import { FormError } from "@/components/form/FormError";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogBackdrop,
  DialogDescription,
  DialogPopup,
  DialogPortal,
  DialogTitle,
} from "@/components/ui/dialog";
import { SIGNAL_SOURCES, sourceLabel } from "@/lib/api/mappers";
import type { TopicResponse } from "@/lib/api/types";
import { SOURCE_QUERY_PLACEHOLDERS } from "./topicDisplay";
import { useTopicForm } from "./useTopicForm";

/**
 * Create/edit a topic. Only the polled sources get a query field — Reddit and
 * Product Hunt are in the enum but have no worker implementation, so a query for
 * them would never run (see `SIGNAL_SOURCES`).
 *
 * Mount this only while open, keyed by topic, so the form picks up fresh defaults
 * instead of holding the previous topic's values.
 */
export function TopicDialog({
  topic,
  onClose,
}: {
  /** The topic being edited, or null to create one. */
  topic: TopicResponse | null;
  onClose: () => void;
}) {
  const { control, rules, onSubmit, isPending, rootError } = useTopicForm(
    topic,
    onClose,
  );

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogPortal>
        <DialogBackdrop />
        <DialogPopup>
          <div className="mb-4 flex flex-col gap-1">
            <DialogTitle>{topic ? "Edit topic" : "New topic"}</DialogTitle>
            <DialogDescription>
              A topic is what you write about. Give each source a search query
              and the radar polls it on every run.
            </DialogDescription>
          </div>

          <form onSubmit={onSubmit} className="flex flex-col gap-4">
            <ControlledTextField
              control={control}
              name="name"
              label="Name"
              placeholder="Local-first"
              rules={rules.name}
            />

            <div className="flex flex-col gap-3">
              <span className="text-[12.5px] font-medium">
                Queries
                <span className="ml-1.5 font-normal text-muted-foreground">
                  leave a source blank to skip it
                </span>
              </span>
              {SIGNAL_SOURCES.map((source) => (
                <ControlledTextField
                  key={source}
                  control={control}
                  name={`queries.${source}`}
                  label={sourceLabel(source)}
                  placeholder={SOURCE_QUERY_PLACEHOLDERS[source]}
                  rules={rules.query}
                />
              ))}
            </div>

            <FormError message={rootError} />

            <div className="mt-1 flex justify-end gap-2">
              <Button
                type="button"
                variant="ghost"
                onClick={onClose}
                disabled={isPending}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isPending}>
                {isPending && <Loader2 className="size-4 animate-spin" />}
                {topic ? "Save topic" : "Create topic"}
              </Button>
            </div>
          </form>
        </DialogPopup>
      </DialogPortal>
    </Dialog>
  );
}

"use client";

import { useTransition } from "react";
import { type Control, type RegisterOptions, useForm } from "react-hook-form";
import { toastManager } from "@/components/ui/toast";
import { createTopicAction, updateTopicAction } from "@/lib/api/actions";
import type {
  SignalSource,
  TopicQueries,
  TopicResponse,
} from "@/lib/api/types";
import { TOPIC_NAME_MAX, TOPIC_QUERY_MAX } from "./topicDisplay";

export interface TopicFormValues {
  name: string;
  /**
   * Keyed by every source, not just the polled ones. The form renders only the
   * polled set, but a topic may carry a query for a source the worker doesn't run
   * yet — and `queries` replaces wholesale on save, so carrying the unrendered
   * ones through form state is what keeps the save from quietly dropping them.
   */
  queries: Record<SignalSource, string>;
}

interface TopicFieldRules {
  name: RegisterOptions<TopicFormValues, "name">;
  query: RegisterOptions<TopicFormValues, `queries.${SignalSource}`>;
}

export interface UseTopicForm {
  control: Control<TopicFormValues>;
  rules: TopicFieldRules;
  onSubmit: (event?: React.BaseSyntheticEvent) => void;
  isPending: boolean;
  /** Server-side failure (409 duplicate name, 400 invalid), if any. */
  rootError?: string;
}

const EMPTY_QUERIES: Record<SignalSource, string> = {
  HACKER_NEWS: "",
  DEVTO: "",
  GITHUB_TRENDING: "",
  REDDIT: "",
  PRODUCT_HUNT: "",
};

/** Blank query fields are omitted, not sent: the API rejects a blank query outright. */
const toCommandQueries = (
  queries: Record<SignalSource, string>,
): TopicQueries =>
  Object.fromEntries(
    Object.entries(queries)
      .map(([source, query]) => [source, query.trim()])
      .filter(([, query]) => query.length > 0),
  );

/**
 * Create/edit topic form: field setup, validation mirroring the API's own rules,
 * and the submit that calls the right action for the mode. Pass the topic being
 * edited, or null to create.
 */
export function useTopicForm(
  topic: TopicResponse | null,
  onClose: () => void,
): UseTopicForm {
  const [isPending, startTransition] = useTransition();

  const {
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<TopicFormValues>({
    mode: "onBlur",
    defaultValues: {
      name: topic?.name ?? "",
      queries: { ...EMPTY_QUERIES, ...topic?.queries },
    },
  });

  const onSubmit = handleSubmit((values) => {
    const name = values.name.trim();
    const queries = toCommandQueries(values.queries);

    startTransition(async () => {
      // Create sends `enabled: null`, which the API reads as enabled — a topic is
      // created to be polled. Edit leaves `enabled` out: the list's toggle owns it,
      // and a sparse patch is what keeps this form from reverting it.
      const result = topic
        ? await updateTopicAction(topic.id, { name, queries })
        : await createTopicAction({ name, enabled: null, queries });

      if (!result.ok) {
        setError("root", { message: result.message });
        return;
      }
      toastManager.add({
        title: topic ? "Topic saved" : "Topic created",
        description: result.data.name,
      });
      onClose();
    });
  });

  const rules: TopicFieldRules = {
    name: {
      required: "Name is required",
      maxLength: {
        value: TOPIC_NAME_MAX,
        message: `Name must be at most ${TOPIC_NAME_MAX} characters`,
      },
    },
    query: {
      maxLength: {
        value: TOPIC_QUERY_MAX,
        message: `Query must be at most ${TOPIC_QUERY_MAX} characters`,
      },
    },
  };

  return {
    control,
    rules,
    onSubmit,
    isPending,
    rootError: errors.root?.message,
  };
}

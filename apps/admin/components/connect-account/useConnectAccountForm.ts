"use client";

import { useTransition } from "react";
import { type Control, type RegisterOptions, useForm } from "react-hook-form";
import { toastManager } from "@/components/ui/toast";
import { useAccountState } from "@/context/account-context";
import { useAccountActions } from "@/lib/actions/useAccountActions";
import { connectAccountAction } from "@/lib/api/actions";
import { accountLabel, platformLabel } from "@/lib/api/mappers";
import {
  INSTANCE_PATTERN,
  PLATFORM_CONNECT_FIELDS,
  PLATFORM_CONNECT_ORDER,
  type PlatformConnectFields,
} from "@/lib/platform-connect";
import type { Platform } from "@/lib/types";

export interface ConnectFormValues {
  platform: Platform;
  handle: string;
  credentialValue: string;
  instance: string;
}

/** Validation rules keyed by field — mirror the API contract (see AccountService). */
interface ConnectFieldRules {
  handle: RegisterOptions<ConnectFormValues, "handle">;
  credentialValue: RegisterOptions<ConnectFormValues, "credentialValue">;
  instance: RegisterOptions<ConnectFormValues, "instance">;
}

export interface UseConnectAccountForm {
  control: Control<ConnectFormValues>;
  /** Currently selected platform (drives which fields render). */
  platform: Platform;
  /** Field copy for the selected platform. */
  fields: PlatformConnectFields;
  redditTaken: boolean;
  isPlatformDisabled: (platform: Platform) => boolean;
  selectPlatform: (next: Platform) => void;
  rules: ConnectFieldRules;
  onSubmit: (event?: React.BaseSyntheticEvent) => void;
  isPending: boolean;
  /** Server-side failure message (409 duplicate, Reddit limit, 400), if any. */
  rootError?: string;
}

/**
 * All connect-form logic: form setup, platform selection (with its side effects),
 * validation rules, and the submit handler that talks to the API, optimistically
 * selects the new account, toasts, and closes. The component stays presentational.
 */
export function useConnectAccountForm(
  onClose: () => void,
): UseConnectAccountForm {
  const { accounts } = useAccountState();
  const { addAccount } = useAccountActions();
  const [isPending, startTransition] = useTransition();

  const redditTaken = accounts.some(
    (a) => a.platform === "REDDIT" && a.status === "ACTIVE",
  );
  const isPlatformDisabled = (platform: Platform) =>
    PLATFORM_CONNECT_FIELDS[platform].singleAccount && redditTaken;
  const firstAvailable =
    PLATFORM_CONNECT_ORDER.find((p) => !isPlatformDisabled(p)) ?? "BLUESKY";

  const {
    control,
    handleSubmit,
    watch,
    setValue,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<ConnectFormValues>({
    mode: "onBlur",
    defaultValues: {
      platform: firstAvailable,
      handle: "",
      credentialValue: "",
      instance: "",
    },
  });

  const platform = watch("platform");
  const fields = PLATFORM_CONNECT_FIELDS[platform];

  const selectPlatform = (next: Platform) => {
    if (isPlatformDisabled(next)) return;
    setValue("platform", next);
    // Instance only applies to Mastodon; clear it (and any error) when leaving.
    if (!PLATFORM_CONNECT_FIELDS[next].needsInstance) {
      setValue("instance", "");
      clearErrors("instance");
    }
    clearErrors("root");
  };

  const onSubmit = handleSubmit((values) => {
    const needsInstance =
      PLATFORM_CONNECT_FIELDS[values.platform].needsInstance;
    startTransition(async () => {
      const result = await connectAccountAction({
        platform: values.platform,
        handle: values.handle.trim(),
        credentialValue: values.credentialValue,
        instance: needsInstance ? values.instance.trim() : null,
      });
      if (!result.ok) {
        setError("root", { message: result.message });
        return;
      }
      addAccount(result.data);
      toastManager.add({
        title: "Account connected",
        description: `${accountLabel(result.data)} · ${platformLabel(result.data.platform)}`,
      });
      onClose();
    });
  });

  const rules: ConnectFieldRules = {
    handle: { required: `${fields.handleLabel} is required` },
    credentialValue: { required: `${fields.credentialLabel} is required` },
    instance: {
      validate: (value, formValues) => {
        if (!PLATFORM_CONNECT_FIELDS[formValues.platform].needsInstance)
          return true;
        const v = value.trim();
        if (!v) return "Instance is required for Mastodon";
        return (
          INSTANCE_PATTERN.test(v) ||
          "Enter a valid instance host, e.g. fosstodon.org"
        );
      },
    },
  };

  return {
    control,
    platform,
    fields,
    redditTaken,
    isPlatformDisabled,
    selectPlatform,
    rules,
    onSubmit,
    isPending,
    rootError: errors.root?.message,
  };
}

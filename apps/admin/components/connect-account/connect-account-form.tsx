"use client";

import { Eye, EyeOff, Loader2, TriangleAlert } from "lucide-react";
import { useState, useTransition } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { toastManager } from "@/components/ui/toast";
import { useAccountState } from "@/context/account-context";
import { useAccountActions } from "@/lib/actions/useAccountActions";
import { connectAccountAction } from "@/lib/api/actions";
import { accountLabel, platformLabel } from "@/lib/api/mappers";
import { PLATFORM_META } from "@/lib/mock-data";
import {
  INSTANCE_PATTERN,
  PLATFORM_CONNECT_FIELDS,
  PLATFORM_CONNECT_ORDER,
} from "@/lib/platform-connect";
import type { Platform } from "@/lib/types";
import { cn } from "@/lib/utils";

interface FormValues {
  platform: Platform;
  handle: string;
  credentialValue: string;
  instance: string;
}

/**
 * Connect-account form. Shared by the intercepted modal and the standalone
 * `/accounts/connect` page. react-hook-form owns validation (mirroring the API
 * contract); server-side failures (409 duplicate, Reddit limit, 400) surface as
 * a root error. On success it optimistically selects the new account and fires a
 * toast, then calls `onSuccess` to let the caller close/navigate.
 */
export function ConnectAccountForm({ onClose }: { onClose: () => void }) {
  const { accounts } = useAccountState();
  const { addAccount } = useAccountActions();
  const [isPending, startTransition] = useTransition();
  const [showCredential, setShowCredential] = useState(false);

  const redditTaken = accounts.some(
    (a) => a.platform === "REDDIT" && a.status === "ACTIVE",
  );
  const isDisabled = (p: Platform) =>
    PLATFORM_CONNECT_FIELDS[p].singleAccount && redditTaken;
  const firstAvailable =
    PLATFORM_CONNECT_ORDER.find((p) => !isDisabled(p)) ?? "BLUESKY";

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError,
    clearErrors,
    getValues,
    formState: { errors },
  } = useForm<FormValues>({
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
  const rootError = errors.root?.message;

  function selectPlatform(next: Platform) {
    if (isDisabled(next)) return;
    setValue("platform", next);
    // Instance only applies to Mastodon; clear it (and any error) when leaving.
    if (!PLATFORM_CONNECT_FIELDS[next].needsInstance) {
      setValue("instance", "");
      clearErrors("instance");
    }
    clearErrors("root");
  }

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

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      {/* Platform picker */}
      <div className="flex flex-col gap-1.5">
        <span className="text-[12.5px] font-medium">Platform</span>
        <div className="grid grid-cols-3 gap-1.5">
          {PLATFORM_CONNECT_ORDER.map((p) => {
            const meta = PLATFORM_META[p];
            const active = p === platform;
            const disabled = isDisabled(p);
            return (
              <button
                key={p}
                type="button"
                onClick={() => selectPlatform(p)}
                disabled={disabled}
                title={
                  disabled ? "Single account — already connected" : undefined
                }
                className={cn(
                  "flex items-center justify-center gap-1.5 rounded-lg border px-2 py-2 text-[12.5px] font-medium transition-colors",
                  active
                    ? "border-neutral-400 bg-muted dark:border-neutral-600"
                    : "border-border hover:bg-muted",
                  disabled &&
                    "cursor-not-allowed opacity-40 hover:bg-transparent",
                )}
              >
                <span
                  className="size-2 rounded-full"
                  style={{ background: meta.dot }}
                />
                {PLATFORM_CONNECT_FIELDS[p].label}
              </button>
            );
          })}
        </div>
        {redditTaken && (
          <p className="text-[11px] text-muted-foreground">
            Reddit allows a single connected account.
          </p>
        )}
      </div>

      {/* Handle */}
      <Field
        htmlFor="handle"
        label={fields.handleLabel}
        error={errors.handle?.message}
      >
        <Input
          id="handle"
          autoComplete="off"
          autoCapitalize="none"
          spellCheck={false}
          placeholder={fields.handlePlaceholder}
          aria-invalid={errors.handle ? true : undefined}
          {...register("handle", {
            required: `${fields.handleLabel} is required`,
          })}
        />
      </Field>

      {/* Instance (Mastodon only) */}
      {fields.needsInstance && (
        <Field
          htmlFor="instance"
          label="Instance"
          error={errors.instance?.message}
        >
          <Input
            id="instance"
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            placeholder={fields.instancePlaceholder}
            aria-invalid={errors.instance ? true : undefined}
            {...register("instance", {
              validate: (value) => {
                if (
                  !PLATFORM_CONNECT_FIELDS[getValues("platform")].needsInstance
                )
                  return true;
                const v = value.trim();
                if (!v) return "Instance is required for Mastodon";
                return (
                  INSTANCE_PATTERN.test(v) ||
                  "Enter a valid instance host, e.g. fosstodon.org"
                );
              },
            })}
          />
        </Field>
      )}

      {/* Credential */}
      <Field
        htmlFor="credential"
        label={fields.credentialLabel}
        hint={fields.credentialHint}
        error={errors.credentialValue?.message}
      >
        <div className="relative">
          <Input
            id="credential"
            type={showCredential ? "text" : "password"}
            autoComplete="off"
            className="pr-9"
            placeholder={fields.credentialPlaceholder}
            aria-invalid={errors.credentialValue ? true : undefined}
            {...register("credentialValue", {
              required: `${fields.credentialLabel} is required`,
            })}
          />
          <button
            type="button"
            onClick={() => setShowCredential((v) => !v)}
            title={showCredential ? "Hide" : "Show"}
            className="absolute right-2 top-1/2 flex size-6 -translate-y-1/2 items-center justify-center rounded text-muted-foreground hover:text-foreground"
          >
            {showCredential ? (
              <EyeOff className="size-4" />
            ) : (
              <Eye className="size-4" />
            )}
          </button>
        </div>
      </Field>

      {rootError && (
        <div className="flex items-start gap-1.5 rounded-lg border border-red-200 bg-red-50 px-2.5 py-2 text-[12px] text-red-700 dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-400">
          <TriangleAlert className="mt-px size-3.5 shrink-0" />
          <span>{rootError}</span>
        </div>
      )}

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
          Connect account
        </Button>
      </div>
    </form>
  );
}

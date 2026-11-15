import type { HTMLAttributes } from "react";

import { cn } from "@/lib/utils";

export type BadgeTone = "neutral" | "verified" | "uncertain" | "rejected";

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
}

const badgeToneClasses: Record<BadgeTone, string> = {
  neutral: "border-border bg-secondary text-secondary-foreground",
  verified: "border-verified/30 bg-verified/10 text-verified",
  uncertain: "border-uncertain/30 bg-uncertain/10 text-uncertain",
  rejected: "border-rejected/30 bg-rejected/10 text-rejected",
};

export function Badge({ className, tone = "neutral", ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex min-h-7 items-center gap-1.5 rounded-md border px-2.5 py-1 text-xs font-semibold leading-none",
        badgeToneClasses[tone],
        className,
      )}
      {...props}
    />
  );
}

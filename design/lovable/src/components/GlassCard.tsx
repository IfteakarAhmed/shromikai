import type { HTMLAttributes } from "react";

import { cn } from "@/lib/utils";

interface GlassCardProps extends HTMLAttributes<HTMLElement> {
  as?: "article" | "aside" | "div" | "section";
  interactive?: boolean;
}

export function GlassCard({
  as: Element = "div",
  className,
  interactive = false,
  ...props
}: GlassCardProps) {
  return (
    <Element
      className={cn(
        "glass-panel relative overflow-hidden rounded-lg",
        interactive && "glass-interactive",
        className,
      )}
      {...props}
    />
  );
}

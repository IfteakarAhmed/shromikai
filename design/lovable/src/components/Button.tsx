import type { ButtonProps } from "@/components/ui/button";
import { Button as BaseButton } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function Button({ className, ...props }: ButtonProps) {
  return (
    <BaseButton
      className={cn("min-h-11 shadow-sm transition-all duration-300 ease-out", className)}
      {...props}
    />
  );
}

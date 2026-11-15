import { useEffect, useRef, type HTMLAttributes } from "react";

import { cn } from "@/lib/utils";

interface ScrollRevealProps extends HTMLAttributes<HTMLDivElement> {
  delay?: 0 | 1 | 2 | 3;
}

export function ScrollReveal({ children, className, delay = 0, ...props }: ScrollRevealProps) {
  const elementRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const element = elementRef.current;

    if (!element) return;

    if (typeof IntersectionObserver === "undefined") return;

    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    element.dataset["reveal"] = "pending";
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          delete element.dataset["reveal"];
          observer.disconnect();
        }
      },
      { threshold: 0.12, rootMargin: "0px 0px -36px 0px" },
    );

    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  return (
    <div
      ref={elementRef}
      className={cn(
        "motion-safe:transition-[opacity,transform] motion-safe:duration-500 motion-safe:ease-out",
        className,
      )}
      data-reveal-delay={delay}
      {...props}
    >
      {children}
    </div>
  );
}

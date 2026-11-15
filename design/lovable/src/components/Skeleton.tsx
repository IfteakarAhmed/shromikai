import { Skeleton as BaseSkeleton } from "@/components/ui/skeleton";

interface SkeletonProps {
  className?: string;
}

export function Skeleton({ className }: SkeletonProps) {
  return <BaseSkeleton aria-hidden="true" className={className} />;
}

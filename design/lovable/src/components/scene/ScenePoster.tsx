import poster from "@/assets/shromikai-contract-poster.webp";

interface ScenePosterProps {
  alt: string;
  className?: string;
}

export function ScenePoster({ alt, className }: ScenePosterProps) {
  return (
    <img
      src={poster}
      alt={alt}
      width={960}
      height={768}
      fetchPriority="high"
      className={className}
    />
  );
}

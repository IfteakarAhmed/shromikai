/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE?: string
  /** '1' builds the static recorded replay: no server, the exported files answer (ADR-0027). */
  readonly VITE_STATIC_REPLAY?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/** Languages that have a prerendered public URL prefix (/en/, /fr/). */
export const PUBLIC_LANGUAGES = ['en', 'fr'] as const;
export type PublicLanguage = (typeof PUBLIC_LANGUAGES)[number];

/** The public language in a URL path like '/fr/' or '/en', or null when the path is not one. */
export function languageFromPath(path: string): PublicLanguage | null {
  const match = /^\/(en|fr)(?:[/?#]|$)/.exec(path);
  return match ? (match[1] as PublicLanguage) : null;
}

import { Pipe, PipeTransform } from '@angular/core';
import { readLanguage } from './language.service';

const translatedTexts = new Map<string, string>();

export function rememberAiTexts(texts: Record<string, string>): void {
  for (const [source, translated] of Object.entries(texts)) translatedTexts.set(source, translated);
}

/** Display-only: source DTOs remain unchanged for domain actions and requests. */
export function aiText(source: string | null | undefined): string {
  return source && readLanguage() === 'fr' ? translatedTexts.get(source) ?? source : source ?? '';
}

/** Translate flowchart labels, never node identifiers, connections, or executable syntax. */
export function aiDiagram(source: string): string {
  if (readLanguage() !== 'fr') return source;
  const label = (raw: string) => aiText(raw.trim().replace(/^"|"$/g, ''))
    .replace(/["\r\n]/g, "'").replace(/</g, '(').replace(/>/g, ')');
  return source.replace(/([A-Za-z_][\w-]*\s*[[({])([^\][{}()\n]+)([\])}])/g,
    (_, open: string, text: string, close: string) => `${open}"${label(text)}"${close}`)
    .replace(/\|([^|\n]+)\|/g, (_, text: string) => `|"${label(text)}"|`);
}

@Pipe({ name: 'aiText', standalone: true })
export class AiTextPipe implements PipeTransform {
  transform(source: string | null | undefined): string { return aiText(source); }
}

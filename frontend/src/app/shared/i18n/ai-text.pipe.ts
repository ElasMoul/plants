import { Pipe, PipeTransform } from '@angular/core';
import { Optional } from '@angular/core';
import { SectionLanguageState } from './section-language.state';
import { parseDetailAsList } from '../utils/detail-list.util';

/** Content only changes inside an explicit section language context. */
export function aiText(source: string | null | undefined, section?: SectionLanguageState | null): string {
  return section?.text(source) ?? source ?? '';
}

/** Translate flowchart labels, never node identifiers, connections, or executable syntax. */
export function aiDiagram(source: string, section?: SectionLanguageState | null): string {
  if (!section) return source;
  const label = (raw: string) => aiText(raw.trim().replace(/^"|"$/g, ''), section)
    .replace(/["\r\n]/g, "'").replace(/</g, '(').replace(/>/g, ')');
  return source.replace(/([A-Za-z_][\w-]*\s*[[({])([^\][{}()\n]+)([\])}])/g,
    (_, open: string, text: string, close: string) => `${open}"${label(text)}"${close}`)
    .replace(/\|([^|\n]+)\|/g, (_, text: string) => `|"${label(text)}"|`);
}

@Pipe({ name: 'aiText', standalone: true, pure: false })
export class AiTextPipe implements PipeTransform {
  constructor(@Optional() private readonly section: SectionLanguageState | null = null) {}
  transform(source: string | null | undefined): string { return aiText(source, this.section); }
}

@Pipe({ name: 'aiDiagram', standalone: true, pure: false })
export class AiDiagramPipe implements PipeTransform {
  constructor(@Optional() private readonly section: SectionLanguageState | null = null) {}
  transform(source: string): string { return aiDiagram(source, this.section); }
}
@Pipe({ name: 'aiDetail', standalone: true, pure: false })
export class AiDetailPipe implements PipeTransform {
  constructor(@Optional() private readonly section: SectionLanguageState | null = null) {}
  transform(source: string | null | undefined) { return parseDetailAsList(aiText(source, this.section)); }
}

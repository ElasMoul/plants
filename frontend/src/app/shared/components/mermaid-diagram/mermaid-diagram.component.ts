import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

let mermaidInitialized = false;
let renderCounter = 0;

const THEME_VARIABLES = {
  primaryColor: '#e5efe9',
  primaryBorderColor: '#1a3c2a',
  lineColor: '#7a8c80',
  fontFamily: 'Inter, Roboto, sans-serif',
};

@Component({
  selector: 'app-mermaid-diagram',
  templateUrl: './mermaid-diagram.component.html',
  styleUrls: ['./mermaid-diagram.component.scss'],
})
export class MermaidDiagramComponent implements OnChanges {
  @Input() definition = '';

  svg: SafeHtml | null = null;

  constructor(private readonly sanitizer: DomSanitizer) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['definition']) {
      void this.render();
    }
  }

  private async render(): Promise<void> {
    this.svg = null;

    if (!this.definition?.trim()) return;

    try {
      const mermaid = (await import('mermaid')).default;
      if (!mermaidInitialized) {
        mermaid.initialize({ startOnLoad: false, theme: 'base', themeVariables: THEME_VARIABLES });
        mermaidInitialized = true;
      }

      const id = `mermaid-diagram-${renderCounter++}`;
      const { svg } = await mermaid.render(id, this.definition);
      this.svg = this.sanitizer.bypassSecurityTrustHtml(svg);
    } catch {
      // Malformed mermaid syntax from the AI — fail silently, diagrams are a bonus, never required.
      this.svg = null;
    }
  }
}

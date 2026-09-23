import { Component, Input } from "@angular/core";

/** Local line icons keep the console usable when external font services are unavailable. */
@Component({
  selector: "app-admin-icon",
  standalone: true,
  template:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="paths[name] || paths[\'eco\']" /></svg>',
  styles: [
    ":host{display:inline-flex;width:21px;height:21px;flex-shrink:0}svg{width:100%;height:100%}",
  ],
})
export class AdminIconComponent {
  @Input() name = "eco";
  readonly paths: Record<string, string> = {
    eco: "M5 20C5 12 11 8 18 5M5 16C0 6 9 2 21 3C21 15 14 21 5 16Z",
    space_dashboard:
      "M3 3H10V12H3ZM14 3H21V8H14ZM3 16H10V21H3ZM14 12H21V21H14Z",
    people_outline:
      "M16 21V18C16 15 13 14 9 14S2 15 2 18V21M9 10A4 4 0 1 0 9 2A4 4 0 1 0 9 10M17 3C22 3 22 10 17 10M19 14C22 15 23 17 22 21",
    auto_awesome: "M12 2L15 9L22 12L15 15L12 22L9 15L2 12L9 9ZM20 2V6M18 4H22",
    history: "M3 11A9 9 0 1 1 5 18M3 4V11H10M12 7V12L16 15",
    spa: "M12 21C4 21 2 16 2 10C8 10 12 14 12 21ZM12 21C20 21 22 16 22 10C16 10 12 14 12 21ZM8 11C7 7 10 3 12 2C14 3 17 7 16 11",
    arrow_back: "M20 12H4M10 6L4 12L10 18",
    arrow_forward: "M4 12H20M14 6L20 12L14 18",
    chevron_right: "M9 5L16 12L9 19",
    chevron_left: "M15 5L8 12L15 19",
    unfold_more: "M8 8L12 4L16 8M8 16L12 20L16 16",
    refresh: "M20 8A9 9 0 1 0 1 12M20 3V9H14",
    local_florist:
      "M12 22V13M12 19C7 19 4 17 4 14C9 14 12 16 12 19M12 16C17 16 20 14 20 11M12 4C7 -1 3 5 7 8C2 11 8 16 12 12C17 16 22 11 17 8C21 4 15 0 12 4Z",
    document_scanner:
      "M3 8V3H8M16 3H21V8M21 16V21H16M8 21H3V16M8 7H16V17H8ZM1 12H23",
    healing:
      "M3 15L15 3Q17 1 19 3L21 5Q23 7 21 9L9 21Q7 23 5 21L3 19Q1 17 3 15ZM8 10L14 16M10 8L16 14M11 11H11.1M13 13H13.1",
    schedule: "M22 12A10 10 0 1 1 2 12A10 10 0 1 1 22 12M12 6V12L16 14",
    hourglass_empty: "M6 2H18V6L13 12L18 18V22H6V18L11 12L6 6ZM6 3H18M6 21H18",
    troubleshoot:
      "M16 16L22 22M19 10A9 9 0 1 1 1 10A9 9 0 1 1 19 10M4 10H7L9 6L12 14L14 10H17",
    manage_accounts:
      "M12 21H2V19C2 15 6 14 10 14M10 10A4 4 0 1 0 10 2A4 4 0 1 0 10 10M20 17A3 3 0 1 1 14 17A3 3 0 1 1 20 17M17 12V14M17 20V22M12 17H14M20 17H22",
    search: "M16 16L22 22M19 10A9 9 0 1 1 1 10A9 9 0 1 1 19 10",
    person_search:
      "M14 21H2V18C2 15 6 14 10 14M10 10A4 4 0 1 0 10 2A4 4 0 1 0 10 10M21 17A3 3 0 1 1 15 17A3 3 0 1 1 21 17M20 20L23 23",
    shield: "M12 2L21 6V12C21 18 12 22 12 22S3 18 3 12V6ZM8 12L11 15L17 9",
    computer: "M2 3H22V17H2ZM8 21H16M12 17V21",
    info_outline:
      "M22 12A10 10 0 1 1 2 12A10 10 0 1 1 22 12M12 11V17M12 7H12.01",
    error_outline:
      "M22 12A10 10 0 1 1 2 12A10 10 0 1 1 22 12M12 6V13M12 17H12.01",
    check_circle_outline:
      "M22 12A10 10 0 1 1 2 12A10 10 0 1 1 22 12M7 12L10 15L17 8",
  };
}

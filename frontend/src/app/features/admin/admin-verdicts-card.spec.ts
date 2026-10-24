import { FR } from "../../shared/i18n/fr";

// Node globals are not in the browser-typed tsconfig; the template is read as text.
declare const require: (m: string) => { readFileSync(p: string, e: string): string };
declare const __dirname: string;
const html: string = require("fs").readFileSync(`${__dirname}/admin.component.html`, "utf8");
const start = html.indexOf('data-testid="verdict-counts"');
const card = html.slice(start, html.indexOf("</article>", start));

describe("admin verdicts card template", () => {
  it("keeps the body inside a padded wrapper", () => {
    expect(card).toContain('class="verdict-body"');
    expect(card.indexOf("verdict-body")).toBeGreaterThan(card.indexOf("panel-heading"));
  });

  it("styles counts like the stat cards", () => {
    expect(card.match(/class="stat-title"/g)).toHaveLength(3);
  });

  it.each([
    "Identification feedback",
    "Was this right?",
    "Right, wrong and not-sure answers users gave to scan results.",
    "From",
    "To",
    "Right",
    "Wrong",
    "Not sure",
  ])("routes %s through the t pipe with a French entry", (key) => {
    expect(card).toContain(`"${key}" | t`);
    expect(FR[key]).toBeTruthy();
  });

  it("has no literal French text", () => {
    expect(card).not.toMatch(/Est-ce|Correct|Incorrect|Pas sûr/);
  });
});

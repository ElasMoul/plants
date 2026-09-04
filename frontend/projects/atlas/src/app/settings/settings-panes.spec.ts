import { DEFAULT_SETTINGS, SettingsSection, structuredCloneish } from './settings.model';
import {
  MOTION_CONTROLS_HTML,
  PaneContext,
  renderPane,
  routeOverviewClick,
} from './settings-panes';

const SECTIONS: SettingsSection[] = [
  'general',
  'profile',
  'notifications',
  'appearance',
  'data',
  'ai',
  'privacy',
  'integrations',
  'advanced',
];

function ctx(over: Partial<PaneContext> = {}): PaneContext {
  return {
    settings: structuredCloneish(DEFAULT_SETTINGS),
    prefs: {
      aiModelPreference: 'GITHUB_GPT4O',
      visionModelPreference: 'GITHUB_GPT4O',
      reasoningModelPreference: 'DEEPSEEK_R1',
      visionModelAvailability: { ANTHROPIC_CLAUDE: false },
      reasoningModelAvailability: { ANTHROPIC_CLAUDE: false },
      plantnetProject: 'all',
      plantnetLang: 'en',
      businessTier: false,
    },
    prefsState: 'idle',
    mock: false,
    push: 'off',
    account: { name: 'Sam Okafor', email: 'sam@example.org', session: 'this browser' },
    vapidConfigured: false,
    ...over,
  };
}

function dom(html: string): HTMLElement {
  const host = document.createElement('div');
  host.innerHTML = html;
  return host;
}

describe('settings panes (S7)', () => {
  it('renders every section but Appearance, each with its own heading', () => {
    for (const section of SECTIONS) {
      const html = renderPane(section, ctx());
      if (section === 'appearance') {
        expect(html).toBeNull();
        continue;
      }
      expect(html).not.toBeNull();
      const head = dom(html as string).querySelector('h3.sec');
      expect(head?.textContent).toMatch(/·/);
    }
  });

  it('offers every settings key exactly once across the panes', () => {
    const c = ctx({ mock: true });
    const html =
      SECTIONS.map(s => renderPane(s, c) ?? '').join('') + MOTION_CONTROLS_HTML(c.settings);
    const keys = Array.from(dom(html).querySelectorAll('[data-set]'));
    // One CONTROL per key: a picker's options share a group, a field is alone.
    const controls: Record<string, Set<Element>> = {};
    for (const el of keys) {
      const k = (el as HTMLElement).dataset['set'] as string;
      (controls[k] ??= new Set()).add(el.closest('.palettes') ?? el);
    }
    const counts: Record<string, number> = {};
    for (const [k, set] of Object.entries(controls)) counts[k] = set.size;
    // ui and palette are the pinned pane's own pickers; seenAt is written by an
    // action ("Mark all read"), never chosen.
    const skip = new Set(['appearance.ui', 'appearance.palette', 'notifications.seenAt']);
    for (const [group, leaves] of Object.entries(DEFAULT_SETTINGS)) {
      for (const leaf of Object.keys(leaves)) {
        const path = `${group}.${leaf}`;
        if (skip.has(path)) continue;
        expect([path, counts[path]]).toEqual([path, 1]);
      }
    }
  });

  it('presses exactly one option in every group', () => {
    const c = ctx({ mock: true });
    for (const section of SECTIONS) {
      const html = renderPane(section, c);
      if (!html) continue;
      for (const group of Array.from(dom(html).querySelectorAll('.palettes'))) {
        const pressed = group.querySelectorAll('[aria-pressed="true"]');
        expect([section, group.getAttribute('aria-label'), pressed.length]).toEqual([
          section,
          group.getAttribute('aria-label'),
          1,
        ]);
      }
    }
  });

  it('follows the current value rather than the default', () => {
    const c = ctx();
    c.settings.general.dateStyle = 'absolute';
    const html = renderPane('general', c) as string;
    const chosen = dom(html).querySelector(
      '[data-set="general.dateStyle"][aria-pressed="true"]',
    ) as HTMLElement;
    expect(chosen.dataset['value']).toBe('absolute');
  });

  it('disables a model the server cannot serve, and says why', () => {
    const html = renderPane('ai', ctx()) as string;
    const claude = dom(html).querySelector(
      '[data-set="ai.visionModelPreference"][data-value="ANTHROPIC_CLAUDE"]',
    ) as HTMLElement;
    expect(claude.getAttribute('aria-disabled')).toBe('true');
    expect(claude.textContent).toContain('Not configured on this server');
    const gpt = dom(html).querySelector(
      '[data-set="ai.visionModelPreference"][data-value="GITHUB_GPT4O"]',
    ) as HTMLElement;
    expect(gpt.getAttribute('aria-disabled')).toBeNull();
  });

  it('says the models could not be read rather than showing empty pickers', () => {
    const html = renderPane('ai', ctx({ prefsState: 'failed', prefs: null })) as string;
    const el = dom(html);
    expect(el.querySelector('.state--error')).not.toBeNull();
    expect(el.querySelector('[data-action="reload-prefs"]')).not.toBeNull();
    expect(el.querySelector('[data-set="ai.visionModelPreference"]')).toBeNull();
  });

  it('shows the mock controls only in the mock garden', () => {
    const live = renderPane('data', ctx({ mock: false })) as string;
    expect(live).not.toContain('data.mockScenario');
    expect(live).not.toContain('reset-mock');
    const mock = renderPane('data', ctx({ mock: true })) as string;
    expect(mock).toContain('data.mockScenario');
    expect(dom(mock).querySelector('[data-action="reset-mock"]')).not.toBeNull();
    expect(dom(renderPane('advanced', ctx({ mock: false })) as string)
      .querySelector('[data-action="mock-fail-next"]')).toBeNull();
  });

  it('offers the constitution parameters as rows with no control at all', () => {
    const el = dom(renderPane('advanced', ctx()) as string);
    const dls = Array.from(el.querySelectorAll('dl.rows'));
    expect(dls.length).toBe(1);
    expect(dls[0].textContent).toContain('4 → 2 + N more');
    expect(dls[0].querySelectorAll('button, input, select').length).toBe(0);
  });

  it('escapes what the reader typed', () => {
    const c = ctx();
    c.settings.profile.displayName = '<img src=x onerror="boom">';
    const html = renderPane('profile', c) as string;
    expect(html).not.toContain('<img');
    const input = dom(html).querySelector(
      'input[data-set="profile.displayName"]',
    ) as HTMLInputElement;
    expect(input.value).toBe('<img src=x onerror="boom">');
  });

  it('never opens a dialog and never says loading', () => {
    const c = ctx({ mock: true, prefsState: 'reading' });
    const html = SECTIONS.map(s => renderPane(s, c) ?? '').join('');
    expect(html).not.toContain('role="dialog"');
    expect(html.toLowerCase()).not.toContain('loading');
    expect(html).not.toContain('...');
  });

  describe('routeOverviewClick', () => {
    function click(html: string, selector: string) {
      const host = document.createElement('div');
      host.innerHTML = html;
      document.body.appendChild(host);
      const el = host.querySelector<HTMLElement>(selector) as HTMLElement;
      const intent = routeOverviewClick(el);
      host.remove();
      return intent;
    }

    it('names a section from its verbatim nav label', () => {
      expect(
        click('<section id="settings"><nav><button>Data &amp; Sync</button></nav></section>', 'button'),
      ).toEqual({ kind: 'section', section: 'data' });
    });

    it('reads a set with its typed value', () => {
      expect(
        click('<button data-set="data.pageSize" data-value="100" data-kind="number"></button>', 'button'),
      ).toEqual({ kind: 'set', key: 'data.pageSize', value: 100 });
      expect(
        click('<button data-set="care.askForNotes" data-value="true" data-kind="boolean"></button>', 'button'),
      ).toEqual({ kind: 'set', key: 'care.askForNotes', value: true });
    });

    it('refuses a disabled option', () => {
      expect(
        click(
          '<button data-set="ai.visionModelPreference" data-value="ANTHROPIC_CLAUDE" aria-disabled="true"></button>',
          'button',
        ),
      ).toBeNull();
    });

    it('routes actions, the pickers, and the four footer controls', () => {
      expect(click('<button data-action="reload"></button>', 'button')).toEqual({
        kind: 'action',
        name: 'reload',
      });
      expect(click('<button class="palette" data-ui="glasshouse-table"></button>', 'button')).toEqual({
        kind: 'ui',
        value: 'glasshouse-table',
      });
      expect(click('<button class="palette" data-palette="terrarium"></button>', 'button')).toEqual({
        kind: 'palette',
        value: 'terrarium',
      });
      expect(click('<button id="dive-back"></button>', 'button')).toEqual({ kind: 'close' });
      expect(click('<button id="close-settings"></button>', 'button')).toEqual({ kind: 'close' });
      expect(click('<button id="cancel-settings"></button>', 'button')).toEqual({ kind: 'cancel' });
      expect(click('<button id="save-settings"></button>', 'button')).toEqual({ kind: 'save' });
      expect(
        click('<section id="settings"><footer><button class="hop">Reset to defaults</button></footer></section>', '.hop'),
      ).toEqual({ kind: 'reset' });
    });

    it('ignores a click on nothing in particular', () => {
      expect(click('<p>just prose</p>', 'p')).toBeNull();
    });
  });
});

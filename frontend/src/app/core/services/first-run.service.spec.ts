import { FIRST_RUN_EVENTS, FirstRunService } from './first-run.service';
import { FIRST_RUN_KEYS } from './first-run.keys.spec-data';
import { FR } from '../../shared/i18n/fr';
import { AR } from '../../shared/i18n/ar';
import { FirstRunIntroComponent } from '../../shared/components/first-run-intro/first-run-intro.component';
import { PhotoUploadComponent } from '../../features/identification/components/photo-upload/photo-upload.component';

describe('first-run guidance', () => {
  let service: FirstRunService;
  let va: jest.Mock;

  beforeEach(() => {
    localStorage.clear();
    va = jest.fn();
    (window as any).va = va;
    service = new FirstRunService();
  });
  afterEach(() => { delete (window as any).va; });

  const events = () => va.mock.calls.map(c => c[1].name);

  describe('intro', () => {
    it('shows for a new user, and Skip hides it, persists the flag and records the event', () => {
      const intro = new FirstRunIntroComponent(service);
      intro.ngOnInit();
      expect(intro.visible).toBe(true);
      intro.skip();
      expect(intro.visible).toBe(false);
      expect(service.isSeen('intro')).toBe(true);
      expect(events()).toEqual([FIRST_RUN_EVENTS.introShown, FIRST_RUN_EVENTS.introSkipped]);
    });

    it('does not reappear once seen', () => {
      service.markSeen('intro');
      const intro = new FirstRunIntroComponent(service);
      intro.ngOnInit();
      expect(intro.visible).toBe(false);
      expect(va).not.toHaveBeenCalled();
    });

    it('walks through the steps and finishes', () => {
      const intro = new FirstRunIntroComponent(service);
      intro.ngOnInit();
      intro.next(); intro.next();
      expect(intro.isLast).toBe(true);
      intro.next();
      expect(intro.visible).toBe(false);
      expect(events()).toContain(FIRST_RUN_EVENTS.introFinished);
    });
  });

  describe('photo upload', () => {
    const make = () => {
      const upload = new PhotoUploadComponent({ getPlants: jest.fn() } as any, { open: jest.fn() } as any, {} as any, service);
      upload.lockedPlantId = 1; // skip the plant list call
      upload.ngOnInit();
      return upload;
    };
    const input = () => ({ click: jest.fn() } as unknown as HTMLInputElement);
    const permission = (state: string | Error) => {
      Object.defineProperty(navigator, 'permissions', {
        configurable: true,
        value: { query: state instanceof Error ? jest.fn().mockRejectedValue(state) : jest.fn().mockResolvedValue({ state }) },
      });
    };

    it('shows tips before the first scan and hides them once dismissed', () => {
      const upload = make();
      expect(upload.showTips).toBe(true);
      upload.dismissTips();
      expect(make().showTips).toBe(false);
    });

    it('explains the camera first and only opens the capture input on "Use camera"', async () => {
      permission('prompt');
      const upload = make();
      const capture = input();
      upload.openCameraExplainer();
      expect(upload.showCameraExplainer).toBe(true);
      expect(capture.click).not.toHaveBeenCalled();
      await upload.useCamera(capture);
      expect(capture.click).toHaveBeenCalledTimes(1);
    });

    it('keeps the upload option and records the refusal when camera permission is denied', async () => {
      permission('denied');
      const upload = make();
      const capture = input();
      va.mockClear();
      upload.openCameraExplainer();
      await upload.useCamera(capture);
      expect(capture.click).not.toHaveBeenCalled();
      expect(upload.cameraRefused).toBe(true);
      expect(upload.showCameraExplainer).toBe(true);
      expect(events()).toContain(FIRST_RUN_EVENTS.cameraPermissionRefused);
      const file = input();
      upload.chooseUploadInstead(file);
      expect(file.click).toHaveBeenCalled();
      expect(events()).toContain(FIRST_RUN_EVENTS.uploadFallbackChosen);
    });

    it('lets the browser prompt when the Permissions API cannot query the camera', async () => {
      permission(new Error('unsupported'));
      const upload = make();
      const capture = input();
      await upload.useCamera(capture);
      expect(capture.click).toHaveBeenCalled();
    });

    it('records the first scan once', () => {
      const upload = make();
      upload.onAnalyze(); upload.onAnalyze();
      expect(events().filter(e => e === FIRST_RUN_EVENTS.firstScanStarted)).toHaveLength(1);
    });
  });

  it('has non-empty FR and AR text for every new string', () => {
    for (const key of FIRST_RUN_KEYS) {
      expect(FR[key]?.trim()).toBeTruthy();
      expect(AR[key]?.trim()).toBeTruthy();
    }
  });
});

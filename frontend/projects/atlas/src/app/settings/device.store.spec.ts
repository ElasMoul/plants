import { TestBed } from '@angular/core/testing';
import { DEVICE_KEY, DeviceStore } from './device.store';

function make(): DeviceStore {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [DeviceStore] });
  return TestBed.inject(DeviceStore);
}

describe('DeviceStore', () => {
  beforeEach(() => localStorage.clear());

  it('keeps live and mock care state apart', () => {
    const d = make();
    d.pausePlan('mock', 203);
    d.rememberTreatment('mock', 301);
    expect(d.care('mock').pausedPlanIds).toEqual([203]);
    expect(d.care('live').pausedPlanIds).toEqual([]);
    expect(d.care('live').knownTreatmentIds).toEqual([]);
    expect(make().care('mock').pausedPlanIds).toEqual([203]);
  });

  it('caps remembered treatment ids at ten, newest first', () => {
    const d = make();
    for (let i = 1; i <= 12; i++) d.rememberTreatment('live', i);
    const ids = d.care('live').knownTreatmentIds;
    expect(ids.length).toBe(10);
    expect(ids[0]).toBe(12);
    expect(ids).not.toContain(1);
    d.rememberTreatment('live', 12);
    expect(d.care('live').knownTreatmentIds[0]).toBe(12);
    expect(d.care('live').knownTreatmentIds.length).toBe(10);
    d.forgetTreatment('live', 12);
    expect(d.care('live').knownTreatmentIds).not.toContain(12);
  });

  it('pause and resume are idempotent', () => {
    const d = make();
    d.pausePlan('live', 7);
    d.pausePlan('live', 7);
    expect(d.care('live').pausedPlanIds).toEqual([7]);
    d.resumePlan('live', 7);
    d.resumePlan('live', 7);
    expect(d.care('live').pausedPlanIds).toEqual([]);
  });

  it('prunes snoozed entries whose time has passed', () => {
    const d = make();
    d.snooze('live', [601], '2026-09-04T09:00:00.000Z');
    d.snooze('live', [602], '2026-09-06T09:00:00.000Z');
    d.pruneSnoozed('live', '2026-09-05T00:00:00.000Z');
    expect(d.care('live').snoozed[601]).toBeUndefined();
    expect(d.care('live').snoozed[602]).toBe('2026-09-06T09:00:00.000Z');
  });

  it('clear removes the key', () => {
    const d = make();
    d.setLastFocus('n-garden');
    expect(localStorage.getItem(DEVICE_KEY)).not.toBeNull();
    d.clear();
    expect(localStorage.getItem(DEVICE_KEY)).toBeNull();
    expect(d.state().lastFocus).toBeUndefined();
  });

  it('remembers the push endpoint and forgets it again', () => {
    const d = make();
    d.setPush({ endpoint: 'https://push.example/x', subscribedAt: '2026-09-04T09:00:00.000Z' });
    expect(make().state().push?.endpoint).toBe('https://push.example/x');
    d.setPush(undefined);
    expect(make().state().push).toBeUndefined();
  });
});

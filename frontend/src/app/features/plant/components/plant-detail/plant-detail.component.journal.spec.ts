import { of, throwError } from 'rxjs';
import { PlantDetailComponent } from './plant-detail.component';

describe('PlantDetailComponent journal events (PLA-111)', () => {
  const event = { id: 5, type: 'PRUNING', eventDate: '2026-05-01', note: null } as any;
  const scan = { id: 9, createdAt: '2026-05-02T10:00:00Z' } as any;

  function build(dialogResult: any = undefined) {
    const journal = {
      list: jest.fn().mockReturnValue(of({ data: { content: [event] } })),
      create: jest.fn().mockReturnValue(of({})),
      update: jest.fn().mockReturnValue(of({})),
      delete: jest.fn().mockReturnValue(of({})),
    };
    const ident = { getPlantIdentifications: jest.fn().mockReturnValue(of({ data: { content: [scan] } })) };
    const snack = { open: jest.fn() };
    const dialog = { open: jest.fn().mockReturnValue({ afterClosed: () => of(dialogResult) }) };
    const cmp = new PlantDetailComponent(
      {} as any, {} as any, {} as any, {} as any, journal as any, {} as any,
      ident as any, {} as any, snack as any, dialog as any, {} as any,
    );
    (cmp as any).plantId = 1;
    return { cmp, journal, ident, snack, dialog };
  }

  it('loads scans and events and merges them newest first', () => {
    const { cmp, journal } = build();
    cmp.loadScanHistory();
    expect(journal.list).toHaveBeenCalledWith(1);
    expect(cmp.timeline.map(i => i.kind)).toEqual(['scan', 'event']);
    expect(cmp.trackByTimelineItem(0, cmp.timeline[0])).toBe('s9');
    expect(cmp.trackByTimelineItem(1, cmp.timeline[1])).toBe('e5');
  });

  it('reports a load failure', () => {
    const { cmp, journal, snack } = build();
    journal.list.mockReturnValue(throwError(() => new Error('x')));
    cmp.loadJournalEvents();
    expect(snack.open).toHaveBeenCalled();
  });

  it('creates an event from the dialog result and reloads', () => {
    const body = { type: 'PRUNING', eventDate: '2026-05-01', note: null };
    const { cmp, journal } = build(body);
    cmp.openJournalEventDialog();
    expect(journal.create).toHaveBeenCalledWith(1, body);
    expect(journal.list).toHaveBeenCalled();
  });

  it('updates when editing and reports save errors', () => {
    const body = { type: 'PRUNING', eventDate: '2026-05-01', note: 'n' };
    const { cmp, journal, snack } = build(body);
    cmp.openJournalEventDialog(event);
    expect(journal.update).toHaveBeenCalledWith(1, 5, body);
    journal.update.mockReturnValue(throwError(() => new Error('x')));
    cmp.openJournalEventDialog(event);
    expect(snack.open).toHaveBeenCalledTimes(2);
  });

  it('does nothing when the dialog is dismissed', () => {
    const { cmp, journal } = build(undefined);
    cmp.openJournalEventDialog();
    expect(journal.create).not.toHaveBeenCalled();
  });

  it('deletes only after confirmation and reports errors', () => {
    const { cmp, journal, snack } = build();
    const confirm = jest.spyOn(window, 'confirm').mockReturnValue(false);
    cmp.deleteJournalEvent(event);
    expect(journal.delete).not.toHaveBeenCalled();
    confirm.mockReturnValue(true);
    cmp.deleteJournalEvent(event);
    expect(journal.delete).toHaveBeenCalledWith(1, 5);
    journal.delete.mockReturnValue(throwError(() => new Error('x')));
    cmp.deleteJournalEvent(event);
    expect(snack.open).toHaveBeenCalledTimes(2);
    confirm.mockRestore();
  });
});

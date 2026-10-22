import { JournalEventDialogComponent } from './journal-event-dialog.component';

describe('JournalEventDialogComponent (PLA-111)', () => {
  function build(event?: any) {
    const ref = { close: jest.fn() };
    const cmp = new JournalEventDialogComponent(ref as any, { event });
    return { ref, cmp };
  }

  it('defaults to a new repotting event dated today', () => {
    const { cmp } = build();
    expect(cmp.type).toBe('REPOTTING');
    expect(cmp.eventDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(cmp.valid).toBe(true);
  });

  it('closes with the trimmed note, or null when blank', () => {
    const { ref, cmp } = build({ id: 1, type: 'PRUNING', eventDate: '2026-05-01', note: ' hi ' });
    cmp.save();
    expect(ref.close).toHaveBeenCalledWith({ type: 'PRUNING', eventDate: '2026-05-01', note: 'hi' });
    cmp.note = '  ';
    cmp.save();
    expect(ref.close).toHaveBeenLastCalledWith({ type: 'PRUNING', eventDate: '2026-05-01', note: null });
  });

  it('does not save an invalid date and cancel closes empty', () => {
    const { ref, cmp } = build();
    cmp.eventDate = '';
    expect(cmp.valid).toBe(false);
    cmp.save();
    expect(ref.close).not.toHaveBeenCalled();
    cmp.cancel();
    expect(ref.close).toHaveBeenCalledWith();
  });
});

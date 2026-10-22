import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { JournalEventService } from './journal-event.service';

describe('JournalEventService (PLA-111)', () => {
  function build() {
    const http = {
      get: jest.fn(() => of({})),
      post: jest.fn(() => of({})),
      put: jest.fn(() => of({})),
      delete: jest.fn(() => of(undefined)),
    };
    return { http, service: new JournalEventService(http as unknown as HttpClient) };
  }

  it('lists, creates, updates and deletes against the plant journal URL', () => {
    const { http, service } = build();
    const body = { type: 'PRUNING' as const, eventDate: '2026-05-01', note: null };
    service.list(4).subscribe();
    service.create(4, body).subscribe();
    service.update(4, 7, body).subscribe();
    service.delete(4, 7).subscribe();
    expect(http.get).toHaveBeenCalledWith(expect.stringMatching(/\/plants\/4\/journal-events$/), expect.anything());
    expect(http.post).toHaveBeenCalledWith(expect.stringMatching(/\/plants\/4\/journal-events$/), body);
    expect(http.put).toHaveBeenCalledWith(expect.stringMatching(/\/journal-events\/7$/), body);
    expect(http.delete).toHaveBeenCalledWith(expect.stringMatching(/\/journal-events\/7$/));
  });
});

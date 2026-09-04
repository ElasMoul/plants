import { frameToken, SseParser } from './sse-parse';

/** Feeds the parser the way HttpDownloadProgressEvent does: cumulative text. */
function walk(chunks: string[]): { tokens: string[]; parser: SseParser } {
  const parser = new SseParser();
  const tokens: string[] = [];
  let seen = '';
  for (const chunk of chunks) {
    seen += chunk;
    tokens.push(...parser.feed(seen));
  }
  return { tokens, parser };
}

describe('SseParser', () => {
  it('reads one token per event and asks for no sentinel', () => {
    const { tokens, parser } = walk(['data:Hi\n\n', 'data: there\n\n']);
    expect(tokens).toEqual(['Hi', 'there']);
    expect(parser.end()).toEqual([]);
  });

  it('keeps the newline of a token Spring split across data lines', () => {
    // Spring writes a token containing \n as consecutive data: lines of ONE event.
    const { tokens } = walk(['data:first\ndata:second\n\n']);
    expect(tokens).toEqual(['first\nsecond']);
  });

  it('frames and re-reads a multi-line token unchanged', () => {
    const token = 'Two things.\n\nOne, water it.';
    const { tokens } = walk([frameToken(token)]);
    expect(tokens).toEqual([token]);
  });

  it('holds an event split across chunks until it completes', () => {
    const { tokens } = walk(['data:par', 'tial']);
    expect(tokens).toEqual([]);
    const parser = new SseParser();
    let seen = 'data:par';
    expect(parser.feed(seen)).toEqual([]);
    seen += 'tial\n';
    expect(parser.feed(seen)).toEqual([]);
    seen += '\n';
    expect(parser.feed(seen)).toEqual(['partial']);
  });

  it('flushes an unterminated event when the stream ends', () => {
    const parser = new SseParser();
    expect(parser.feed('data:cut off')).toEqual([]);
    expect(parser.end()).toEqual(['cut off']);
  });

  it('ignores comment and field lines that are not data', () => {
    const { tokens } = walk([':keep-alive\n\nevent:message\ndata:ok\n\n']);
    expect(tokens).toEqual(['ok']);
  });

  it('tolerates CRLF framing and a shrinking text', () => {
    const parser = new SseParser();
    expect(parser.feed('data:ok\r\n\r\n')).toEqual(['ok']);
    expect(parser.feed('')).toEqual([]);
  });
});

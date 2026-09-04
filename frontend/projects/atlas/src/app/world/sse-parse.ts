/**
 * A pure, incremental Server-Sent-Events reader for the chat stream. No Angular,
 * no HTTP — it is fed the cumulative `partialText` of a download-progress event
 * and returns only the tokens that have completed since the last feed.
 *
 * Two deliberate differences from the classic frontend's parser:
 *
 *  - a token containing a newline is emitted by Spring as consecutive `data:`
 *    lines inside ONE event; this parser rejoins them WITH the newline, where
 *    the classic one silently drops it,
 *  - no optional-space stripping after `data:`. Spring's SseEmitter writes
 *    `data:` immediately followed by the payload, so a token that legitimately
 *    begins with a space (the Ollama per-token path emits ' most likely.')
 *    would lose it. The round trip through `frameToken` is byte-exact,
 *  - no terminal sentinel is expected. The server sends no `[DONE]` and no
 *    completion event: the end of the stream is the HTTP response ending, which
 *    only the caller can see. `end()` flushes whatever stands unterminated.
 */
export class SseParser {
  /** How much of the cumulative text has already been read. */
  private consumed = 0;
  /** The trailing, not-yet-newline-terminated line. */
  private buffer = '';
  /** The `data:` lines of the event being assembled. */
  private lines: string[] = [];

  /** The tokens completed by this chunk, in order. Never throws. */
  feed(partialText: string): string[] {
    if (partialText.length < this.consumed) return [];
    const chunk = partialText.slice(this.consumed);
    this.consumed = partialText.length;
    if (!chunk) return [];
    this.buffer += chunk;

    const parts = this.buffer.split('\n');
    this.buffer = parts.pop() ?? '';
    const out: string[] = [];
    for (const part of parts) {
      const line = part.endsWith('\r') ? part.slice(0, -1) : part;
      if (line === '') {
        const event = this.flush();
        if (event !== null) out.push(event);
        continue;
      }
      if (line.startsWith('data:')) this.lines.push(line.slice(5));
      // Any other field (event:, id:, retry:, a comment) is not part of the data.
    }
    return out;
  }

  /**
   * The stream ended. Anything held without its closing blank line is still a
   * real token — a 60s emitter timeout cuts the connection exactly there.
   */
  end(): string[] {
    const tail = this.buffer;
    this.buffer = '';
    if (tail.startsWith('data:')) this.lines.push(tail.slice(5));
    const event = this.flush();
    return event === null ? [] : [event];
  }

  private flush(): string | null {
    if (this.lines.length === 0) return null;
    const event = this.lines.join('\n');
    this.lines = [];
    return event;
  }
}

/** The wire framing of one token, exactly as Spring's SseEmitter writes it. */
export function frameToken(token: string): string {
  return (
    token
      .split('\n')
      .map(line => `data:${line}`)
      .join('\n') + '\n\n'
  );
}

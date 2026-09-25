/**
 * The SSE reader moved to @plantpal/shared-core so the classic chat uses the same, Spring-exact
 * parser (it used to drop newlines and eat leading spaces). Re-exported here so Atlas imports and
 * specs are unchanged.
 */
export { SseParser, frameToken } from '@plantpal/shared-core';

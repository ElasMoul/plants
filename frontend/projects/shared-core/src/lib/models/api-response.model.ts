export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
  correlationId?: string;
  // Error-path only — present once GlobalExceptionHandler returns real status codes (T7.1).
  errorCode?: number;
  // Set on 429 rate-limit errors only, once the backend's Bucket4j check exposes it (T7.1).
  retryAfterSeconds?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

package com.plantpal.session.entity;

/**
 * Distinguishes "you were away" from "the app broke" in user-facing copy (D2/D8) — the
 * session lifecycle's terminal states (architecture pack, mission 9b774285) all produce
 * different explanations, never a bare "signed out".
 */
public enum RevokedReason {
  LOGOUT,
  IDLE_TIMEOUT,
  ABSOLUTE_CAP,
  PASSWORD_CHANGE,
  ADMIN
}

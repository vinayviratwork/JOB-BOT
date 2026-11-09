package com.vinay.jobbot.entity;

/**
 * Lifecycle states for a job application.
 * Transitions: DISCOVERED -> DRAFTED -> APPROVED -> APPLIED -> (RESPONDED | REJECTED)
 * The application NEVER auto-submits; APPLIED is only set by a human action.
 */
public enum ApplicationStatus {
    DISCOVERED,
    DRAFTED,
    APPROVED,
    APPLIED,
    RESPONDED,
    REJECTED
}

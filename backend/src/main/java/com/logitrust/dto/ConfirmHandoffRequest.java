package com.logitrust.dto;

/**
 * Optional body for confirm-handoff. confirmedItemCount feeds the
 * quantity-mismatch fraud factor (FR-4.2) — how many items the receiver
 * actually counted vs. how many were declared at creation.
 */
public record ConfirmHandoffRequest(Integer confirmedItemCount) {
}

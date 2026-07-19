package io.github.nandydesikan.eip.returns.domain;

public enum ReturnWorkflowPhase {
    REQUESTED,
    ELIGIBILITY_CONFIRMED,
    RETURN_SHIPMENT_RESERVED,
    AWAITING_ITEM_RECEIPT,
    ITEM_RECEIVED,
    REFUND_CONFIRMED,
    COMPLETED,
    REJECTED,
    CANCELLED
}

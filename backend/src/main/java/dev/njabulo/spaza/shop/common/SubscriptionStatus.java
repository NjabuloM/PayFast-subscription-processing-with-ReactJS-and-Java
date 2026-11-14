package dev.njabulo.spaza.shop.common;

public enum SubscriptionStatus {
    /** Created at checkout, waiting for PayFast to confirm the first payment */
    PENDING,
    /** PayFast confirmed a payment (ITN with payment_status=COMPLETE) */
    ACTIVE,
    /** The subscription was cancelled at PayFast (ITN with payment_status=CANCELLED) */
    CANCELLED,
    /** The payment failed (ITN with payment_status=FAILED) */
    FAILED
}

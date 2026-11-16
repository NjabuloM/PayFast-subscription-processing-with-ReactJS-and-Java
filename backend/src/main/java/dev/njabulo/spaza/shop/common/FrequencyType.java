package dev.njabulo.spaza.shop.common;

import org.payment.gateway.SubscriptionFrequency;

/**
 * The billing options offered in the shop, and how each one maps onto a PayFast subscription.
 */
public enum FrequencyType {
    /** Billed every month for a year (12 payments) */
    MONTHLY("monthly", SubscriptionFrequency.MONTHLY_CYCLE_PERIOD, 12),
    /** Billed once a year, renewing until the subscriber cancels (0 = indefinite) */
    ANNUALLY("yearly", SubscriptionFrequency.ANNUAL_CYCLE_PERIOD, 0);

    private final String name;
    private final SubscriptionFrequency payFastFrequency;
    private final int cycles;

    FrequencyType(String name, SubscriptionFrequency payFastFrequency, int cycles) {
        this.name = name;
        this.payFastFrequency = payFastFrequency;
        this.cycles = cycles;
    }

    public String getName() {
        return name;
    }

    /** PayFast's frequency code, e.g. 3 for monthly and 6 for annual */
    public int getPayFastFrequencyCode() {
        return payFastFrequency.getCode();
    }

    /** Number of payments PayFast will collect; 0 means until cancelled */
    public int getCycles() {
        return cycles;
    }
}

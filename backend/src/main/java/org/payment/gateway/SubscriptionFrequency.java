package org.payment.gateway;

public enum SubscriptionFrequency {

    MONTHLY_CYCLE_PERIOD("Monthly", 3),
    QUARTERLY_CYCLE_PERIOD("Quarterly", 4),
    BIANNUALLY_CYCLE_PERIOD("Biannually", 5),
    ANNUAL_CYCLE_PERIOD("Annual", 6);

    private final String name;
    private final int code;

    SubscriptionFrequency(String name, int code) {
        this.name = name;
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}

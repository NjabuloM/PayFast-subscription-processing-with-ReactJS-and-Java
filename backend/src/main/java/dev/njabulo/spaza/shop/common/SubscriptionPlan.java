package dev.njabulo.spaza.shop.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A billing option for a magazine, as shown to the customer and as charged by PayFast.
 *
 * @param frequency the billing option
 * @param amount    the amount charged on every billing cycle, in rand
 * @param cycles    number of payments PayFast will collect; 0 means until cancelled
 */
public record SubscriptionPlan(FrequencyType frequency, BigDecimal amount, int cycles) {

    /** Discount for paying a year upfront */
    static final BigDecimal ANNUAL_DISCOUNT = new BigDecimal("0.10");

    /**
     * Prices are always worked out here, on the server. The client only says which option it wants.
     *
     * @param monthlyPrice the magazine's monthly price
     * @param frequency    the billing option
     */
    public static SubscriptionPlan of(double monthlyPrice, FrequencyType frequency) {
        BigDecimal monthly = BigDecimal.valueOf(monthlyPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = switch (frequency) {
            case MONTHLY -> monthly;
            // 12 months less 10%, rounded up to the next whole rand
            case ANNUALLY -> monthly.multiply(BigDecimal.valueOf(12))
                    .multiply(BigDecimal.ONE.subtract(ANNUAL_DISCOUNT))
                    .setScale(0, RoundingMode.CEILING)
                    .setScale(2, RoundingMode.UNNECESSARY);
        };
        return new SubscriptionPlan(frequency, amount, frequency.getCycles());
    }
}

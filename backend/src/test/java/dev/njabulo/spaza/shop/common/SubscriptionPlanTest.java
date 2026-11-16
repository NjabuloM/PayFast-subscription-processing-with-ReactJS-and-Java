package dev.njabulo.spaza.shop.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class SubscriptionPlanTest {

    @Test
    void monthlyPlanChargesTheMonthlyPriceTwelveTimes() {
        SubscriptionPlan plan = SubscriptionPlan.of(44.90, FrequencyType.MONTHLY);

        Assertions.assertEquals(new BigDecimal("44.90"), plan.amount());
        Assertions.assertEquals(12, plan.cycles());
        Assertions.assertEquals(3, plan.frequency().getPayFastFrequencyCode());
    }

    @Test
    void annualPlanIsTwelveMonthsLessTenPercentRoundedUpAndRenewsUntilCancelled() {
        SubscriptionPlan plan = SubscriptionPlan.of(44.90, FrequencyType.ANNUALLY); //44.90 x 12 x 0.9 = 484.92

        Assertions.assertEquals(new BigDecimal("485.00"), plan.amount());
        Assertions.assertEquals(0, plan.cycles());
        Assertions.assertEquals(6, plan.frequency().getPayFastFrequencyCode());
    }

    @Test
    void annualPlanForAWholeRandAmountIsNotRoundedFurther() {
        Assertions.assertEquals(new BigDecimal("324.00"), SubscriptionPlan.of(30, FrequencyType.ANNUALLY).amount());
    }
}

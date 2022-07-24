package org.payment.gateway;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;
import java.util.Map;

class PayFastProviderTest {

    private PayFastProvider payFastProvider;
    static final String TEST_MERCHANT_ID = "10000100";
    static final String TEST_MERCHANT_KEY = "46f0cd694581a";
    static final String TEST_PASSPHRASE = "configured-passphrase";

    @BeforeEach
    public void init() {
        payFastProvider = new PayFastProvider.PayFastProviderBuilder(TEST_MERCHANT_ID, TEST_MERCHANT_KEY, TEST_PASSPHRASE)
                .emailAddress("joe@soap.net")
                .lastName("Soap")
                .firstName("Joe")
                .returnUrl("http://localhost.heroku.com/return")
                .cancelUrl("http://localhost.heroku.com/cancel")
                .notifyUrl("http://localhost.heroku.com/notify")
                .paymentId("MAG-SUB-102")
                .amount(34)
                .itemName("Popular Mechanics")
                .itemDescription("Annual Subscription of Popular Mechanics")
                .customStr1("MAG-SUB-102")
                .subscriptionType(1)
                .recurringAmount(34)
                .frequency(3)
                .cycles(12)
                .build();
    }

    @Test
    void testFieldSortOrderByPosition() {
        Map<Integer, ElementKeyValuePair> sortedMemberList = PayFastProviderUtility
                .sortElementsByPlacement(payFastProvider);

        Assertions.assertEquals("merchant_id", sortedMemberList.get(1).key());
    }

    @Test
    void testSignatureGeneration() throws NoSuchAlgorithmException {
        String signature = PayFastProviderUtility.generateSecuritySignature(payFastProvider);

        Assertions.assertTrue(signature.length() > 31);
    }
}

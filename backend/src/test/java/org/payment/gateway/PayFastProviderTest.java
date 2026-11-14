package org.payment.gateway;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
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

    @Test
    void passphraseIsUsedForTheSignatureButNeverSentInTheForm() {
        Map<Integer, ElementKeyValuePair> signatureElements = PayFastProviderUtility.sortElementsByPlacement(payFastProvider);
        Map<Integer, ElementKeyValuePair> formElements = PayFastProviderUtility.sortFormElementsByPlacement(payFastProvider);

        Assertions.assertTrue(signatureElements.values().stream().anyMatch(e -> e.key().equals("passphrase")));
        Assertions.assertTrue(formElements.values().stream().noneMatch(e -> e.key().equals("passphrase")));
        Assertions.assertTrue(formElements.values().stream().noneMatch(e -> TEST_PASSPHRASE.equals(e.value())));
        Assertions.assertEquals(signatureElements.size() - 1, formElements.size());
    }

    @Test
    void itnSignatureKeepsReceivedOrderAndEmptyValuesSkipsSignatureAndAppendsPassphrase()
            throws NoSuchAlgorithmException {
        Map<String, String> itn = new LinkedHashMap<>();
        itn.put("m_payment_id", "abc");
        itn.put("item_name", "Garden and Home");
        itn.put("custom_str2", "");
        itn.put("amount_gross", "43.50");
        itn.put("signature", "ignored");

        String expected = md5("m_payment_id=abc&item_name=Garden+and+Home&custom_str2=&amount_gross=43.50"
                + "&passphrase=" + TEST_PASSPHRASE);

        Assertions.assertEquals(expected, PayFastProviderUtility.generateItnSignature(itn, TEST_PASSPHRASE));
        Assertions.assertEquals("m_payment_id=abc&item_name=Garden+and+Home&custom_str2=&amount_gross=43.50",
                PayFastProviderUtility.buildItnParameterString(itn));
    }

    static String md5(String input) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
        return String.format("%032x", new BigInteger(1, digest));
    }
}

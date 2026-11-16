package org.payment.gateway;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
                .amount(new BigDecimal("34"))
                .itemName("Popular Mechanics")
                .itemDescription("Annual Subscription of Popular Mechanics")
                .customStr1("MAG-SUB-102")
                .subscriptionType(1)
                .recurringAmount(new BigDecimal("34"))
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

    @Test
    void amountsAreSentWithTwoDecimalsAndUnsetCustomIntIsLeftOut() {
        Map<Integer, ElementKeyValuePair> formElements = PayFastProviderUtility.sortFormElementsByPlacement(payFastProvider);

        Assertions.assertEquals("34.00", valueOf(formElements, "amount"));
        Assertions.assertEquals("34.00", valueOf(formElements, "recurring_amount"));
        Assertions.assertNull(valueOf(formElements, "custom_int1"));
    }

    @Test
    void emailAndCellNumberAreBothKeptInPayFastOrder() {
        PayFastProvider withCell = new PayFastProvider.PayFastProviderBuilder(TEST_MERCHANT_ID, TEST_MERCHANT_KEY, null)
                .emailAddress("joe@soap.net")
                .cellNumber("0821234567")
                .paymentId("ref")
                .build();

        java.util.List<String> keys = PayFastProviderUtility.sortFormElementsByPlacement(withCell).values().stream()
                .map(ElementKeyValuePair::key).toList();

        Assertions.assertTrue(keys.indexOf("email_address") >= 0);
        Assertions.assertEquals(keys.indexOf("email_address") + 1, keys.indexOf("cell_number"));
        Assertions.assertEquals(keys.indexOf("cell_number") + 1, keys.indexOf("m_payment_id"));
    }

    @Test
    void valuesAreEncodedLikePhpUrlencode() {
        Assertions.assertEquals("Garden+%26+Home%2A+%7E+-_.", PayFastProviderUtility.encode("Garden & Home* ~ -_."));
        Assertions.assertEquals("http%3A%2F%2Flocalhost%3A8080%2Fnotify", PayFastProviderUtility.encode("http://localhost:8080/notify"));
    }

    @Test
    void itnBodyIsParsedInReceivedOrderKeepingEmptyFieldsAndDecodingValues() {
        Map<String, String> fields = PayFastProviderUtility.parseItnBody(
                "m_payment_id=abc&item_name=Garden+%26+Home&custom_str2=&amount_gross=43.50&flag&m_payment_id=second");

        Assertions.assertEquals(java.util.List.of("m_payment_id", "item_name", "custom_str2", "amount_gross", "flag"),
                java.util.List.copyOf(fields.keySet()));
        Assertions.assertEquals("abc", fields.get("m_payment_id")); //first value wins
        Assertions.assertEquals("Garden & Home", fields.get("item_name"));
        Assertions.assertEquals("", fields.get("custom_str2"));
        Assertions.assertEquals("", fields.get("flag"));
    }

    private static String valueOf(Map<Integer, ElementKeyValuePair> elements, String key) {
        return elements.values().stream().filter(e -> e.key().equals(key))
                .map(e -> String.valueOf(e.value())).findFirst().orElse(null);
    }

    static String md5(String input) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
        return String.format("%032x", new BigInteger(1, digest));
    }
}

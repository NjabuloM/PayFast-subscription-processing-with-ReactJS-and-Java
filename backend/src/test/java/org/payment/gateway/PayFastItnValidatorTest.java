package org.payment.gateway;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

class PayFastItnValidatorTest {

    static final String MERCHANT_ID = "10000100";
    static final String PASSPHRASE = "configured-passphrase";

    private final PayFastItnValidator validator =
            new PayFastItnValidator(MERCHANT_ID, PASSPHRASE, "sandbox.payfast.co.za", false);
    private Map<String, String> itn;

    @BeforeEach
    void init() throws NoSuchAlgorithmException {
        itn = new LinkedHashMap<>();
        itn.put("m_payment_id", "ref-1");
        itn.put("pf_payment_id", "1089250");
        itn.put("payment_status", "COMPLETE");
        itn.put("item_name", "CAR");
        itn.put("amount_gross", "44.90");
        itn.put("merchant_id", MERCHANT_ID);
        itn.put("token", "dc0521d3-55fe-269b-fa00-b647310d760f");
        itn.put("signature", PayFastProviderUtility.generateItnSignature(itn, PASSPHRASE));
    }

    @Test
    void acceptsCorrectlySignedNotification() {
        Assertions.assertTrue(validator.verifyLocally(itn, "127.0.0.1").valid());
    }

    @Test
    void rejectsTamperedAmount() {
        itn.put("amount_gross", "1.00");
        PayFastItnValidator.Result result = validator.verifyLocally(itn, "127.0.0.1");
        Assertions.assertFalse(result.valid());
        Assertions.assertEquals("signature mismatch", result.reason());
    }

    @Test
    void rejectsSignatureMadeWithoutThePassphrase() throws NoSuchAlgorithmException {
        itn.put("signature", PayFastProviderUtility.generateItnSignature(itn, null));
        Assertions.assertFalse(validator.verifyLocally(itn, "127.0.0.1").valid());
    }

    @Test
    void rejectsMissingSignature() {
        itn.remove("signature");
        Assertions.assertFalse(validator.verifyLocally(itn, "127.0.0.1").valid());
    }

    @Test
    void rejectsOtherMerchant() throws NoSuchAlgorithmException {
        itn.remove("signature");
        itn.put("merchant_id", "99999999");
        itn.put("signature", PayFastProviderUtility.generateItnSignature(itn, PASSPHRASE));
        PayFastItnValidator.Result result = validator.verifyLocally(itn, "127.0.0.1");
        Assertions.assertFalse(result.valid());
        Assertions.assertEquals("merchant_id does not match", result.reason());
    }

    @Test
    void rejectsNonPayFastSourceWhenSourceCheckIsOn() {
        PayFastItnValidator strict = new PayFastItnValidator(MERCHANT_ID, PASSPHRASE, "sandbox.payfast.co.za", true);
        Assertions.assertFalse(strict.verifyLocally(itn, "203.0.113.7").valid()); //TEST-NET-3, never PayFast
    }
}

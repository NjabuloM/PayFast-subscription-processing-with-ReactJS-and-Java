package dev.njabulo.spaza.shop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

/**
 * PayFast settings. Values are bound from application.properties, which in turn reads the environment variables
 * MERCHANT_ID, MERCHANT_KEY, PASS_PHRASE, RETURN_URL, CANCEL_URL, NOTIFY_URL, PAYFAST_SANDBOX, ...
 *
 * @param sandbox          true to talk to sandbox.payfast.co.za, false for the live www.payfast.co.za
 * @param verifySourceIp   reject ITNs that don't come from a PayFast IP address
 * @param verifyWithServer post each ITN back to PayFast to confirm it is genuine
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "payfast")
public record PayFastProperties(String merchantId,
                                String merchantKey,
                                String passPhrase,
                                String returnUrl,
                                String cancelUrl,
                                String notifyUrl,
                                boolean sandbox,
                                boolean verifySourceIp,
                                boolean verifyWithServer) {

    public PayFastProperties {
        // Blank values (e.g. an unset env var) are treated as "not configured", so they are left out of the
        // form and the signature instead of being sent as empty fields.
        merchantId = blankToNull(merchantId);
        merchantKey = blankToNull(merchantKey);
        passPhrase = blankToNull(passPhrase);
        returnUrl = blankToNull(returnUrl);
        cancelUrl = blankToNull(cancelUrl);
        notifyUrl = blankToNull(notifyUrl);
    }

    public String host() {
        return sandbox ? "sandbox.payfast.co.za" : "www.payfast.co.za";
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}

package org.payment.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Performs the security checks PayFast asks merchants to run on every ITN (Instant Transaction Notification)
 * before trusting it. See https://developers.payfast.co.za/docs#step_4_confirm_payment
 * <ol>
 *     <li>{@link #verifyLocally}: the signature matches, the merchant id is ours, and the request came from
 *     a PayFast server</li>
 *     <li>{@link #confirmWithServer}: PayFast itself confirms it sent this exact notification</li>
 * </ol>
 * The remaining check, that the amount paid matches what was expected, needs order data and is left to the caller.
 */
public class PayFastItnValidator {
    private static final Logger LOG = LoggerFactory.getLogger(PayFastItnValidator.class);

    static final List<String> PAYFAST_HOSTS = List.of(
            "www.payfast.co.za", "sandbox.payfast.co.za", "w1w.payfast.co.za", "w2w.payfast.co.za");

    private final String merchantId;
    private final String passPhrase;
    private final String payFastHost;
    private final boolean verifySourceIp;
    private final HttpClient httpClient;

    public PayFastItnValidator(String merchantId, String passPhrase, String payFastHost, boolean verifySourceIp) {
        this.merchantId = merchantId;
        this.passPhrase = passPhrase;
        this.payFastHost = payFastHost;
        this.verifySourceIp = verifySourceIp;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public record Result(boolean valid, String reason) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result fail(String reason) {
            return new Result(false, reason);
        }
    }

    /**
     * Checks that need no call to PayFast.
     *
     * @param receivedFields the ITN fields, in the order PayFast sent them
     * @param remoteAddress  the IP address the notification came from
     */
    public Result verifyLocally(Map<String, String> receivedFields, String remoteAddress) {
        String receivedSignature = receivedFields.get("signature");
        if (receivedSignature == null || receivedSignature.isBlank()) {
            return Result.fail("missing signature");
        }
        try {
            String expectedSignature = PayFastProviderUtility.generateItnSignature(receivedFields, passPhrase);
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                    receivedSignature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
                return Result.fail("signature mismatch");
            }
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Failed to compute ITN signature", e);
            return Result.fail("signature could not be computed");
        }

        if (!Objects.equals(merchantId, receivedFields.get("merchant_id"))) {
            return Result.fail("merchant_id does not match");
        }

        if (verifySourceIp && !isPayFastAddress(remoteAddress)) {
            return Result.fail("source address " + remoteAddress + " is not a PayFast server");
        }

        return Result.ok();
    }

    /**
     * Posts the notification back to PayFast, which answers VALID only if it really sent it.
     *
     * @param receivedFields the ITN fields, in the order PayFast sent them
     */
    public Result confirmWithServer(Map<String, String> receivedFields) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + payFastHost + "/eng/query/validate"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        PayFastProviderUtility.buildItnParameterString(receivedFields)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if ("VALID".equals(response.body().trim())) {
                return Result.ok();
            }
            return Result.fail("PayFast did not confirm the notification (HTTP " + response.statusCode() + ")");
        } catch (IOException e) {
            LOG.error("Could not reach PayFast to confirm the notification", e);
            return Result.fail("PayFast could not be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("interrupted while confirming with PayFast");
        }
    }

    private boolean isPayFastAddress(String remoteAddress) {
        Set<String> validAddresses = new HashSet<>();
        for (String host : PAYFAST_HOSTS) {
            try {
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    validAddresses.add(address.getHostAddress());
                }
            } catch (UnknownHostException e) {
                LOG.warn("Could not resolve {}", host);
            }
        }
        return validAddresses.contains(remoteAddress);
    }
}

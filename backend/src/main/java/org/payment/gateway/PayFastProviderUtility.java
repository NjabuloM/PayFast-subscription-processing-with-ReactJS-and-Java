package org.payment.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * A helper utility class responsible for interrogating payfast specific annotations in order to create a security
 * signature in the form that is required by the provider
 *
 */
public class PayFastProviderUtility {
    static final Logger LOG = LoggerFactory.getLogger(PayFastProviderUtility.class);

    static final String MESSAGE_DIGEST_ALGORITHM = "MD5";
    static final String PASSPHRASE_LABEL = "passphrase";
    public static final int PAYFAST_SUBSCRIPTION_TYPE_FLAG = 1;

    private PayFastProviderUtility() {

    }

    /**
     * This method takes in an object containing values and iterate through them so that it can arrange these values
     * according to the ordering annotation provided for each field. Thereafter, this ordered Map will be used
     * to generate a signature.
     * <p>
     * NB: the result includes signature-only fields such as the passphrase. Never send it to a client; use
     * {@link #sortFormElementsByPlacement(PayFastProvider)} for that.
     *
     * @param payFastProvider an object containing the required fields and their details
     * @return a Map object ordered by key
     */
    public static Map<Integer, ElementKeyValuePair> sortElementsByPlacement(PayFastProvider payFastProvider) {
        return sortElementsByPlacement(payFastProvider, true);
    }

    /**
     * Same as {@link #sortElementsByPlacement(PayFastProvider)}, but leaves out fields that are only used to compute
     * the signature (e.g. the passphrase). This is the set of fields that is safe to post to PayFast via the browser.
     *
     * @param payFastProvider an object containing the required fields and their details
     * @return a Map object ordered by key, without signature-only fields
     */
    public static Map<Integer, ElementKeyValuePair> sortFormElementsByPlacement(PayFastProvider payFastProvider) {
        return sortElementsByPlacement(payFastProvider, false);
    }

    private static Map<Integer, ElementKeyValuePair> sortElementsByPlacement(PayFastProvider payFastProvider,
                                                                          boolean includeSignatureOnly) {
        Class<?> objectClassDefinition = payFastProvider.getClass();
        HashMap<Integer, ElementKeyValuePair> providerElementMap = new HashMap<>();

        for (Field memberField : objectClassDefinition.getDeclaredFields()) {
            GatewayElement element = memberField.getAnnotation(GatewayElement.class);
            if (element == null || (element.signatureOnly() && !includeSignatureOnly)) {
                continue;
            }
            Object fieldValue = getValue(memberField, payFastProvider);
            if (fieldValue != null) {
                providerElementMap.put(element.placement(),
                        new ElementKeyValuePair(getLabel(memberField), fieldValue));
            }
        }

        return new TreeMap<>(providerElementMap); //We use a TreeMap to get the ordering by key
    }

    /**
     * Method that reads the label assigned to the member field being passed in
     *
     * @param memberField a class field member whose annotation label is being queried
     * @return a label defined for the field member
     */
    private static String getLabel(Field memberField) {
        String label = memberField.getAnnotation(GatewayElement.class).label();
        if (label.isBlank()) {
            return memberField.getName();
        } else {
            return label;
        }
    }

    /**
     * Given the field member and the object where it is to be extracted from, then return the actual value held by
     * the field member.
     *
     * @param memberField a class field member whose value is being queried
     * @param provider the parent object containing all field members
     * @return value of the member field
     */
    private static Object getValue(Field memberField, PayFastProvider provider) {
        memberField.setAccessible(true);
        try {
            return memberField.get(provider);
        } catch (IllegalAccessException e) {
            LOG.error("Field value of {} is inaccessible due to access control", memberField.getName());
        }
        return null;
    }

    /**
     * We'll concatenate non-empty name value pairs here, as needed for the signature
     * NB: It's important that the specified order is maintained, as shown in the
     * docs (https://developers.payfast.co.za/docs)
     *
     * @return An MD5 hashed ampersand separated value pair
     */
    public static String generateSecuritySignature(PayFastProvider provider) throws NoSuchAlgorithmException {
        Map<Integer, ElementKeyValuePair> sortedValuePairMap = sortElementsByPlacement(provider);

        String joinedNameValuePair = concatenateNonEmptyNameValuePairs(sortedValuePairMap);

        return computeHashValue(joinedNameValuePair);
    }

    /**
     * Builds the parameter string for an ITN (Instant Transaction Notification) as PayFast describes it: every
     * received field, in the order received, except {@code signature}. Empty values are kept.
     * This is also the exact payload that gets posted back to PayFast for server-side validation.
     *
     * @param receivedFields the ITN fields, in the order PayFast sent them
     * @return a string of key=value pairs joined with '&amp;'
     */
    public static String buildItnParameterString(Map<String, String> receivedFields) {
        StringJoiner joiner = new StringJoiner("&");
        receivedFields.forEach((key, value) -> {
            if (!"signature".equals(key)) {
                joiner.add(key + "=" + encode(value == null ? "" : value));
            }
        });
        return joiner.toString();
    }

    /**
     * Parses a raw application/x-www-form-urlencoded ITN body, keeping the fields in the exact order PayFast sent
     * them (the Servlet API makes no such promise for request parameters) and keeping empty fields.
     * If a field appears more than once, the first value wins.
     *
     * @param body the raw request body, e.g. {@code m_payment_id=abc&item_name=Garden+and+Home&custom_str2=}
     * @return the decoded fields, in received order
     */
    public static Map<String, String> parseItnBody(String body) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return fields;
        }
        for (String pair : body.trim().split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String key = URLDecoder.decode(separator < 0 ? pair : pair.substring(0, separator), StandardCharsets.UTF_8);
            String value = separator < 0 ? "" : URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            fields.putIfAbsent(key, value);
        }
        return fields;
    }

    /**
     * Computes the signature PayFast should have sent with an ITN.
     *
     * @param receivedFields the ITN fields, in the order PayFast sent them
     * @param passPhrase the merchant passphrase, or null/blank if none is configured
     * @return the expected MD5 signature
     */
    public static String generateItnSignature(Map<String, String> receivedFields, String passPhrase)
            throws NoSuchAlgorithmException {
        String parameterString = buildItnParameterString(receivedFields);
        if (passPhrase != null && !passPhrase.isBlank()) {
            parameterString += "&" + PASSPHRASE_LABEL + "=" + encode(passPhrase);
        }
        return computeHashValue(parameterString);
    }

    /**
     * The idea here is to take an ordered Map of key-value-pair and join them together such that the
     * resulting string looks like so: key1=value1&key2=value2... and so on.
     *
     * @param sortedValuePairMap a key value pair representation of values to be joined together
     * @return a string representation of a key-value-pair joined together with an '&'
     */
    private static String concatenateNonEmptyNameValuePairs(Map<Integer, ElementKeyValuePair> sortedValuePairMap) {
        StringJoiner joiner = new StringJoiner("&");

        for (Map.Entry<Integer, ElementKeyValuePair> element : sortedValuePairMap.entrySet()) {
            if (null != element.getValue().value()) {
                joiner.add(element.getValue().key() + "=" + encode(String.valueOf(element.getValue().value())));
            }
        }

        return joiner.toString();
    }

    /**
     * URL-encodes a value the way PayFast's signature rules expect (PHP's urlencode): spaces become '+', hex digits
     * are upper case, and only letters, digits and "-_." are left as they are. Java's URLEncoder matches this except
     * that it leaves '*' unencoded, so that one is fixed up here.
     */
    static String encode(String value) {
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8).replace("*", "%2A");
    }

    /**
     * Performs a hash computation on the provided string value
     *
     * @param joinedNameValuePair a string of key-value-pair joined together with an '&'
     * @return a hashed value of the passed in string
     * @throws NoSuchAlgorithmException when the necessary algorithm is not found in the environment
     */
    private static String computeHashValue(String joinedNameValuePair) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM);
        messageDigest.update(joinedNameValuePair.getBytes(StandardCharsets.UTF_8));
        //A bit of MacGyver-ring with the byte array conversion. Same can be achieved with org.apache.commons
        BigInteger hashed = new BigInteger(1, messageDigest.digest());
        StringBuilder outcome = new StringBuilder(hashed.toString(16));
        while (outcome.length() < 32) {
            outcome.insert(0, "0");
        }
        return outcome.toString();
    }
}

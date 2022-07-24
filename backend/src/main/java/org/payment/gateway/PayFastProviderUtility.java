package org.payment.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * A helper utility class responsible for interrogating payfast specific annotations in order to create a security
 * signature in the form that is required by the provider
 *
 */
public class PayFastProviderUtility {
    static final Logger LOG = LoggerFactory.getLogger(PayFastProviderUtility.class);

    static final String MESSAGE_DIGEST_ALGORITHM = "MD5";
    public static final int PAYFAST_SUBSCRIPTION_TYPE_FLAG = 1;

    private PayFastProviderUtility() {

    }

    /**
     * This method takes in an object containing values and iterate through them so that it can arrange these values
     * according to the ordering annotation provided for each field. Thereafter, this ordered Map will be used
     * to generate a signature.
     *
     * @param payFastProvider an object containing the required fields and their details
     * @return a Map object ordered by key
     */
    public static Map<Integer, ElementKeyValuePair> sortElementsByPlacement(PayFastProvider payFastProvider) {
        Class<?> objectClassDefinition = payFastProvider.getClass();
        HashMap<Integer, ElementKeyValuePair> providerElementMap = new HashMap<>();

        for (Field memberField : objectClassDefinition.getDeclaredFields()) {
            if (memberField.isAnnotationPresent(GatewayElement.class)) {
                Object fieldValue = getValue(memberField, payFastProvider);
                if (fieldValue != null) {
                    providerElementMap.put(getPosition(memberField),
                            new ElementKeyValuePair(getLabel(memberField), fieldValue));
                }
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
     * Reads the position that this field should hold before a signature is generated.
     *
     * @param memberField a class field member whose position is being queried
     * @return a position assigned for the field member
     */
    private static int getPosition(Field memberField) {
        return memberField.getAnnotation(GatewayElement.class).placement();
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
     * The idea here is to take an ordered Map of key-value-pair and join them together such that the
     * resulting string looks like so: key1=value1&key2=value2... and so on.
     *
     * @param sortedValuePairMap a key value pair representation of values to be joined together
     * @return a string representation of a key-value-pair joined together with an '&'
     */
    private static String concatenateNonEmptyNameValuePairs(Map<Integer, ElementKeyValuePair> sortedValuePairMap) {
        StringBuilder outputString = new StringBuilder();

        for (Map.Entry<Integer, ElementKeyValuePair> element : sortedValuePairMap.entrySet()) {
            if (null != element.getValue().value()) {
                outputString.append(element.getValue().key())
                    .append("=")
                    .append(URLEncoder.encode(String.valueOf(element.getValue().value()),
                            StandardCharsets.UTF_8))
                    .append("&");
            }
        }

        outputString.deleteCharAt(outputString.length() - 1); //Remove the trailing '&' sign
        return outputString.toString();
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
        messageDigest.update(joinedNameValuePair.getBytes());
        //A bit of MacGyver-ring with the byte array conversion. Same can be achieved with org.apache.commons
        BigInteger hashed = new BigInteger(1, messageDigest.digest());
        StringBuilder outcome = new StringBuilder(hashed.toString(16));
        while (outcome.length() < 32) {
            outcome.insert(0, "0");
        }
        return outcome.toString();
    }
}

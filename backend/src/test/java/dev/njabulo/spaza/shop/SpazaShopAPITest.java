package dev.njabulo.spaza.shop;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.njabulo.spaza.shop.common.FrequencyType;
import dev.njabulo.spaza.shop.common.SubscriptionStatus;
import dev.njabulo.spaza.shop.persistence.dao.SubscriptionDAO;
import dev.njabulo.spaza.shop.persistence.model.PaymentNotification;
import dev.njabulo.spaza.shop.persistence.model.Subscription;
import dev.njabulo.spaza.shop.persistence.service.ShopService;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.payment.gateway.PayFastProviderUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SpazaDemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "payfast.merchant-id=" + SpazaShopAPITest.MERCHANT_ID,
                "payfast.merchant-key=46f0cd694581a",
                "payfast.pass-phrase=" + SpazaShopAPITest.PASSPHRASE,
                "payfast.notify-url=http://localhost:8080/shop/payment/notification",
                // The test posts its own ITNs from localhost, without a round trip to PayFast
                "payfast.verify-source-ip=false",
                "payfast.verify-with-server=false"
        })
class SpazaShopAPITest {

    static final String MERCHANT_ID = "10000100";
    static final String PASSPHRASE = "test-passphrase";
    private static final String HOST ="http://localhost:8080";
    // Read decimals exactly (44.90 stays 44.90), so the tests see what the API really sent
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    // Seed data (data.sql): magazine 1 is CAR at R44.90 a month, magazine 5 is GARDEN AND HOME at R43.50
    private static final long CAR = 1;
    private static final long GARDEN_AND_HOME = 5;

    @Autowired
    private SubscriptionDAO subscriptionDAO;

    @Autowired
    private ShopService shopService;

    @Test
    void givenACatalogueRequest_whenAListingIssued_thenAnHttpOkReplyIsReceived() throws IOException {
        //Given
        final HttpUriRequest peruseRequest = new HttpGet(HOST + "/shop/newsstand");

        //When
        HttpResponse listingResponse = HttpClientBuilder.create().build().execute(peruseRequest);

        //Then
        Assertions.assertEquals(HttpStatus.SC_OK, listingResponse.getStatusLine().getStatusCode());
    }

    @Test
    void givenACatalogueRequest_whenAListingIssued_theTitlesShouldContainHouseAndHome() throws IOException {
        //Given
        final HttpUriRequest peruseRequest = new HttpGet(HOST + "/shop/newsstand");

        //When
        HttpResponse listingResponse = HttpClientBuilder.create().build().execute(peruseRequest);

        //Then
        String flattenedListing = EntityUtils.toString(listingResponse.getEntity());
        Assertions.assertTrue(flattenedListing.toLowerCase().contains("garden and home"));
    }

    @Test
    void givenASubscription_whenTaken_anAcceptedReplyIsReceived() throws IOException {
        //Given
        final HttpPost httpPost = new HttpPost(HOST + "/shop/newsstand/subscribe");
        httpPost.setHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());
        StringEntity requestEntity = new StringEntity("""
                {
                    "magazineId": 1,
                    "emailAddress": "joe@soap.net",
                    "paymentFrequency": "MONTHLY"
                }
                """);
        httpPost.setEntity(requestEntity);

        //When
        HttpResponse subscribeResponse = HttpClientBuilder.create().build().execute(httpPost);

        //Then
        Assertions.assertEquals(HttpStatus.SC_ACCEPTED, subscribeResponse.getStatusLine().getStatusCode());
    }

    // ---------------------------------------------------------------- plans and checkout

    @Test
    void givenAMagazine_whenPlansRequested_thenServerPricedMonthlyAndAnnualOptionsAreReturned() throws IOException {
        HttpResponse response = HttpClientBuilder.create().build().execute(new HttpGet(HOST + "/shop/newsstand/magazine/1/plans"));

        Assertions.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        JsonNode plans = JSON.readTree(EntityUtils.toString(response.getEntity()));
        Assertions.assertEquals("MONTHLY", plans.get(0).get("frequency").asText());
        Assertions.assertEquals(0, new BigDecimal("44.90").compareTo(plans.get(0).get("amount").decimalValue()));
        Assertions.assertEquals("ANNUALLY", plans.get(1).get("frequency").asText());
        Assertions.assertEquals(0, new BigDecimal("485.00").compareTo(plans.get(1).get("amount").decimalValue()));
    }

    @Test
    void givenMonthlyCheckout_thenASignedMonthlyFormWithoutThePassphraseIsReturned() throws IOException {
        //When
        HttpResponse checkoutResponse = checkout(CAR, "MONTHLY");

        //Then
        Assertions.assertEquals(HttpStatus.SC_ACCEPTED, checkoutResponse.getStatusLine().getStatusCode());
        String body = EntityUtils.toString(checkoutResponse.getEntity());
        JsonNode checkoutForm = JSON.readTree(body);
        Map<String, String> form = toMap(checkoutForm.get("fields"));

        Assertions.assertEquals("https://sandbox.payfast.co.za/eng/process", checkoutForm.get("action").asText());
        Assertions.assertEquals(32, form.get("signature").length());
        Assertions.assertFalse(form.containsKey("passphrase"));
        Assertions.assertFalse(body.contains(PASSPHRASE));
        Assertions.assertEquals(MERCHANT_ID, form.get("merchant_id"));
        Assertions.assertEquals("44.90", form.get("amount"));
        Assertions.assertEquals("44.90", form.get("recurring_amount"));
        Assertions.assertEquals("3", form.get("frequency"));
        Assertions.assertEquals("12", form.get("cycles"));
        Assertions.assertFalse(form.containsKey("custom_int1"));
        //Every field value is a JSON string, so the browser posts exactly what was signed
        checkoutForm.get("fields").forEach(field -> Assertions.assertTrue(field.get("value").isTextual(),
                field.get("name").asText() + " should be sent as a string"));

        //The checkout created a PENDING subscription, whose reference is sent as m_payment_id
        Subscription saved = subscriptionDAO.findByReference(form.get("m_payment_id")).orElseThrow();
        Assertions.assertEquals(SubscriptionStatus.PENDING, saved.getStatus());
        Assertions.assertEquals(FrequencyType.MONTHLY, saved.getPaymentFrequency());
        Assertions.assertEquals(new BigDecimal("44.90"), saved.getAmount());
        Assertions.assertNotNull(saved.getCreateDate());
    }

    @Test
    void givenAnnualCheckout_thenPayFastBillsTheDiscountedYearlyAmountAnnually() throws IOException {
        HttpResponse checkoutResponse = checkout(CAR, "ANNUALLY");

        Assertions.assertEquals(HttpStatus.SC_ACCEPTED, checkoutResponse.getStatusLine().getStatusCode());
        Map<String, String> form = toMap(JSON.readTree(EntityUtils.toString(checkoutResponse.getEntity())).get("fields"));
        Assertions.assertEquals("485.00", form.get("amount"));
        Assertions.assertEquals("485.00", form.get("recurring_amount"));
        Assertions.assertEquals("6", form.get("frequency"));
        Assertions.assertEquals("0", form.get("cycles"));
        Assertions.assertEquals("Annual subscription to CAR", form.get("item_description"));

        Subscription saved = subscriptionDAO.findByReference(form.get("m_payment_id")).orElseThrow();
        Assertions.assertEquals(FrequencyType.ANNUALLY, saved.getPaymentFrequency());
        Assertions.assertEquals(new BigDecimal("485.00"), saved.getAmount());
    }

    @Test
    void givenCheckoutWithoutEmail_thenABadRequestIsReturned() throws IOException {
        HttpResponse response = postJson("/shop/newsstand/checkout", """
                { "magazineId": 1, "firstName": "Joe", "lastName": "Soap", "paymentFrequency": "MONTHLY" }
                """);

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    @Test
    void givenCheckoutWithoutABillingOption_thenABadRequestIsReturned() throws IOException {
        HttpResponse response = postJson("/shop/newsstand/checkout", """
                { "magazineId": 1, "emailAddress": "joe@soap.net" }
                """);

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    // ---------------------------------------------------------------- notifications (ITN)

    /**
     * A notification body written out by hand the way PayFast sends it (form-encoded, in PayFast's field order,
     * with empty fields and characters that need encoding), signed independently of the code under test:
     * MD5 of the body without the signature, plus "&passphrase=...".
     */
    @Test
    void givenARawPayFastShapedItnBody_thenItsSignatureIsAcceptedAndTheSubscriptionActivated() throws Exception {
        String reference = referenceFromCheckout(checkout(GARDEN_AND_HOME, "MONTHLY")); //R43.50

        String unsignedBody = "m_payment_id=" + reference
                + "&pf_payment_id=2201457&payment_status=COMPLETE"
                + "&item_name=GARDEN+AND+HOME&item_description=Monthly+subscription+to+GARDEN+%26+HOME+%2A+deals"
                + "&amount_gross=43.50&amount_fee=-2.24&amount_net=41.26"
                + "&custom_str1=20004-7&custom_str2=&custom_str3=&custom_str4=&custom_str5="
                + "&custom_int1=&custom_int2=&custom_int3=&custom_int4=&custom_int5="
                + "&name_first=Th%C3%A9o&name_last=O%27Brien&email_address=theo%2Btest%40example.com"
                + "&merchant_id=" + MERCHANT_ID
                + "&token=a5f8a1c2-6d0e-4b9a-8f53-2b7c1d9e0f11&billing_date=2026-11-16";
        String signature = md5(unsignedBody + "&passphrase=" + PASSPHRASE);

        HttpResponse response = postRawItn(unsignedBody + "&signature=" + signature);

        Assertions.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Subscription saved = subscriptionDAO.findByReference(reference).orElseThrow();
        Assertions.assertEquals(SubscriptionStatus.ACTIVE, saved.getStatus());
        Assertions.assertEquals("a5f8a1c2-6d0e-4b9a-8f53-2b7c1d9e0f11", saved.getPayFastToken());
    }

    @Test
    void givenTheSameFieldsInADifferentOrder_thenTheSignatureNoLongerMatches() throws Exception {
        String reference = referenceFromCheckout(checkout(GARDEN_AND_HOME, "MONTHLY"));

        String signedOrder = "m_payment_id=" + reference + "&payment_status=COMPLETE&amount_gross=43.50&merchant_id=" + MERCHANT_ID;
        String receivedOrder = "payment_status=COMPLETE&m_payment_id=" + reference + "&amount_gross=43.50&merchant_id=" + MERCHANT_ID;

        HttpResponse response = postRawItn(receivedOrder + "&signature=" + md5(signedOrder + "&passphrase=" + PASSPHRASE));

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    @Test
    void givenAGenuineCompleteItn_thenTheSubscriptionIsActivatedTheTokenStoredAndThePaymentRecorded() throws Exception {
        String reference = referenceFromCheckout(checkout(CAR, "MONTHLY"));

        Map<String, String> itn = itn(reference, "1001", "COMPLETE", "44.90");
        HttpResponse response = postItn(itn);

        Assertions.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Subscription saved = subscriptionDAO.findByReference(reference).orElseThrow();
        Assertions.assertEquals(SubscriptionStatus.ACTIVE, saved.getStatus());
        Assertions.assertEquals(itn.get("token"), saved.getPayFastToken());
        List<PaymentNotification> history = shopService.getPaymentHistory(saved.getId());
        Assertions.assertEquals(1, history.size());
        Assertions.assertEquals("1001", history.get(0).getPfPaymentId());
        Assertions.assertEquals(new BigDecimal("44.90"), history.get(0).getAmountGross());
    }

    @Test
    void givenAnAnnualSubscription_thenOnlyTheAnnualAmountIsAccepted() throws Exception {
        String reference = referenceFromCheckout(checkout(CAR, "ANNUALLY"));

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST,
                postItn(itn(reference, "1101", "COMPLETE", "44.90")).getStatusLine().getStatusCode());
        Assertions.assertEquals(HttpStatus.SC_OK,
                postItn(itn(reference, "1102", "COMPLETE", "485.00")).getStatusLine().getStatusCode());
        Assertions.assertEquals(SubscriptionStatus.ACTIVE, subscriptionDAO.findByReference(reference).orElseThrow().getStatus());
    }

    @Test
    void givenAForgedItn_thenItIsRejectedAndNothingChanges() throws Exception {
        String reference = referenceFromCheckout(checkout(CAR, "MONTHLY"));

        Map<String, String> itn = itn(reference, "1201", "COMPLETE", "44.90");
        itn.put("signature", "0".repeat(32));
        HttpResponse response = postItn(itn);

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
        Subscription saved = subscriptionDAO.findByReference(reference).orElseThrow();
        Assertions.assertEquals(SubscriptionStatus.PENDING, saved.getStatus());
        Assertions.assertTrue(shopService.getPaymentHistory(saved.getId()).isEmpty());
    }

    @Test
    void givenACorrectlySignedItnWithTheWrongAmount_thenItIsRejected() throws Exception {
        String reference = referenceFromCheckout(checkout(CAR, "MONTHLY"));

        HttpResponse response = postItn(itn(reference, "1301", "COMPLETE", "1.00"));

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
        Assertions.assertEquals(SubscriptionStatus.PENDING,
                subscriptionDAO.findByReference(reference).orElseThrow().getStatus());
    }

    @Test
    void givenACancelledItnWithoutAnAmount_thenTheSubscriptionIsCancelled() throws Exception {
        String reference = referenceFromCheckout(checkout(CAR, "MONTHLY"));
        postItn(itn(reference, "1401", "COMPLETE", "44.90"));

        HttpResponse response = postItn(itn(reference, "1401", "CANCELLED", null));

        Assertions.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assertions.assertEquals(SubscriptionStatus.CANCELLED,
                subscriptionDAO.findByReference(reference).orElseThrow().getStatus());
    }

    @Test
    void givenTheSameNotificationTwice_thenTheSecondIsAcknowledgedButNotAppliedAgain() throws Exception {
        String reference = referenceFromCheckout(checkout(CAR, "MONTHLY"));
        Map<String, String> itn = itn(reference, "1501", "COMPLETE", "44.90");

        Assertions.assertEquals(HttpStatus.SC_OK, postItn(itn).getStatusLine().getStatusCode());
        Assertions.assertEquals(HttpStatus.SC_OK, postItn(itn).getStatusLine().getStatusCode());

        Subscription saved = subscriptionDAO.findByReference(reference).orElseThrow();
        Assertions.assertEquals(1, shopService.getPaymentHistory(saved.getId()).size());
    }

    @Test
    void givenAPaymentNotificationAfterCancellation_thenTheSubscriptionStaysCancelled() throws Exception {
        String reference = referenceFromCheckout(checkout(CAR, "MONTHLY"));
        postItn(itn(reference, "1601", "COMPLETE", "44.90"));
        postItn(itn(reference, "1601", "CANCELLED", null));

        //A late (or replayed) payment notification with a payment id we haven't seen yet
        HttpResponse response = postItn(itn(reference, "1602", "COMPLETE", "44.90"));

        Assertions.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Subscription saved = subscriptionDAO.findByReference(reference).orElseThrow();
        Assertions.assertEquals(SubscriptionStatus.CANCELLED, saved.getStatus());
        Assertions.assertEquals(3, shopService.getPaymentHistory(saved.getId()).size()); //still recorded
    }

    @Test
    void givenRecurringPayments_thenEachIsRecordedAndTheSubscriptionStaysActive() throws Exception {
        String reference = referenceFromCheckout(checkout(CAR, "MONTHLY"));

        postItn(itn(reference, "1701", "COMPLETE", "44.90"));
        postItn(itn(reference, "1702", "COMPLETE", "44.90"));
        postItn(itn(reference, "1703", "COMPLETE", "44.90"));

        Subscription saved = subscriptionDAO.findByReference(reference).orElseThrow();
        Assertions.assertEquals(SubscriptionStatus.ACTIVE, saved.getStatus());
        Assertions.assertEquals(List.of("1701", "1702", "1703"), shopService.getPaymentHistory(saved.getId())
                .stream().map(PaymentNotification::getPfPaymentId).toList());
    }

    @Test
    void givenAFailedFirstPayment_thenTheSubscriptionFails_butAFailedRecurringPaymentKeepsItActive() throws Exception {
        String first = referenceFromCheckout(checkout(CAR, "MONTHLY"));
        postItn(itn(first, "1801", "FAILED", "44.90"));
        Assertions.assertEquals(SubscriptionStatus.FAILED, subscriptionDAO.findByReference(first).orElseThrow().getStatus());

        String recurring = referenceFromCheckout(checkout(CAR, "MONTHLY"));
        postItn(itn(recurring, "1811", "COMPLETE", "44.90"));
        postItn(itn(recurring, "1812", "FAILED", "44.90"));
        Assertions.assertEquals(SubscriptionStatus.ACTIVE, subscriptionDAO.findByReference(recurring).orElseThrow().getStatus());
    }

    @Test
    void givenAnItnForAnUnknownReference_thenItIsRejected() throws Exception {
        HttpResponse response = postItn(itn(UUID.randomUUID().toString(), "1901", "COMPLETE", "44.90"));

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    // ---------------------------------------------------------------- helpers

    private HttpResponse checkout(long magazineId, String frequency) throws IOException {
        return postJson("/shop/newsstand/checkout", """
                {
                    "magazineId": %d,
                    "firstName": "Joe",
                    "lastName": "Soap",
                    "emailAddress": "joe@soap.net",
                    "paymentFrequency": "%s"
                }
                """.formatted(magazineId, frequency));
    }

    private static HttpResponse postJson(String path, String json) throws IOException {
        HttpPost post = new HttpPost(HOST + path);
        post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        return HttpClientBuilder.create().build().execute(post);
    }

    private static String referenceFromCheckout(HttpResponse checkoutResponse) throws IOException {
        JsonNode checkoutForm = JSON.readTree(EntityUtils.toString(checkoutResponse.getEntity()));
        return toMap(checkoutForm.get("fields")).get("m_payment_id");
    }

    /** An ITN shaped like PayFast's, signed with the test passphrase. A null amount leaves the amount fields out. */
    private static Map<String, String> itn(String reference, String pfPaymentId, String paymentStatus,
                                           String amountGross) throws NoSuchAlgorithmException {
        Map<String, String> itn = new LinkedHashMap<>();
        itn.put("m_payment_id", reference);
        itn.put("pf_payment_id", pfPaymentId);
        itn.put("payment_status", paymentStatus);
        itn.put("item_name", "CAR");
        itn.put("item_description", "Monthly subscription to CAR");
        if (amountGross != null) {
            itn.put("amount_gross", amountGross);
            itn.put("amount_fee", "-2.30");
            itn.put("amount_net", "42.60");
        }
        itn.put("custom_str1", "");
        itn.put("name_first", "Joe");
        itn.put("name_last", "Soap");
        itn.put("email_address", "joe@soap.net");
        itn.put("merchant_id", MERCHANT_ID);
        itn.put("token", "dc0521d3-55fe-269b-fa00-b647310d760f");
        itn.put("billing_date", "2026-11-16");
        itn.put("signature", PayFastProviderUtility.generateItnSignature(itn, PASSPHRASE));
        return itn;
    }

    private static HttpResponse postItn(Map<String, String> itn) throws IOException {
        List<NameValuePair> pairs = new ArrayList<>();
        itn.forEach((key, value) -> pairs.add(new BasicNameValuePair(key, value)));
        HttpPost post = new HttpPost(HOST + "/shop/payment/notification");
        post.setEntity(new UrlEncodedFormEntity(pairs, StandardCharsets.UTF_8));
        return HttpClientBuilder.create().build().execute(post);
    }

    private static HttpResponse postRawItn(String body) throws IOException {
        HttpPost post = new HttpPost(HOST + "/shop/payment/notification");
        post.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));
        return HttpClientBuilder.create().build().execute(post);
    }

    private static Map<String, String> toMap(JsonNode formFields) {
        Map<String, String> map = new LinkedHashMap<>();
        formFields.forEach(field -> map.put(field.get("name").asText(), field.get("value").asText()));
        return map;
    }

    private static String md5(String input) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
        return String.format("%032x", new BigInteger(1, digest));
    }
}

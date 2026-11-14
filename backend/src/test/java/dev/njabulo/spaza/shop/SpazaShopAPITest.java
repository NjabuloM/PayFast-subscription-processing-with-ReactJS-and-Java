package dev.njabulo.spaza.shop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.njabulo.spaza.shop.common.FrequencyType;
import dev.njabulo.spaza.shop.common.SubscriptionStatus;
import dev.njabulo.spaza.shop.persistence.dao.SubscriptionDAO;
import dev.njabulo.spaza.shop.persistence.model.Subscription;
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
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private SubscriptionDAO subscriptionDAO;

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

    @Test
    void givenSubscriptionDetails_whenCheckout_thenASignedFormWithoutThePassphraseIsReturned() throws IOException {
        //When
        HttpResponse checkoutResponse = checkout(1);

        //Then
        Assertions.assertEquals(HttpStatus.SC_ACCEPTED, checkoutResponse.getStatusLine().getStatusCode());
        String body = EntityUtils.toString(checkoutResponse.getEntity());
        Map<String, String> form = toMap(JSON.readTree(body));

        Assertions.assertEquals(32, form.get("signature").length());
        Assertions.assertFalse(form.containsKey("passphrase"));
        Assertions.assertFalse(body.contains(PASSPHRASE));
        Assertions.assertEquals(MERCHANT_ID, form.get("merchant_id"));

        //The checkout created a PENDING subscription, whose reference is sent as m_payment_id
        Subscription saved = subscriptionDAO.findByReference(form.get("m_payment_id")).orElseThrow();
        Assertions.assertEquals(SubscriptionStatus.PENDING, saved.getStatus());
        Assertions.assertEquals(FrequencyType.MONTHLY, saved.getPaymentFrequency());
        Assertions.assertNotNull(saved.getCreateDate());
    }

    @Test
    void givenCheckoutWithoutEmail_thenABadRequestIsReturned() throws IOException {
        final HttpPost httpCheckoutPost = new HttpPost(HOST + "/shop/newsstand/checkout");
        httpCheckoutPost.setEntity(new StringEntity("""
                { "magazineId": 1, "firstName": "Joe", "lastName": "Soap" }
                """, ContentType.APPLICATION_JSON));

        HttpResponse response = HttpClientBuilder.create().build().execute(httpCheckoutPost);

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
    }

    @Test
    void givenAGenuineCompleteItn_thenTheSubscriptionIsActivatedAndTheTokenStored() throws Exception {
        String reference = referenceFromCheckout(checkout(1)); //CAR, R44.90

        Map<String, String> itn = itn(reference, "COMPLETE", "44.90");
        HttpResponse response = postItn(itn);

        Assertions.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Subscription saved = subscriptionDAO.findByReference(reference).orElseThrow();
        Assertions.assertEquals(SubscriptionStatus.ACTIVE, saved.getStatus());
        Assertions.assertEquals(itn.get("token"), saved.getPayFastToken());
    }

    @Test
    void givenAForgedItn_thenItIsRejectedAndNothingChanges() throws Exception {
        String reference = referenceFromCheckout(checkout(1));

        Map<String, String> itn = itn(reference, "COMPLETE", "44.90");
        itn.put("signature", "0".repeat(32));
        HttpResponse response = postItn(itn);

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
        Assertions.assertEquals(SubscriptionStatus.PENDING,
                subscriptionDAO.findByReference(reference).orElseThrow().getStatus());
    }

    @Test
    void givenACorrectlySignedItnWithTheWrongAmount_thenItIsRejected() throws Exception {
        String reference = referenceFromCheckout(checkout(1));

        HttpResponse response = postItn(itn(reference, "COMPLETE", "1.00"));

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
        Assertions.assertEquals(SubscriptionStatus.PENDING,
                subscriptionDAO.findByReference(reference).orElseThrow().getStatus());
    }

    @Test
    void givenACancelledItn_thenTheSubscriptionIsCancelled() throws Exception {
        String reference = referenceFromCheckout(checkout(1));

        HttpResponse response = postItn(itn(reference, "CANCELLED", "44.90"));

        Assertions.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assertions.assertEquals(SubscriptionStatus.CANCELLED,
                subscriptionDAO.findByReference(reference).orElseThrow().getStatus());
    }

    private HttpResponse checkout(long magazineId) throws IOException {
        final HttpPost httpCheckoutPost = new HttpPost(HOST + "/shop/newsstand/checkout");
        httpCheckoutPost.setEntity(new StringEntity("""
                {
                    "magazineId": %d,
                    "firstName": "Joe",
                    "lastName": "Soap",
                    "emailAddress": "joe@soap.net",
                    "paymentFrequency": "MONTHLY"
                }
                """.formatted(magazineId), ContentType.APPLICATION_JSON));
        return HttpClientBuilder.create().build().execute(httpCheckoutPost);
    }

    private static String referenceFromCheckout(HttpResponse checkoutResponse) throws IOException {
        return toMap(JSON.readTree(EntityUtils.toString(checkoutResponse.getEntity()))).get("m_payment_id");
    }

    /** An ITN shaped like PayFast's, signed with the test passphrase */
    private static Map<String, String> itn(String reference, String paymentStatus, String amountGross)
            throws NoSuchAlgorithmException {
        Map<String, String> itn = new LinkedHashMap<>();
        itn.put("m_payment_id", reference);
        itn.put("pf_payment_id", "1089250");
        itn.put("payment_status", paymentStatus);
        itn.put("item_name", "CAR");
        itn.put("item_description", "April issue of CAR");
        itn.put("amount_gross", amountGross);
        itn.put("amount_fee", "-2.30");
        itn.put("amount_net", "42.60");
        itn.put("custom_str1", "");
        itn.put("name_first", "Joe");
        itn.put("name_last", "Soap");
        itn.put("email_address", "joe@soap.net");
        itn.put("merchant_id", MERCHANT_ID);
        itn.put("token", "dc0521d3-55fe-269b-fa00-b647310d760f");
        itn.put("billing_date", "2026-11-14");
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

    private static Map<String, String> toMap(JsonNode formFields) {
        Map<String, String> map = new LinkedHashMap<>();
        formFields.forEach(field -> map.put(field.get("name").asText(), field.get("value").asText()));
        return map;
    }
}

package dev.njabulo.spaza.shop;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SpazaDemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class SpazaShopAPITest {

    private static final String HOST ="http://localhost:8080";

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
    void givenSubscriptionDetails_whenCheckout_thenAUniqueSignatureIsGenerated() throws IOException {
        //Given
        final HttpPost httpCheckoutPost = new HttpPost(HOST + "/shop/newsstand/checkout");
        StringEntity customerDetails = new StringEntity("""
                {
                    "subscriptionId": 1,
                    "firstName": "Joe",
                    "lastName": "Soap"
                }
                """);
        httpCheckoutPost.setEntity(customerDetails);

        //When
        HttpResponse checkoutResponse = HttpClientBuilder.create().build().execute(httpCheckoutPost);

        //Then
        String generatedHashSignature = EntityUtils.toString(checkoutResponse.getEntity());
        Assertions.assertTrue(generatedHashSignature.length() >= 32);
    }
}

package dev.njabulo.spaza.shop.persistence.service;

import dev.njabulo.spaza.shop.common.HtmlFormField;
import dev.njabulo.spaza.shop.persistence.dao.MagazineDAO;
import dev.njabulo.spaza.shop.persistence.dao.SubscriptionDAO;
import dev.njabulo.spaza.shop.persistence.model.Magazine;
import dev.njabulo.spaza.shop.persistence.model.Subscription;
import org.payment.gateway.ElementKeyValuePair;
import org.payment.gateway.PayFastProvider;
import org.payment.gateway.PayFastProviderUtility;
import org.payment.gateway.SubscriptionFrequency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class ShopService {

    private final Logger logger = LoggerFactory.getLogger(ShopService.class);
    private final MagazineDAO magazineDAO;
    private final SubscriptionDAO subscriptionDAO;

    public ShopService(MagazineDAO magazineDAO, SubscriptionDAO subscriptionDAO) {
        this.magazineDAO = magazineDAO;
        this.subscriptionDAO = subscriptionDAO;
    }

    @Transactional(readOnly = true)
    public List<Magazine> viewMagazineStand() {
        return magazineDAO.findAll();
    }

    public Magazine getMagazineById(long identification) {
        Optional<Magazine> magazine = magazineDAO.findById(identification);
        return magazine.orElse(null);
    }

    @Transactional
    public Subscription addSubscription(Subscription subscription) {
        return subscriptionDAO.saveAndFlush(subscription);
    }

    public List<HtmlFormField> generateHiddenHTMLForm(Subscription subscription, Magazine magazine) {
        //Read security details from environment variables
        String merchantId = System.getenv("MERCHANT_ID");
        String merchantKey = System.getenv("MERCHANT_KEY");
        String passPhrase = System.getenv("PASS_PHRASE");

        //Other fields
        String returnUrl = System.getenv("RETURN_URL");
        String cancelUrl = System.getenv("CANCEL_URL");
        String notifyUrl = System.getenv("NOTIFY_URL");

        //Collate subscription data
        PayFastProvider payFastProvider = new PayFastProvider
                .PayFastProviderBuilder(merchantId, merchantKey, passPhrase)
                .firstName(subscription.getFirstName())
                .lastName(subscription.getLastName())
                .emailAddress(subscription.getEmailAddress())
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .notifyUrl(notifyUrl)
                .paymentId(subscription.getReference())
                .amount(magazine.getPrice())
                .itemName(magazine.getName())
                .itemDescription(magazine.getMonth() + " issue of " + magazine.getName())
                .customStr1(magazine.getSku() + "-" + subscription.getId())
                .subscriptionType(PayFastProviderUtility.PAYFAST_SUBSCRIPTION_TYPE_FLAG)
                .recurringAmount(magazine.getPrice())
                .frequency(SubscriptionFrequency.MONTHLY_CYCLE_PERIOD.getCode())
                .cycles(12) //number of payments/cycles that will occur (0 = indefinite)
                .build();

        try {
            String signature = PayFastProviderUtility.generateSecuritySignature(payFastProvider);
            return arrangeHiddenHTMLFormElements(payFastProvider, signature);
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to create security signature", e);
        }

        return Collections.emptyList();
    }

    private List<HtmlFormField> arrangeHiddenHTMLFormElements(PayFastProvider payFastProvider, String signature) {
        TreeMap<Integer, ElementKeyValuePair> sortedFormElements = (TreeMap<Integer, ElementKeyValuePair>)
                PayFastProviderUtility.sortElementsByPlacement(payFastProvider);
        int nextKey = sortedFormElements.lastKey()+1;
        sortedFormElements.put(nextKey, new ElementKeyValuePair("signature", signature));

        List<HtmlFormField> htmlFormElements = new ArrayList<>();
        for (Map.Entry<Integer, ElementKeyValuePair> element : sortedFormElements.entrySet()) {
            if (null != element.getValue()) {
                HtmlFormField formElement = new HtmlFormField(
                        element.getValue().key(),
                        "hidden",
                        element.getValue().value()
                );

                htmlFormElements.add(formElement);
            }
        }

        return htmlFormElements;
    }
}

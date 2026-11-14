package dev.njabulo.spaza.shop.persistence.service;

import dev.njabulo.spaza.shop.common.HtmlFormField;
import dev.njabulo.spaza.shop.common.SubscriptionStatus;
import dev.njabulo.spaza.shop.config.PayFastProperties;
import dev.njabulo.spaza.shop.persistence.dao.MagazineDAO;
import dev.njabulo.spaza.shop.persistence.dao.SubscriptionDAO;
import dev.njabulo.spaza.shop.persistence.model.Magazine;
import dev.njabulo.spaza.shop.persistence.model.Subscription;
import org.payment.gateway.ElementKeyValuePair;
import org.payment.gateway.PayFastItnValidator;
import org.payment.gateway.PayFastProvider;
import org.payment.gateway.PayFastProviderUtility;
import org.payment.gateway.SubscriptionFrequency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ShopService {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    private final Logger logger = LoggerFactory.getLogger(ShopService.class);
    private final MagazineDAO magazineDAO;
    private final SubscriptionDAO subscriptionDAO;
    private final PayFastProperties payFastProperties;
    private final PayFastItnValidator itnValidator;

    public ShopService(MagazineDAO magazineDAO, SubscriptionDAO subscriptionDAO,
                       PayFastProperties payFastProperties, PayFastItnValidator itnValidator) {
        this.magazineDAO = magazineDAO;
        this.subscriptionDAO = subscriptionDAO;
        this.payFastProperties = payFastProperties;
        this.itnValidator = itnValidator;
    }

    @Transactional(readOnly = true)
    public List<Magazine> viewMagazineStand() {
        return magazineDAO.findAll();
    }

    public Magazine getMagazineById(long identification) {
        Optional<Magazine> magazine = magazineDAO.findById(identification);
        return magazine.orElse(null);
    }

    /**
     * Saves a new subscription in the PENDING state with a fresh, server-generated reference. The reference is sent
     * to PayFast as m_payment_id and comes back on every ITN, which is how notifications are matched to it.
     */
    @Transactional
    public Subscription addSubscription(Subscription subscription) {
        subscription.setReference(UUID.randomUUID().toString());
        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setPayFastToken(null); //Only PayFast hands out tokens, via the ITN
        subscription.setCreateDate(LocalDateTime.now());
        return subscriptionDAO.saveAndFlush(subscription);
    }

    public List<HtmlFormField> generateHiddenHTMLForm(Subscription subscription, Magazine magazine) {
        //Collate subscription data
        PayFastProvider payFastProvider = new PayFastProvider
                .PayFastProviderBuilder(payFastProperties.merchantId(), payFastProperties.merchantKey(),
                        payFastProperties.passPhrase())
                .firstName(subscription.getFirstName())
                .lastName(subscription.getLastName())
                .emailAddress(subscription.getEmailAddress())
                .returnUrl(payFastProperties.returnUrl())
                .cancelUrl(payFastProperties.cancelUrl())
                .notifyUrl(payFastProperties.notifyUrl())
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
        //NB: the form-safe variant, so that the passphrase never leaves the server
        TreeMap<Integer, ElementKeyValuePair> sortedFormElements = new TreeMap<>(
                PayFastProviderUtility.sortFormElementsByPlacement(payFastProvider));
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

    /**
     * Handles a PayFast ITN (Instant Transaction Notification): runs PayFast's recommended security checks and,
     * only if they all pass, updates the matching subscription.
     *
     * @param receivedFields the ITN fields, in the order PayFast sent them
     * @param remoteAddress  the IP address the notification came from
     * @return true if the notification was genuine and has been applied
     */
    @Transactional
    public boolean processPaymentNotification(Map<String, String> receivedFields, String remoteAddress) {
        String reference = receivedFields.get("m_payment_id");

        //1-3. Signature, merchant id and source address
        PayFastItnValidator.Result localCheck = itnValidator.verifyLocally(receivedFields, remoteAddress);
        if (!localCheck.valid()) {
            logger.warn("Rejected ITN for {}: {}", reference, localCheck.reason());
            return false;
        }

        //4. The notification is for an order we know about, and the amount is what we expected
        Optional<Subscription> found = reference == null ? Optional.empty() : subscriptionDAO.findByReference(reference);
        if (found.isEmpty()) {
            logger.warn("Rejected ITN: no subscription with reference {}", reference);
            return false;
        }
        Subscription subscription = found.get();
        Magazine magazine = getMagazineById(subscription.getMagazineId());
        if (magazine == null || !amountMatches(magazine.getPrice(), receivedFields.get("amount_gross"))) {
            logger.warn("Rejected ITN for {}: amount_gross {} does not match the expected amount", reference,
                    receivedFields.get("amount_gross"));
            return false;
        }

        //5. PayFast confirms it really sent this notification
        if (payFastProperties.verifyWithServer()) {
            PayFastItnValidator.Result serverCheck = itnValidator.confirmWithServer(receivedFields);
            if (!serverCheck.valid()) {
                logger.warn("Rejected ITN for {}: {}", reference, serverCheck.reason());
                return false;
            }
        }

        //All checks passed: update our own records
        String paymentStatus = receivedFields.getOrDefault("payment_status", "");
        switch (paymentStatus) {
            case "COMPLETE" -> {
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                String token = receivedFields.get("token");
                if (token != null && !token.isBlank()) {
                    subscription.setPayFastToken(token); //needed later to pause/cancel/update via PayFast's API
                }
            }
            case "CANCELLED" -> subscription.setStatus(SubscriptionStatus.CANCELLED);
            case "FAILED" -> subscription.setStatus(SubscriptionStatus.FAILED);
            default -> logger.info("ITN for {} has payment_status '{}'; no change made", reference, paymentStatus);
        }
        subscriptionDAO.save(subscription);
        logger.info("ITN for {} applied: payment_status={}, subscription status={}", reference, paymentStatus,
                subscription.getStatus());
        return true;
    }

    private static boolean amountMatches(double expected, String received) {
        if (received == null) {
            return false;
        }
        try {
            BigDecimal difference = BigDecimal.valueOf(expected).subtract(new BigDecimal(received.trim())).abs();
            return difference.compareTo(AMOUNT_TOLERANCE) <= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

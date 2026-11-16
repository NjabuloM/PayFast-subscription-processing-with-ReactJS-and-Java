package dev.njabulo.spaza.shop.persistence.service;

import dev.njabulo.spaza.shop.common.FrequencyType;
import dev.njabulo.spaza.shop.common.HtmlFormField;
import dev.njabulo.spaza.shop.common.SubscriptionPlan;
import dev.njabulo.spaza.shop.common.SubscriptionStatus;
import dev.njabulo.spaza.shop.config.PayFastProperties;
import dev.njabulo.spaza.shop.persistence.dao.MagazineDAO;
import dev.njabulo.spaza.shop.persistence.dao.PaymentNotificationDAO;
import dev.njabulo.spaza.shop.persistence.dao.SubscriptionDAO;
import dev.njabulo.spaza.shop.persistence.model.Magazine;
import dev.njabulo.spaza.shop.persistence.model.PaymentNotification;
import dev.njabulo.spaza.shop.persistence.model.Subscription;
import dev.njabulo.spaza.shop.web.dto.CheckoutForm;
import org.payment.gateway.ElementKeyValuePair;
import org.payment.gateway.PayFastItnValidator;
import org.payment.gateway.PayFastProvider;
import org.payment.gateway.PayFastProviderUtility;
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
    private final PaymentNotificationDAO paymentNotificationDAO;
    private final PayFastProperties payFastProperties;
    private final PayFastItnValidator itnValidator;

    public ShopService(MagazineDAO magazineDAO, SubscriptionDAO subscriptionDAO,
                       PaymentNotificationDAO paymentNotificationDAO,
                       PayFastProperties payFastProperties, PayFastItnValidator itnValidator) {
        this.magazineDAO = magazineDAO;
        this.subscriptionDAO = subscriptionDAO;
        this.paymentNotificationDAO = paymentNotificationDAO;
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

    /** The billing options for a magazine, priced on the server */
    public List<SubscriptionPlan> getPlans(Magazine magazine) {
        return Arrays.stream(FrequencyType.values())
                .map(frequency -> SubscriptionPlan.of(magazine.getPrice(), frequency))
                .toList();
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

    /**
     * Saves a PENDING subscription for the chosen plan, fixing the amount PayFast will charge per cycle.
     */
    @Transactional
    public Subscription addSubscription(Subscription subscription, SubscriptionPlan plan) {
        subscription.setPaymentFrequency(plan.frequency());
        subscription.setAmount(plan.amount());
        return addSubscription(subscription);
    }

    public CheckoutForm generateCheckoutForm(Subscription subscription, Magazine magazine, SubscriptionPlan plan) {
        String planName = plan.frequency() == FrequencyType.ANNUALLY ? "Annual" : "Monthly";

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
                .amount(plan.amount()) //first payment, collected immediately
                .itemName(magazine.getName())
                .itemDescription(planName + " subscription to " + magazine.getName())
                .customStr1(magazine.getSku() + "-" + subscription.getId())
                .subscriptionType(PayFastProviderUtility.PAYFAST_SUBSCRIPTION_TYPE_FLAG)
                .recurringAmount(plan.amount())
                .frequency(plan.frequency().getPayFastFrequencyCode())
                .cycles(plan.cycles()) //number of payments/cycles that will occur (0 = indefinite)
                .build();

        try {
            String signature = PayFastProviderUtility.generateSecuritySignature(payFastProvider);
            return new CheckoutForm(payFastProperties.processUrl(),
                    arrangeHiddenHTMLFormElements(payFastProvider, signature));
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to create security signature", e);
        }

        return new CheckoutForm(payFastProperties.processUrl(), Collections.emptyList());
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
                //Always a string: as a JSON number, 44.90 would reach the browser as 44.9 and no longer match
                //the value that was signed
                HtmlFormField formElement = new HtmlFormField(
                        element.getValue().key(),
                        "hidden",
                        String.valueOf(element.getValue().value())
                );

                htmlFormElements.add(formElement);
            }
        }

        return htmlFormElements;
    }

    /**
     * Handles a PayFast ITN (Instant Transaction Notification): runs PayFast's recommended security checks and,
     * only if they all pass, records the notification and updates the matching subscription.
     * <p>
     * Status rules: a successful payment activates the subscription; a cancellation is final (nothing reactivates
     * a cancelled subscription, so a late or replayed payment notification can't); a failed payment only matters
     * for the first payment, later failures are kept in the payment history. A notification already processed
     * (same pf_payment_id and payment_status) is acknowledged without changing anything.
     *
     * @param receivedFields the ITN fields, in the order PayFast sent them
     * @param remoteAddress  the IP address the notification came from
     * @return true if the notification is genuine (applied, or a duplicate); false if it was rejected
     */
    @Transactional
    public boolean processPaymentNotification(Map<String, String> receivedFields, String remoteAddress) {
        String reference = receivedFields.get("m_payment_id");
        String paymentStatus = receivedFields.getOrDefault("payment_status", "");
        String pfPaymentId = receivedFields.get("pf_payment_id");

        //1-3. Signature, merchant id and source address
        PayFastItnValidator.Result localCheck = itnValidator.verifyLocally(receivedFields, remoteAddress);
        if (!localCheck.valid()) {
            logger.warn("Rejected ITN for {}: {}", reference, localCheck.reason());
            return false;
        }

        //4. The notification is for an order we know about...
        Optional<Subscription> found = reference == null ? Optional.empty() : subscriptionDAO.findByReference(reference);
        if (found.isEmpty()) {
            logger.warn("Rejected ITN: no subscription with reference {}", reference);
            return false;
        }
        Subscription subscription = found.get();

        //...and, for a payment, the amount is what this subscription was sold at. PayFast doesn't always send an
        //amount for other events, such as a cancellation, so those aren't checked against it.
        if ("COMPLETE".equals(paymentStatus)
                && !amountMatches(subscription.getAmount(), receivedFields.get("amount_gross"))) {
            logger.warn("Rejected ITN for {}: amount_gross {} does not match the expected {}", reference,
                    receivedFields.get("amount_gross"), subscription.getAmount());
            return false;
        }

        //Already processed? PayFast resends a notification until it gets a 200, and a replayed notification still
        //carries a valid signature, so acknowledge it without applying it twice.
        if (pfPaymentId != null && !pfPaymentId.isBlank()
                && paymentNotificationDAO.existsByPfPaymentIdAndPaymentStatus(pfPaymentId, paymentStatus)) {
            logger.info("ITN for {} (pf_payment_id {}, {}) was already processed", reference, pfPaymentId, paymentStatus);
            return true;
        }

        //5. PayFast confirms it really sent this notification
        if (payFastProperties.verifyWithServer()) {
            PayFastItnValidator.Result serverCheck = itnValidator.confirmWithServer(receivedFields);
            if (!serverCheck.valid()) {
                logger.warn("Rejected ITN for {}: {}", reference, serverCheck.reason());
                return false;
            }
        }

        //All checks passed: record it, then update our own records
        recordNotification(subscription, pfPaymentId, paymentStatus, receivedFields.get("amount_gross"));
        applyStatus(subscription, paymentStatus, receivedFields.get("token"));
        subscriptionDAO.save(subscription);
        logger.info("ITN for {} applied: payment_status={}, subscription status={}", reference, paymentStatus,
                subscription.getStatus());
        return true;
    }

    @Transactional(readOnly = true)
    public List<PaymentNotification> getPaymentHistory(long subscriptionId) {
        return paymentNotificationDAO.findBySubscriptionIdOrderByIdAsc(subscriptionId);
    }

    private void recordNotification(Subscription subscription, String pfPaymentId, String paymentStatus,
                                    String amountGross) {
        PaymentNotification notification = new PaymentNotification();
        notification.setSubscriptionId(subscription.getId());
        notification.setPfPaymentId(pfPaymentId == null || pfPaymentId.isBlank() ? null : pfPaymentId);
        notification.setPaymentStatus(paymentStatus);
        notification.setAmountGross(parseAmount(amountGross));
        notification.setReceivedAt(LocalDateTime.now());
        paymentNotificationDAO.save(notification);
    }

    private void applyStatus(Subscription subscription, String paymentStatus, String token) {
        SubscriptionStatus current = subscription.getStatus();
        if (current == SubscriptionStatus.CANCELLED) {
            logger.info("Subscription {} is cancelled; '{}' notification recorded but status unchanged",
                    subscription.getReference(), paymentStatus);
            return;
        }

        switch (paymentStatus) {
            case "COMPLETE" -> {
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                if (token != null && !token.isBlank()) {
                    subscription.setPayFastToken(token); //needed later to pause/cancel/update via PayFast's API
                }
            }
            case "CANCELLED" -> subscription.setStatus(SubscriptionStatus.CANCELLED);
            case "FAILED" -> {
                if (current == SubscriptionStatus.PENDING) {
                    subscription.setStatus(SubscriptionStatus.FAILED);
                }
            }
            default -> logger.info("ITN for {} has payment_status '{}'; no change made",
                    subscription.getReference(), paymentStatus);
        }
    }

    private static boolean amountMatches(BigDecimal expected, String received) {
        BigDecimal actual = parseAmount(received);
        if (expected == null || actual == null) {
            return false;
        }
        return expected.subtract(actual).abs().compareTo(AMOUNT_TOLERANCE) <= 0;
    }

    private static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

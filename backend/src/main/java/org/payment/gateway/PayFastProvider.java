package org.payment.gateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public class PayFastProvider {
    // Merchant details
    @GatewayElement(label = "merchant_id", placement = 1)
    private final String merchantId;
    @GatewayElement(label = "merchant_key", placement = 2)
    private final String merchantKey;
    @GatewayElement(label = "return_url", placement = 3)
    private final String returnUrl;
    @GatewayElement(label = "cancel_url", placement = 4)
    private final String cancelUrl;
    @GatewayElement(label = "notify_url", placement = 5)
    private final String notifyUrl;
    @GatewayElement(label = "passphrase", placement = 21, signatureOnly = true)
    private final String passPhrase;

    // Buyer details
    @GatewayElement(label = "name_first", placement = 6)
    private final String firstName;
    @GatewayElement(label = "name_last", placement = 7)
    private final String lastName;
    @GatewayElement(label = "email_address", placement = 8)
    private final String emailAddress;
    @GatewayElement(label = "cell_number", placement = 9)
    private final String cellNumber; //Will be used if email is not provided

    // Transaction detail
    @GatewayElement(label = "m_payment_id", placement = 10)
    private final String paymentId;
    @GatewayElement(label = "amount", placement = 11)
    private final BigDecimal amount;
    @GatewayElement(label = "item_name", placement = 12)
    private final String itemName;
    @GatewayElement(label = "item_description", placement = 13)
    private final String itemDescription;

    //Custom integers - OPTIONAL (1 to 5)
    @GatewayElement(label = "custom_int1", placement = 14)
    private final Integer customInt1;

    //Custom strings - OPTIONAL (1 to 5)
    @GatewayElement(label = "custom_str1", placement = 15)
    private final String customStr1;

    //Recurring billing / Subscription
    @GatewayElement(label = "subscription_type", placement = 16)
    private final int subscriptionType;
    @GatewayElement(label = "billing_date", placement = 17)
    private final LocalDateTime billingDate;
    @GatewayElement(label = "recurring_amount", placement = 18)
    private final BigDecimal recurringAmount;
    @GatewayElement(label = "frequency", placement = 19)
    private final int frequency;
    @GatewayElement(label = "cycles", placement = 20)
    private final int cycles;

    private PayFastProvider(PayFastProviderBuilder providerBuilder) {
        this.merchantId = providerBuilder.merchantId;
        this.merchantKey = providerBuilder.merchantKey;
        this.returnUrl = providerBuilder.returnUrl;
        this.cancelUrl = providerBuilder.cancelUrl;
        this.notifyUrl = providerBuilder.notifyUrl;
        this.firstName = providerBuilder.firstName;
        this.lastName = providerBuilder.lastName;
        this.emailAddress = providerBuilder.emailAddress;
        this.cellNumber = providerBuilder.cellNumber;
        this.paymentId = providerBuilder.paymentId;
        this.amount = providerBuilder.amount;
        this.itemName = providerBuilder.itemName;
        this.itemDescription = providerBuilder.itemDescription;
        this.customInt1 = providerBuilder.customInt1;
        this.customStr1 = providerBuilder.customStr1;
        this.subscriptionType = providerBuilder.subscriptionType;
        this.billingDate = providerBuilder.billingDate;
        this.recurringAmount = providerBuilder.recurringAmount;
        this.frequency = providerBuilder.frequency;
        this.cycles = providerBuilder.cycles;
        this.passPhrase = providerBuilder.passPhrase;
    }

    public static class PayFastProviderBuilder {
        private final String merchantId;
        private final String merchantKey;
        private final String passPhrase;
        private String returnUrl;
        private String cancelUrl;
        private String notifyUrl;
        private String firstName;
        private String lastName;
        private String emailAddress;
        private String cellNumber;
        private String paymentId;
        private BigDecimal amount;
        private String itemName;
        private String itemDescription;
        private Integer customInt1;
        private String customStr1;
        private int subscriptionType;
        private LocalDateTime billingDate;
        private BigDecimal recurringAmount;
        private int frequency;
        private int cycles;

        public PayFastProviderBuilder(String merchantId, String merchantKey, String passPhrase) {
            this.merchantId = merchantId;
            this.merchantKey = merchantKey;
            this.passPhrase = passPhrase;
        }

        public PayFastProviderBuilder returnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        public PayFastProviderBuilder cancelUrl(String cancelUrl) {
            this.cancelUrl = cancelUrl;
            return this;
        }

        public PayFastProviderBuilder notifyUrl(String notifyUrl) {
            this.notifyUrl = notifyUrl;
            return this;
        }

        public PayFastProviderBuilder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public PayFastProviderBuilder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public PayFastProviderBuilder emailAddress(String emailAddress) {
            this.emailAddress = emailAddress;
            return this;
        }

        public PayFastProviderBuilder cellNumber(String cellNumber) {
            this.cellNumber = cellNumber;
            return this;
        }

        public PayFastProviderBuilder paymentId(String paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public PayFastProviderBuilder amount(BigDecimal amount) {
            this.amount = toRand(amount);
            return this;
        }

        public PayFastProviderBuilder itemName(String itemName) {
            this.itemName = itemName;
            return this;
        }

        public PayFastProviderBuilder itemDescription(String itemDescription) {
            this.itemDescription = itemDescription;
            return this;
        }

        public PayFastProviderBuilder customInt1(Integer customInt1) {
            this.customInt1 = customInt1;
            return this;
        }

        public PayFastProviderBuilder customStr1(String customStr1) {
            this.customStr1 = customStr1;
            return this;
        }

        public PayFastProviderBuilder subscriptionType(int subscriptionType) {
            this.subscriptionType = subscriptionType;
            return this;
        }

        public PayFastProviderBuilder billingDate(LocalDateTime billingDate) {
            this.billingDate = billingDate;
            return this;
        }

        public PayFastProviderBuilder recurringAmount(BigDecimal recurringAmount) {
            this.recurringAmount = toRand(recurringAmount);
            return this;
        }

        public PayFastProviderBuilder frequency(int frequency) {
            this.frequency = frequency;
            return this;
        }

        public PayFastProviderBuilder cycles(int cycles) {
            this.cycles = cycles;
            return this;
        }

        public PayFastProvider build() {
            return new PayFastProvider(this);
        }

        /** PayFast expects amounts in rand with two decimals, e.g. 44.90 */
        private static BigDecimal toRand(BigDecimal amount) {
            return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
        }
    }
}

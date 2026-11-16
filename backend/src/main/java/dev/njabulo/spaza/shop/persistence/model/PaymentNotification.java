package dev.njabulo.spaza.shop.persistence.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A genuine (fully validated) PayFast notification that was received for a subscription.
 */
@Entity(name = "PAYMENT_NOTIFICATION")
public class PaymentNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private long id;

    @Column(name = "subscription_id")
    @Getter
    @Setter
    private long subscriptionId;

    /** PayFast's id for the payment; the same for every resend of one notification */
    @Column(name = "pf_payment_id")
    @Getter
    @Setter
    private String pfPaymentId;

    @Column(name = "payment_status")
    @Getter
    @Setter
    private String paymentStatus;

    @Column(name = "amount_gross")
    @Getter
    @Setter
    private BigDecimal amountGross;

    @Column(name = "received_at")
    @Getter
    @Setter
    private LocalDateTime receivedAt;
}

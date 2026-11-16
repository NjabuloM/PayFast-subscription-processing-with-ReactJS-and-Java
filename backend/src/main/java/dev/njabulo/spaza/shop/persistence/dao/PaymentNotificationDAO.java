package dev.njabulo.spaza.shop.persistence.dao;

import dev.njabulo.spaza.shop.persistence.model.PaymentNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentNotificationDAO extends JpaRepository<PaymentNotification, Long> {
    boolean existsByPfPaymentIdAndPaymentStatus(String pfPaymentId, String paymentStatus);

    List<PaymentNotification> findBySubscriptionIdOrderByIdAsc(long subscriptionId);
}

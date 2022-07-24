package dev.njabulo.spaza.shop.persistence.dao;

import dev.njabulo.spaza.shop.persistence.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionDAO extends JpaRepository<Subscription, Long> {
}

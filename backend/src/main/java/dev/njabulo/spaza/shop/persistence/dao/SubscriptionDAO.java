package dev.njabulo.spaza.shop.persistence.dao;

import dev.njabulo.spaza.shop.persistence.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionDAO extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByReference(String reference);
}

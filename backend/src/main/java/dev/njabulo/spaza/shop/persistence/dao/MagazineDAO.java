package dev.njabulo.spaza.shop.persistence.dao;

import dev.njabulo.spaza.shop.persistence.model.Magazine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MagazineDAO extends JpaRepository<Magazine, Long> {
}

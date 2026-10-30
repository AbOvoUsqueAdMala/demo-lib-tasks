package com.example.extension.kz.repository;

import com.example.extension.kz.model.entity.KzOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KzOrderRepository extends JpaRepository<KzOrder, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select customerOrder from KzOrder customerOrder where customerOrder.id = :orderId")
	Optional<KzOrder> findForUpdate(@Param("orderId") UUID orderId);
}

package com.example.extension.repository;

import com.example.extension.model.entity.ExtensionOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExtensionOrderRepository extends JpaRepository<ExtensionOrder, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select customerOrder from ExtensionOrder customerOrder where customerOrder.id = :orderId")
	Optional<ExtensionOrder> findForUpdate(@Param("orderId") UUID orderId);
}


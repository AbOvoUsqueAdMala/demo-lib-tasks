package com.example.demo.repository;

import com.example.demo.model.entity.Order;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select customerOrder from Order customerOrder where customerOrder.id = :orderId")
	Optional<Order> findForUpdate(@Param("orderId") UUID orderId);
}


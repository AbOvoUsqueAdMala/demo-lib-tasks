package com.example.extension.kz.repository;

import com.example.extension.kz.model.entity.KzTaskResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KzTaskResultRepository extends JpaRepository<KzTaskResult, Long> {
	List<KzTaskResult> findByOrderIdOrderById(UUID orderId);
}

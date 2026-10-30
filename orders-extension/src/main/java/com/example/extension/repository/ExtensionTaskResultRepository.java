package com.example.extension.repository;

import com.example.extension.model.entity.ExtensionTaskResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtensionTaskResultRepository extends JpaRepository<ExtensionTaskResult, Long> {
	List<ExtensionTaskResult> findByOrderIdOrderById(UUID orderId);
}


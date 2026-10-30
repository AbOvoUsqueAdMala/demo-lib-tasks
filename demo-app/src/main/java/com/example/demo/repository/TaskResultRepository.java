package com.example.demo.repository;

import com.example.demo.model.entity.TaskResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskResultRepository extends JpaRepository<TaskResult, Long> {
	List<TaskResult> findByOrderIdOrderById(UUID orderId);
}


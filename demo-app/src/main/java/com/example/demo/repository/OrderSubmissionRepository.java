package com.example.demo.repository;

import com.example.demo.model.entity.OrderSubmissionRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSubmissionRepository extends JpaRepository<OrderSubmissionRecord, UUID> {}

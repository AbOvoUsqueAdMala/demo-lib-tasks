package com.example.demo.model.entity;

import com.example.orders.api.model.dto.OrderSubmission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_submissions", schema = "app")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSubmissionRecord {
	@Id
	private UUID id;
	@Column(nullable = false, length = 20)
	private String processorCode;
	@Column(nullable = false, length = 200)
	private String description;
	@Column(length = 100)
	private String customerReference;
	@Column(length = 500)
	private String internalComment;

	public OrderSubmissionRecord(UUID orderId, String processorCode, OrderSubmission submission) {
		this.id = orderId;
		this.processorCode = processorCode;
		this.description = submission.description();
		this.customerReference = submission.customerReference();
		this.internalComment = submission.internalComment();
	}
}

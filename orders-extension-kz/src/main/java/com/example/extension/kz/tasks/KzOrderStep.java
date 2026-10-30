package com.example.extension.kz.tasks;

import com.github.kagkarlsson.scheduler.task.TaskDescriptor;

public enum KzOrderStep {
	REGISTER_ORDER("NEW", "REGISTERED"),
	GET_ORDER("REGISTERED", "FETCHED"),
	SEND_ORDER("FETCHED", "SENT");

	private final String expectedStatus;
	private final String completedStatus;

	KzOrderStep(String expectedStatus, String completedStatus) {
		this.expectedStatus = expectedStatus;
		this.completedStatus = completedStatus;
	}

	public String expectedStatus() {
		return expectedStatus;
	}

	public String completedStatus() {
		return completedStatus;
	}

	public String taskName() {
		return "EXT_" + name();
	}

	public TaskDescriptor<Void> descriptor() {
		return TaskDescriptor.of(taskName());
	}

	public KzOrderStep next() {
		return this == SEND_ORDER ? null : values()[ordinal() + 1];
	}
}

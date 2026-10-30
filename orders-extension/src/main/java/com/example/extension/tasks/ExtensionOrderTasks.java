package com.example.extension.tasks;

import com.example.extension.service.ExtensionOrderStateService;
import com.example.extension.service.ExtensionOrderWorkflow;
import com.github.kagkarlsson.scheduler.task.FailureHandler;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ExtensionOrderTasks {
	@Bean
	Task<Void> extensionRegisterOrderTask(ObjectProvider<ExtensionOrderWorkflow> workflow,
			ObjectProvider<ExtensionOrderStateService> state,
			@Value("${orders.retry-delay:2s}") Duration retryDelay,
			@Value("${orders.max-retries:5}") int maxRetries) {
		return task(ExtensionOrderStep.REGISTER_ORDER, workflow, state, retryDelay, maxRetries);
	}

	@Bean
	Task<Void> extensionGetOrderTask(ObjectProvider<ExtensionOrderWorkflow> workflow,
			ObjectProvider<ExtensionOrderStateService> state,
			@Value("${orders.retry-delay:2s}") Duration retryDelay,
			@Value("${orders.max-retries:5}") int maxRetries) {
		return task(ExtensionOrderStep.GET_ORDER, workflow, state, retryDelay, maxRetries);
	}

	@Bean
	Task<Void> extensionSendOrderTask(ObjectProvider<ExtensionOrderWorkflow> workflow,
			ObjectProvider<ExtensionOrderStateService> state,
			@Value("${orders.retry-delay:2s}") Duration retryDelay,
			@Value("${orders.max-retries:5}") int maxRetries) {
		return task(ExtensionOrderStep.SEND_ORDER, workflow, state, retryDelay, maxRetries);
	}

	private Task<Void> task(ExtensionOrderStep step, ObjectProvider<ExtensionOrderWorkflow> workflow,
			ObjectProvider<ExtensionOrderStateService> state, Duration retryDelay, int maxRetries) {
		return Tasks.oneTime(step.descriptor())
				.onFailure(FailureHandler.<Void>maxRetries(maxRetries)
						.withBackoff(retryDelay, 2.0)
						.then((completion, operations) -> {
							state.getObject().exhaustRetries(UUID.fromString(
									completion.getExecution().taskInstance.getId()), step);
							operations.remove();
						}))
				.execute((instance, context) ->
						workflow.getObject().execute(UUID.fromString(instance.getId()), step));
	}
}


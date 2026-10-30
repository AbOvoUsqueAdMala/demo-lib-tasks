package com.example.extension.kz.tasks;

import com.example.extension.kz.service.KzOrderStateService;
import com.example.extension.kz.service.KzOrderWorkflow;
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
public class KzOrderTasks {
	@Bean
	Task<Void> kzRegisterOrderTask(ObjectProvider<KzOrderWorkflow> workflow,
			ObjectProvider<KzOrderStateService> state,
			@Value("${orders.retry-delay:2s}") Duration retryDelay,
			@Value("${orders.max-retries:5}") int maxRetries) {
		return task(KzOrderStep.REGISTER_ORDER, workflow, state, retryDelay, maxRetries);
	}

	@Bean
	Task<Void> kzGetOrderTask(ObjectProvider<KzOrderWorkflow> workflow,
			ObjectProvider<KzOrderStateService> state,
			@Value("${orders.retry-delay:2s}") Duration retryDelay,
			@Value("${orders.max-retries:5}") int maxRetries) {
		return task(KzOrderStep.GET_ORDER, workflow, state, retryDelay, maxRetries);
	}

	@Bean
	Task<Void> kzSendOrderTask(ObjectProvider<KzOrderWorkflow> workflow,
			ObjectProvider<KzOrderStateService> state,
			@Value("${orders.retry-delay:2s}") Duration retryDelay,
			@Value("${orders.max-retries:5}") int maxRetries) {
		return task(KzOrderStep.SEND_ORDER, workflow, state, retryDelay, maxRetries);
	}

	private Task<Void> task(KzOrderStep step, ObjectProvider<KzOrderWorkflow> workflow,
			ObjectProvider<KzOrderStateService> state, Duration retryDelay, int maxRetries) {
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

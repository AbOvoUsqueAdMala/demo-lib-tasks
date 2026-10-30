package com.example.demo.tasks;

import com.example.demo.service.OrderStateService;
import com.example.demo.service.OrderWorkflow;
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
public class OrderTasks {
	@Bean
	Task<Void> mainRegisterOrderTask(ObjectProvider<OrderWorkflow> workflow,
			ObjectProvider<OrderStateService> state,
			@Value("${orders.retry-delay:2s}") Duration retryDelay,
			@Value("${orders.max-retries:5}") int maxRetries) {
		return task(OrderStep.REGISTER_ORDER, workflow, state, retryDelay, maxRetries);
	}

	@Bean
	Task<Void> mainGetOrderTask(ObjectProvider<OrderWorkflow> workflow,
			ObjectProvider<OrderStateService> state,
			@Value("${orders.retry-delay:2s}") Duration retryDelay,
			@Value("${orders.max-retries:5}") int maxRetries) {
		return task(OrderStep.GET_ORDER, workflow, state, retryDelay, maxRetries);
	}

	@Bean
	Task<Void> mainSendOrderTask(ObjectProvider<OrderWorkflow> workflow,
			ObjectProvider<OrderStateService> state,
			@Value("${orders.retry-delay:2s}") Duration retryDelay,
			@Value("${orders.max-retries:5}") int maxRetries) {
		return task(OrderStep.SEND_ORDER, workflow, state, retryDelay, maxRetries);
	}

	private Task<Void> task(OrderStep step, ObjectProvider<OrderWorkflow> workflow,
			ObjectProvider<OrderStateService> state, Duration retryDelay, int maxRetries) {
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


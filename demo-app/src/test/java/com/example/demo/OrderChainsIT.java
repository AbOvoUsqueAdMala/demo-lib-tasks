package com.example.demo;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.demo.model.dto.OrderRequest;
import com.example.demo.model.dto.OrderResponse;
import com.example.demo.model.dto.OrderView;
import com.example.demo.service.OrderApplicationService;
import com.example.demo.tasks.OrderStep;
import com.example.orders.api.service.OrderProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.TaskDescriptor;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OrderChainsIT {
	private static final List<Extension> EXTENSIONS = List.of(
			new Extension("kz", "Kz", "orders_extension", "extension_orders",
					"extension_schema_history"),
			new Extension("az", "Az", "orders_extension_az", "extension_az_orders",
					"az_schema_history"));
	@Container
	private static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17.6-alpine");
	private static final WireMockServer WIREMOCK = new WireMockServer(wireMockConfig()
			.dynamicPort().globalTemplating(true)
			.usingFilesUnderDirectory(Path.of("..", "wiremock").toAbsolutePath().toString()));

	@BeforeAll
	static void startWireMock() {
		WIREMOCK.start();
	}

	@AfterAll
	static void stopWireMock() {
		WIREMOCK.stop();
	}

	@BeforeEach
	void resetDatabaseAndStubs() throws Exception {
		WIREMOCK.resetToDefaultMappings();
		try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
				POSTGRES.getUsername(), POSTGRES.getPassword());
				var statement = connection.createStatement()) {
			statement.execute("DROP SCHEMA IF EXISTS app CASCADE");
			statement.execute("DROP SCHEMA IF EXISTS orders_extension CASCADE");
			statement.execute("DROP SCHEMA IF EXISTS orders_extension_az CASCADE");
		}
	}

	@Test
	void dependenciesControlClassesHandlersMigrationsAndPackagedJar() throws Exception {
		try (var context = start()) {
			assertThat(context.getBeansWithAnnotation(RestController.class).values())
					.allMatch(controller -> controller.getClass().getPackageName()
							.equals("com.example.demo.controller")).hasSize(1);
			assertThat(context.getBeansOfType(OrderProcessor.class)).hasSize(systems().size());
			assertThat(context.getBeansOfType(Scheduler.class)).hasSize(1);
			var tasks = context.getBeansOfType(Task.class).values();
			assertThat(tasks).hasSize(systems().size() * 3);
			assertThat(tasks).extracting(task -> task.getName()).doesNotHaveDuplicates();
			JdbcTemplate database = context.getBean(JdbcTemplate.class);
			assertThat(database.queryForObject(
					"SELECT count(*) FROM app.app_schema_history WHERE success", Integer.class))
					.isEqualTo(4);
			for (Extension extension : EXTENSIONS) {
				assertExtensionConfiguration(context, extension);
			}
		}
		assertPackagedExtensions();
	}

	@Test
	void adaptersMapDifferentLibraryModelsAndKeepHostFields() {
		try (var context = start()) {
			RestClient client = client(context);
			for (String system : systems()) {
				var request = new OrderRequest("Mapped description", "CLIENT-42", "Internal note");
				var response = client.post().uri("/api/orders?target=" + system).body(request)
						.retrieve().toEntity(OrderResponse.class);
				assertThat(response.getStatusCode().value()).isEqualTo(202);
				OrderResponse created = response.getBody();
				var location = new OrderLocation(created.id(),
						response.getHeaders().getLocation().toString());
				awaitSent(client, location);
				OrderResponse completed = client.get().uri(location.location())
						.retrieve().body(OrderResponse.class);
				assertThat(completed.description()).isEqualTo(request.description());
				assertThat(completed.customerReference()).isEqualTo(request.customerReference());
				assertThat(completed.internalComment()).isEqualTo(request.internalComment());
				assertThat(completed.target()).isEqualTo(system);
				var registrations = WIREMOCK.findAll(postRequestedFor(
						urlEqualTo("/" + system + "/source/orders")));
				assertThat(registrations).hasSize(1);
				assertThat(registrations.getFirst().getBodyAsString())
						.contains("Mapped description")
						.doesNotContain("CLIENT-42", "Internal note", "customerReference", "internalComment");
			}
		}
	}

	@Test
	void hostSubmissionOrderAndFirstTaskRollBackTogether() {
		try (var context = start("--db-scheduler.polling-interval=1h")) {
			context.getBean(Scheduler.class).stop();
			var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
			for (String system : systems()) {
				transaction.executeWithoutResult(status -> {
					context.getBean(OrderApplicationService.class).create(system,
							new OrderRequest("Atomic submission", "CLIENT-42", "Internal note"));
					status.setRollbackOnly();
				});
				assertThat(count(context, "app.order_submissions")).isZero();
				assertThat(count(context, ordersTable(system))).isZero();
				assertThat(count(context, "app.scheduled_tasks")).isZero();
			}
		}
	}

	@Test
	void unavailableProcessorDoesNotSaveOrScheduleAnything() {
		try (var context = start()) {
			assertThat(client(context).post().uri("/api/orders?target=missing")
					.body(new OrderRequest("Unavailable", null, null)).<Integer>exchange(
							(request, response) -> response.getStatusCode().value())).isEqualTo(404);
			assertThat(count(context, "app.order_submissions")).isZero();
			assertThat(count(context, "app.scheduled_tasks")).isZero();
		}
	}

	@Test
	void restartPreservesRoutingAndFieldsOwnedByHost() {
		List<OrderLocation> orders = new ArrayList<>();
		try (var context = start("--db-scheduler.polling-interval=1h")) {
			context.getBean(Scheduler.class).stop();
			for (String system : systems()) {
				var response = client(context).post().uri("/api/orders?target=" + system)
						.body(new OrderRequest("Restart order", "CLIENT-42", "Internal note"))
						.retrieve().toEntity(OrderResponse.class);
				orders.add(new OrderLocation(response.getBody().id(),
						response.getHeaders().getLocation().toString()));
			}
		}
		try (var context = start()) {
			for (OrderLocation order : orders) {
				// A restarted web server has a different port; persisted routing uses the UUID.
				String path = "/api/orders/" + order.id();
				awaitSent(client(context), new OrderLocation(order.id(), path));
				OrderResponse restored = client(context).get().uri(path)
						.retrieve().body(OrderResponse.class);
				assertThat(restored.customerReference()).isEqualTo("CLIENT-42");
				assertThat(restored.internalComment()).isEqualTo("Internal note");
			}
		}
	}

	@Test
	void upgradePreservesExistingOrdersAndPendingTasks() {
		Flyway.configure().dataSource(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.schemas("app").defaultSchema("app").table("app_schema_history")
				.locations("classpath:db/migration/app").target("2").load().migrate();
		for (Extension extension : EXTENSIONS.stream().filter(Extension::enabled).toList()) {
			Flyway.configure().dataSource(
					POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
					.schemas(extension.schema()).defaultSchema(extension.schema()).table(extension.historyTable())
					.locations("classpath:orders-extension-" + extension.country() + "/db/migration")
					.load().migrate();
		}
		var database = new JdbcTemplate(new DriverManagerDataSource(
				POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
		List<OrderLocation> existingOrders = new ArrayList<>();
		for (String system : systems()) {
			UUID orderId = UUID.randomUUID();
			database.update("INSERT INTO " + ordersTable(system)
					+ " (id, description, status, created_at, updated_at, version) "
					+ "VALUES (?, 'Existing order', 'NEW', now(), now(), 0)", orderId);
			database.update("INSERT INTO app.scheduled_tasks "
					+ "(task_name, task_instance, execution_time, picked, "
					+ "consecutive_failures, version, priority) "
					+ "VALUES (?, ?, now(), false, 0, 0, 0)",
					prefix(system) + "REGISTER_ORDER", orderId.toString());
			existingOrders.add(new OrderLocation(orderId, endpoint(system) + "/" + orderId));
		}
		try (var context = start()) {
			for (OrderLocation order : existingOrders) {
				assertThat(awaitSent(client(context), order).description()).isEqualTo("Existing order");
			}
			assertThat(count(context, "app.order_submissions")).isZero();
		}
	}

	@Test
	void independentChainsCompleteInParallelAndKeepHistory() {
		try (var context = start()) {
			RestClient client = client(context);
			List<OrderLocation> orders = new ArrayList<>();
			for (String system : systems()) {
				WIREMOCK.stubFor(post(urlEqualTo("/" + system + "/source/orders"))
						.willReturn(aResponse().withStatus(201).withFixedDelay(600)
								.withHeader("Content-Type", "application/json")
								.withBody("{\"externalId\":\"{{jsonPath request.body '$.id'}}\"}")
								.withTransformers("response-template")));
				for (int orderIndex = 0; orderIndex < 6; orderIndex++) {
					orders.add(create(client, system));
				}
			}
			for (OrderLocation order : orders) {
				awaitSent(client, order);
			}
			for (String system : systems()) {
				assertOverlappingRegistrations(system);
				WIREMOCK.verify(6, postRequestedFor(urlEqualTo("/" + system + "/source/orders")));
				WIREMOCK.verify(6, postRequestedFor(urlEqualTo("/" + system + "/destination/orders")));
			}
			await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
					assertThat(count(context, "app.scheduled_tasks")).isZero());
		}
	}

	@ParameterizedTest
	@EnumSource(OrderStep.class)
	void retriesTransientFailureAndKeepsSameIdempotencyKey(OrderStep step) {
		try (var context = start()) {
			for (String system : systems()) {
				stubStepRetry(system, step);
				OrderLocation order = create(client(context), system);
				OrderView completed = awaitSent(client(context), order);
				assertThat(completed.history()).hasSize(4);
				assertThat(completed.history()).filteredOn(result ->
						result.outcome().equals("FAILURE")).hasSize(1);
				verifyPostAttempts(system, order, "source", "REGISTER_ORDER",
						step == OrderStep.REGISTER_ORDER ? 2 : 1);
				verifyPostAttempts(system, order, "destination", "SEND_ORDER",
						step == OrderStep.SEND_ORDER ? 2 : 1);
				WIREMOCK.verify(step == OrderStep.GET_ORDER ? 2 : 1, getRequestedFor(urlEqualTo(
						"/" + system + "/source/orders/" + order.id())));
			}
		}
	}

	@Test
	void permanentHttpErrorDoesNotCreateChildTask() {
		try (var context = start()) {
			for (String system : systems()) {
				WIREMOCK.stubFor(post(urlEqualTo("/" + system + "/source/orders"))
						.willReturn(aResponse().withStatus(400)));
				OrderLocation order = create(client(context), system);
				await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
					OrderView failed = read(client(context), order);
					assertThat(failed.status()).isEqualTo("FAILED");
					assertThat(failed.history()).hasSize(1);
					assertThat(failed.history().getFirst().error()).isEqualTo("HTTP 400");
				});
				WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/" + system + "/source/orders")));
				WIREMOCK.verify(0, getRequestedFor(
						urlPathMatching("/" + system + "/source/orders/.*")));
			}
		}
	}

	@Test
	void exhaustedRetriesPersistFailedOrder() {
		try (var context = start("--orders.max-retries=1")) {
			for (String system : systems()) {
				WIREMOCK.stubFor(post(urlEqualTo("/" + system + "/source/orders"))
						.willReturn(aResponse().withStatus(503)));
				OrderLocation order = create(client(context), system);
				await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
					OrderView failed = read(client(context), order);
					assertThat(failed.status()).isEqualTo("FAILED");
					assertThat(failed.history()).hasSize(2);
				});
				WIREMOCK.verify(2, postRequestedFor(urlEqualTo("/" + system + "/source/orders")));
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"externalId\":", "{\"externalId\":\"\"}"})
	void invalidRegistrationResponseFailsWithoutRetries(String responseBody) {
		try (var context = start()) {
			for (String system : systems()) {
				WIREMOCK.stubFor(post(urlEqualTo("/" + system + "/source/orders"))
						.willReturn(aResponse().withStatus(200)
								.withHeader("Content-Type", "application/json")
								.withBody(responseBody)));
				OrderLocation order = create(client(context), system);
				await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
					OrderView failed = read(client(context), order);
					assertThat(failed.status()).isEqualTo("FAILED");
					assertThat(failed.history()).hasSize(1);
					assertThat(failed.history().getFirst().error())
							.isEqualTo("Invalid external response");
				});
				WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/" + system + "/source/orders")));
			}
		}
	}

	@Test
	void orderAndInitialTaskRollBackTogether() {
		try (var context = start("--db-scheduler.polling-interval=1h")) {
			context.getBean(Scheduler.class).stop();
			var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
			for (String system : systems()) {
				transaction.executeWithoutResult(status -> {
					invokeState(context, system, "create", "Rolled back");
					status.setRollbackOnly();
				});
				assertThat(count(context, ordersTable(system))).isZero();
				assertThat(count(context, "app.scheduled_tasks")).isZero();
			}
		}
	}

	@Test
	void childAndStateRollBackTogetherAndReplayedParentCannotDuplicateChild() {
		try (var context = start("--db-scheduler.polling-interval=1h")) {
			context.getBean(Scheduler.class).stop();
			var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
			long expectedTasks = 0;
			for (String system : systems()) {
				OrderView created = stateView(context, system, "create", "Atomic transition");
				Object step = stepClass(context, system).getEnumConstants()[0];
				transaction.executeWithoutResult(status -> {
					invokeState(context, system, "complete", created.id(), step, "external-id", "OK");
					status.setRollbackOnly();
				});
				OrderView rolledBack = stateView(context, system, "get", created.id());
				assertThat(rolledBack.status()).isEqualTo("NEW");
				assertThat(rolledBack.history()).isEmpty();
				assertThat(count(context, "app.scheduled_tasks")).isEqualTo(++expectedTasks);
				invokeState(context, system, "complete", created.id(), step, "external-id", "OK");
				invokeState(context, system, "complete", created.id(), step, "external-id", "OK");
				assertThat(stateView(context, system, "get", created.id()).history()).hasSize(1);
				assertThat(count(context, "app.scheduled_tasks")).isEqualTo(++expectedTasks);
			}
		}
	}

	@Test
	void restartResumesPendingAndRecoversStalePickedExecutions() {
		List<OrderLocation> pending = new ArrayList<>();
		try (var context = start("--db-scheduler.polling-interval=1h")) {
			context.getBean(Scheduler.class).stop();
			for (String system : systems()) {
				pending.add(create(client(context), system));
				OrderLocation progressed = create(client(context), system);
				pending.add(progressed);
				String workflowClass = systemType(system, "service", "OrderWorkflow");
				Object workflow = context.getBean(
						ClassUtils.resolveClassName(workflowClass, context.getClassLoader()));
				ReflectionTestUtils.invokeMethod(workflow, "execute", progressed.id(),
						stepClass(context, system).getEnumConstants()[0]);
				assertThat(read(client(context), progressed).status()).isEqualTo("REGISTERED");
			}
			JdbcTemplate database = context.getBean(JdbcTemplate.class);
			database.update("UPDATE app.scheduled_tasks SET picked = true, "
					+ "picked_by = 'crashed-process', last_heartbeat = now() - interval '1 hour'");
		}
		try (var context = start()) {
			for (OrderLocation order : pending) {
				awaitSent(client(context), order);
			}
		}
	}

	@Test
	void replayAfterSuccessfulChainDoesNotRepeatHttpOrCreateTasks() {
		try (var context = start()) {
			for (String system : systems()) {
				OrderLocation order = create(client(context), system);
				awaitSent(client(context), order);
				Scheduler scheduler = context.getBean(Scheduler.class);
				scheduler.scheduleIfNotExists(TaskDescriptor.of(prefix(system) + "REGISTER_ORDER")
						.instance(order.id().toString()).scheduledTo(Instant.now()));
				await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
						assertThat(count(context, "app.scheduled_tasks")).isZero());
				assertThat(read(client(context), order).history()).hasSize(3);
				WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/" + system + "/source/orders")));
				WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/" + system + "/destination/orders")));
			}
		}
	}

	@Test
	void validatesRequestsAndReturnsNotFound() {
		try (var context = start()) {
			RestClient client = client(context);
			assertThat(client.post().uri("/api/orders").body(Map.of("description", " "))
					.<Integer>exchange((request, response) -> response.getStatusCode().value())).isEqualTo(400);
			assertThat(client.get().uri("/api/orders/" + UUID.randomUUID())
					.<Integer>exchange((request, response) -> response.getStatusCode().value())).isEqualTo(404);
		}
	}

	private ConfigurableApplicationContext start(String... overrides) {
		List<String> arguments = new ArrayList<>(List.of(
				"--server.port=0",
				"--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
				"--spring.datasource.username=" + POSTGRES.getUsername(),
				"--spring.datasource.password=" + POSTGRES.getPassword(),
				"--orders.base-url=" + WIREMOCK.baseUrl(),
				"--orders.extension.kz.base-url=" + WIREMOCK.baseUrl(),
				"--orders.extension.az.base-url=" + WIREMOCK.baseUrl(),
				"--orders.retry-delay=100ms",
				"--db-scheduler.polling-interval=100ms",
				"--db-scheduler.heartbeat-interval=200ms",
				"--db-scheduler.missed-heartbeats-limit=4",
				"--logging.level.root=WARN"));
		// Keep exactly one value per property; command-line duplicates become comma-separated.
		for (String override : overrides) {
			String property = override.substring(0, override.indexOf('=') + 1);
			arguments.removeIf(argument -> argument.startsWith(property));
			arguments.add(override);
		}
		return new SpringApplicationBuilder(DemoApplication.class).run(arguments.toArray(String[]::new));
	}

	private RestClient client(ConfigurableApplicationContext context) {
		int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
		return RestClient.create("http://localhost:" + port);
	}

	private OrderLocation create(RestClient client, String system) {
		String endpoint = endpoint(system);
		var response = client.post().uri(endpoint).body(Map.of("description", "Demo order"))
				.retrieve().toEntity(OrderView.class);
		assertThat(response.getStatusCode().value()).isEqualTo(202);
		assertThat(response.getHeaders().getLocation()).isNotNull();
		return new OrderLocation(response.getBody().id(), response.getHeaders().getLocation().toString());
	}

	private OrderView read(RestClient client, OrderLocation order) {
		return client.get().uri(order.location()).retrieve().body(OrderView.class);
	}

	private OrderView awaitSent(RestClient client, OrderLocation order) {
		await().atMost(Duration.ofSeconds(25)).untilAsserted(() ->
				assertThat(read(client, order).status()).isEqualTo("SENT"));
		OrderView completed = read(client, order);
		assertThat(completed.history().stream().filter(result -> result.outcome().equals("SUCCESS")))
				.hasSize(3);
		assertThat(completed.payload()).contains(order.id().toString());
		return completed;
	}

	private void stubStepRetry(String system, OrderStep step) {
		String endpoint = "/" + system
				+ (step == OrderStep.SEND_ORDER ? "/destination/orders" : "/source/orders");
		var request = step == OrderStep.GET_ORDER
				? get(urlPathMatching(endpoint + "/.*")) : post(urlEqualTo(endpoint));
		String body = step == OrderStep.REGISTER_ORDER
				? "{\"externalId\":\"{{jsonPath request.body '$.id'}}\"}"
				: "{\"id\":\"{{request.pathSegments.[3]}}\"}";
		WIREMOCK.stubFor(request.inScenario(system)
				.whenScenarioStateIs("Started").willSetStateTo("healthy")
				.willReturn(aResponse().withStatus(503)));
		var successRequest = step == OrderStep.GET_ORDER
				? get(urlPathMatching(endpoint + "/.*")) : post(urlEqualTo(endpoint));
		WIREMOCK.stubFor(successRequest.inScenario(system)
				.whenScenarioStateIs("healthy")
				.willReturn(aResponse().withStatus(step == OrderStep.SEND_ORDER ? 204 : 200)
						.withHeader("Content-Type", "application/json").withBody(body)
						.withTransformers("response-template")));
	}

	private void verifyPostAttempts(String system, OrderLocation order,
			String destination, String taskName, int attempts) {
		WIREMOCK.verify(attempts, postRequestedFor(urlEqualTo(
				"/" + system + "/" + destination + "/orders"))
				.withHeader("Idempotency-Key", equalTo(prefix(system) + taskName + ":" + order.id())));
	}

	private Object invokeState(ConfigurableApplicationContext context, String system,
			String method, Object... arguments) {
		if (method.equals("create") && !system.equals("main")) {
			Class<?> commandType = ClassUtils.resolveClassName(
					systemType(system, "model.dto", "OrderCommand"), context.getClassLoader());
			arguments = new Object[] {org.springframework.beans.BeanUtils.instantiateClass(
					commandType.getDeclaredConstructors()[0], arguments)};
		}
		String serviceClass = systemType(system, "service", "OrderStateService");
		Object service = context.getBean(
				ClassUtils.resolveClassName(serviceClass, context.getClassLoader()));
		return ReflectionTestUtils.invokeMethod(service, method, arguments);
	}

	private OrderView stateView(ConfigurableApplicationContext context, String system,
			String method, Object... arguments) {
		Object result = invokeState(context, system, method, arguments);
		UUID orderId = ReflectionTestUtils.invokeMethod(result, "id");
		OrderProcessor processor = context.getBeansOfType(OrderProcessor.class).values().stream()
				.filter(candidate -> candidate.code().equals(system)).findFirst().orElseThrow();
		return context.getBean(ObjectMapper.class)
				.convertValue(processor.get(orderId), OrderView.class);
	}

	private Class<?> stepClass(ConfigurableApplicationContext context, String system) {
		return ClassUtils.resolveClassName(
				systemType(system, "tasks", "OrderStep"), context.getClassLoader());
	}

	private void assertOverlappingRegistrations(String system) {
		var startTimes = WIREMOCK.findAll(
				postRequestedFor(urlEqualTo("/" + system + "/source/orders"))).stream()
				.map(request -> request.getLoggedDate().getTime()).sorted().toList();
		assertThat(startTimes).hasSize(6);
		boolean overlap = false;
		for (int requestIndex = 1; requestIndex < startTimes.size(); requestIndex++) {
			overlap |= startTimes.get(requestIndex) - startTimes.get(requestIndex - 1) < 600;
		}
		assertThat(overlap).as("HTTP requests overlap while scheduler workers execute").isTrue();
	}

	private long count(ConfigurableApplicationContext context, String table) {
		return context.getBean(JdbcTemplate.class)
				.queryForObject("SELECT count(*) FROM " + table, Long.class);
	}

	private void assertPackagedExtensions() throws Exception {
		try (var archive = new ZipFile("target/demo-app-1.0.0-SNAPSHOT.jar")) {
			var libraries = archive.stream().map(entry -> entry.getName()).toList();
			for (Extension extension : EXTENSIONS) {
				assertThat(libraries.stream().anyMatch(name -> name.startsWith(
						"BOOT-INF/lib/orders-extension-" + extension.country() + "-")))
						.as("Packaged extension %s", extension.country()).isEqualTo(extension.enabled());
				assertThat(libraries.stream().anyMatch(name -> name.startsWith(
						"BOOT-INF/lib/demo-" + extension.country() + "-adapter-")))
						.as("Packaged adapter %s", extension.country()).isEqualTo(extension.enabled());
			}
			assertThat(libraries).noneMatch(name -> name.contains("wiremock"));
		}
	}

	private void assertExtensionConfiguration(
			ConfigurableApplicationContext context, Extension extension) {
		JdbcTemplate database = context.getBean(JdbcTemplate.class);
		assertThat(database.queryForObject(
				"SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?",
				Integer.class, extension.schema())).isEqualTo(extension.enabled() ? 1 : 0);
		if (extension.enabled()) {
			assertThat(context.getBeansOfType(ClassUtils.resolveClassName(
					extension.type("config", "OrdersAutoConfiguration"), context.getClassLoader())))
					.hasSize(1);
			assertThat(database.queryForObject("SELECT count(*) FROM " + extension.schema()
					+ "." + extension.historyTable() + " WHERE success", Integer.class)).isEqualTo(2);
		} else {
			assertThat(client(context).post().uri(endpoint(extension.country()))
					.body(Map.of("description", "Unavailable")).<Integer>exchange(
							(request, response) -> response.getStatusCode().value())).isEqualTo(404);
		}
		if (extension.country().equals("kz")) {
			assertLegacyKzRoute(context, extension.enabled());
		}
	}

	private void assertLegacyKzRoute(ConfigurableApplicationContext context, boolean enabled) {
		if (enabled) {
			OrderLocation order = create(client(context), "kz");
			OrderView legacy = client(context).get().uri("/api/extension/orders/" + order.id())
					.retrieve().body(OrderView.class);
			assertThat(legacy.id()).isEqualTo(order.id());
		} else {
			assertThat(client(context).post().uri("/api/extension/orders")
					.body(Map.of("description", "Unavailable")).<Integer>exchange(
							(request, response) -> response.getStatusCode().value())).isEqualTo(404);
		}
	}

	private List<String> systems() {
		List<String> systems = new ArrayList<>(List.of("main"));
		EXTENSIONS.stream().filter(Extension::enabled).map(Extension::country).forEach(systems::add);
		return systems;
	}

	private Extension extension(String country) {
		return EXTENSIONS.stream().filter(extension -> extension.country().equals(country))
				.findFirst().orElseThrow();
	}

	private String endpoint(String system) {
		return system.equals("main") ? "/api/orders" : "/api/extension/" + system + "/orders";
	}

	private String ordersTable(String system) {
		if (system.equals("main")) {
			return "app.orders";
		}
		Extension extension = extension(system);
		return extension.schema() + "." + extension.ordersTable();
	}

	private String systemType(String system, String layer, String suffix) {
		return system.equals("main") ? "com.example.demo." + layer + "." + suffix
				: extension(system).type(layer, suffix);
	}

	private String prefix(String system) {
		return switch (system) {
			case "main" -> "";
			case "kz" -> "EXT_";
			case "az" -> "AZ_";
			default -> throw new IllegalArgumentException("Unknown system: " + system);
		};
	}

	private record Extension(String country, String classPrefix, String schema,
			String ordersTable, String historyTable) {
		boolean enabled() {
			return ClassUtils.isPresent(type("config", "OrdersAutoConfiguration"),
					OrderChainsIT.class.getClassLoader());
		}

		String type(String layer, String suffix) {
			return "com.example.extension." + country + "." + layer + "." + classPrefix + suffix;
		}
	}

	private record OrderLocation(UUID id, String location) {}
}


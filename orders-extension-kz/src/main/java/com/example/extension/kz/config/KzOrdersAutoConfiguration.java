package com.example.extension.kz.config;

import com.example.extension.kz.controller.KzOrderController;
import com.example.extension.kz.model.entity.KzOrder;
import com.example.extension.kz.repository.KzOrderRepository;
import com.example.extension.kz.service.KzOrderGateway;
import com.example.extension.kz.service.KzOrderStateService;
import com.example.extension.kz.service.KzOrderWorkflow;
import com.example.extension.kz.tasks.KzOrderTasks;
import java.net.http.HttpClient;
import java.time.Duration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@AutoConfiguration(after = DataSourceAutoConfiguration.class,
		before = {HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class})
@EntityScan(basePackageClasses = KzOrder.class)
@EnableJpaRepositories(basePackageClasses = KzOrderRepository.class)
@Import({KzOrderStateService.class, KzOrderWorkflow.class,
		KzOrderController.class, KzOrderTasks.class})
public class KzOrdersAutoConfiguration {
	@Bean
	Flyway kzFlyway(DataSource dataSource) {
		return Flyway.configure().dataSource(dataSource)
				.schemas("orders_extension").defaultSchema("orders_extension")
				.table("extension_schema_history")
				.locations("classpath:db/migration/extension/kz").load();
	}

	@Bean
	FlywayMigrationInitializer kzFlywayInitializer(
			@Qualifier("kzFlyway") Flyway flyway) {
		return new FlywayMigrationInitializer(flyway);
	}

	@Bean
	KzOrderGateway kzOrderGateway(RestClient.Builder builder,
			@Value("${orders.extension.kz.base-url:http://localhost:8081}") String baseUrl) {
		var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(2)).build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		return new KzOrderGateway(builder.clone().baseUrl(baseUrl)
				.requestFactory(requestFactory).build());
	}
}

package com.example.extension.config;

import com.example.extension.controller.ExtensionOrderController;
import com.example.extension.model.entity.ExtensionOrder;
import com.example.extension.repository.ExtensionOrderRepository;
import com.example.extension.service.ExtensionOrderGateway;
import com.example.extension.service.ExtensionOrderStateService;
import com.example.extension.service.ExtensionOrderWorkflow;
import com.example.extension.tasks.ExtensionOrderTasks;
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
@EntityScan(basePackageClasses = ExtensionOrder.class)
@EnableJpaRepositories(basePackageClasses = ExtensionOrderRepository.class)
@Import({ExtensionOrderStateService.class, ExtensionOrderWorkflow.class,
		ExtensionOrderController.class, ExtensionOrderTasks.class})
public class OrdersExtensionAutoConfiguration {
	@Bean
	Flyway extensionFlyway(DataSource dataSource) {
		return Flyway.configure().dataSource(dataSource)
				.schemas("orders_extension").defaultSchema("orders_extension")
				.table("extension_schema_history")
				.locations("classpath:db/migration/extension").load();
	}

	@Bean
	FlywayMigrationInitializer extensionFlywayInitializer(
			@Qualifier("extensionFlyway") Flyway flyway) {
		return new FlywayMigrationInitializer(flyway);
	}

	@Bean
	ExtensionOrderGateway extensionOrderGateway(RestClient.Builder builder,
			@Value("${orders.extension.base-url:http://localhost:8081}") String baseUrl) {
		var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(2)).build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		return new ExtensionOrderGateway(builder.clone().baseUrl(baseUrl)
				.requestFactory(requestFactory).build());
	}
}


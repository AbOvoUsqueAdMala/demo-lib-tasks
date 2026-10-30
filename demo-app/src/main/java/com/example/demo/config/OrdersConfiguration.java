package com.example.demo.config;

import com.example.demo.service.OrderGateway;
import com.example.demo.service.OrderStateService;
import com.example.demo.service.OrderWorkflow;
import java.net.http.HttpClient;
import java.time.Duration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@Import({OrderStateService.class, OrderWorkflow.class})
public class OrdersConfiguration {
	@Bean
	Flyway appFlyway(DataSource dataSource) {
		return Flyway.configure().dataSource(dataSource)
				.schemas("app").defaultSchema("app")
				.table("app_schema_history").locations("classpath:db/migration/app").load();
	}

	@Bean
	FlywayMigrationInitializer appFlywayInitializer(@Qualifier("appFlyway") Flyway flyway) {
		return new FlywayMigrationInitializer(flyway);
	}

	@Bean
	OrderGateway orderGateway(RestClient.Builder builder,
			@Value("${orders.base-url:http://localhost:8081}") String baseUrl) {
		var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(2)).build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		return new OrderGateway(builder.clone().baseUrl(baseUrl)
				.requestFactory(requestFactory).build());
	}
}


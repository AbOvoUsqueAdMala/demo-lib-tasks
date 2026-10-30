package com.example.adapters.az.config;

import com.example.adapters.az.service.AzOrderAdapter;
import com.example.adapters.az.service.mapping.AzOrderMapper;
import com.example.extension.az.config.AzOrdersAutoConfiguration;
import com.example.extension.az.service.AzOrderStateService;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AzOrdersAutoConfiguration.class)
public class AzOrderAdapterAutoConfiguration {
	@Bean
	AzOrderMapper azOrderMapper() {
		return Mappers.getMapper(AzOrderMapper.class);
	}

	@Bean
	AzOrderAdapter azOrderAdapter(
			AzOrderStateService orderStateService, AzOrderMapper orderMapper) {
		return new AzOrderAdapter(orderStateService, orderMapper);
	}
}

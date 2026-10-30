package com.example.adapters.kz.config;

import com.example.adapters.kz.service.KzOrderAdapter;
import com.example.adapters.kz.service.mapping.KzOrderMapper;
import com.example.extension.kz.config.KzOrdersAutoConfiguration;
import com.example.extension.kz.service.KzOrderStateService;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = KzOrdersAutoConfiguration.class)
public class KzOrderAdapterAutoConfiguration {
	@Bean
	KzOrderMapper kzOrderMapper() {
		return Mappers.getMapper(KzOrderMapper.class);
	}

	@Bean
	KzOrderAdapter kzOrderAdapter(
			KzOrderStateService orderStateService, KzOrderMapper orderMapper) {
		return new KzOrderAdapter(orderStateService, orderMapper);
	}
}

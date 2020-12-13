package com.eternal_search.geoip.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.jackson.ObjectMapperCustomizer;

import javax.inject.Singleton;

@Singleton
public class ObjectMapperConfig implements ObjectMapperCustomizer {
	@Override
	public void customize(ObjectMapper objectMapper) {
		objectMapper
				.setSerializationInclusion(JsonInclude.Include.NON_NULL)
				.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
	}
}

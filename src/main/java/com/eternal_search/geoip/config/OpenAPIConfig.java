package com.eternal_search.geoip.config;

import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.RequestBody;

import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.stream.Collectors;

public class OpenAPIConfig implements OASFilter {
	@Override
	public void filterOpenAPI(OpenAPI openAPI) {
		openAPI.getComponents().removeSchema("InputStream");
		openAPI.getComponents().removeSchema("InputPart");
	}
	
	@Override
	public RequestBody filterRequestBody(RequestBody requestBody) {
		Optional.ofNullable(requestBody.getContent())
				.map(content -> content.getMediaType(MediaType.MULTIPART_FORM_DATA))
				.map(org.eclipse.microprofile.openapi.models.media.MediaType::getSchema)
				.filter(schema -> schema.getProperties() != null)
				.ifPresent(schema -> schema.setProperties(
						schema.getProperties().entrySet().stream()
								.map(entry -> {
									boolean isBinary = false;
									if (entry.getValue().getRef() != null) {
										switch (entry.getValue().getRef()) {
											case "#/components/schemas/InputStream":
											case "#/components/schemas/InputPart":
												isBinary = true;
												break;
										}
									} else if (entry.getValue().getType() == Schema.SchemaType.ARRAY) {
										if (entry.getValue().getItems().getType() == Schema.SchemaType.STRING) {
											if ("byte".equals(entry.getValue().getItems().getFormat())) {
												isBinary = true;
											}
										}
									}
									if (isBinary) {
										return new AbstractMap.SimpleImmutableEntry<>(
												entry.getKey(),
												OASFactory.createSchema()
														.type(Schema.SchemaType.STRING)
														.format("binary")
										);
									}
									return entry;
								})
								.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
				));
		return requestBody;
	}
}

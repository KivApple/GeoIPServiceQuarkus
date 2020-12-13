package com.eternal_search.geoip.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class GeoIPStatusDTO {
	private Instant updatedAt;
	
	private boolean updating;
}

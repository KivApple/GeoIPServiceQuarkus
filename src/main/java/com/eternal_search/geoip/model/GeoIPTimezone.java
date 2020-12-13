package com.eternal_search.geoip.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeoIPTimezone {
	private Long id;
	
	private String name;
}

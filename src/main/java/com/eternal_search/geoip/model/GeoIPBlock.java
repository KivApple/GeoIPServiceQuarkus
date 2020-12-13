package com.eternal_search.geoip.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeoIPBlock {
	private String start;
	private String stop;
	private Long locationId;
	private String postalCode;
	private Double latitude;
	private Double longitude;
	private Integer accuracyRadius;
	private Boolean isAnonymousProxy;
	private Boolean isSatelliteProvider;
}

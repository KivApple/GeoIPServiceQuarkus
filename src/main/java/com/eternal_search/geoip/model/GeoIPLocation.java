package com.eternal_search.geoip.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeoIPLocation {
	private Long id;
	private String localeCode;
	private Long parentId;
	private GeoIPLocationLevel level;
	private String name;
	private String code;
	private Boolean isInEuropeanUnion;
	private Long timezoneId;
}

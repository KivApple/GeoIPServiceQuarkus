package com.eternal_search.geoip.model.dto;

import com.eternal_search.geoip.model.GeoIPLocationLevel;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeoIPLocationDTO {
	private Long id;
	private GeoIPLocationLevel level;
	private String name;
	private String code;
	private GeoIPLocationDTO parent;
}

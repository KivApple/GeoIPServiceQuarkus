package com.eternal_search.geoip.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeoIPAddressDTO {
	private String address;
	private String localeCode;
	private String postalCode;
	private Double latitude;
	private Double longtiude;
	private Integer accuracyRadius;
	private Boolean isAnonymousProxy;
	private Boolean isSatelliteProvider;
	private Boolean isInEuropeanUnion;
	private String timezone;
	private GeoIPLocationDTO location;
}

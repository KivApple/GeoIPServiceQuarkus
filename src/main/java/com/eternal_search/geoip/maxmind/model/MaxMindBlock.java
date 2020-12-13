package com.eternal_search.geoip.maxmind.model;

import lombok.Data;

@Data
public class MaxMindBlock {
	// Country, city and ASM
	private String network;
	// Country and city
	private Long geonameId;
	private Long registeredCountryGeonameId;
	private Long representedCountryGeonameId;
	private Boolean isAnonymousProxy;
	private Boolean isSatelliteProvider;
	// City
	private String postalCode;
	private Double latitude;
	private Double longitude;
	private Integer accuracyRadius;
	// ASN
	private String autonomousSystemNumber;
	private String autonomousSystemOrganization;
}

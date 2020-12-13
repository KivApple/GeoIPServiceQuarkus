package com.eternal_search.geoip.maxmind.model;

import lombok.Data;

@Data
public class MaxMindLocation {
	// Country and city
	private Long geonameId;
	private String localeCode;
	private String continentCode;
	private String continentName;
	private String countryIsoCode;
	private String countryName;
	private Boolean isInEuropeanUnion;
	// City
	private String subdivision1IsoCode;
	private String subdivision1Name;
	private String subdivision2IsoCode;
	private String subdivision2Name;
	private String cityName;
	private String metroCode;
	private String timeZone;
}

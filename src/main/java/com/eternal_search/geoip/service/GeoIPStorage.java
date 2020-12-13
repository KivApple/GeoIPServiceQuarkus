package com.eternal_search.geoip.service;

import com.eternal_search.geoip.model.GeoIPBlock;
import com.eternal_search.geoip.model.GeoIPLocation;
import com.eternal_search.geoip.model.GeoIPTimezone;
import com.eternal_search.geoip.model.dto.GeoIPAddressDTO;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.function.Function;

public interface GeoIPStorage {
	Multi<String> findLocales();
	
	Uni<GeoIPAddressDTO> findAddress(String address, String localeCode);
	
	<T> Uni<T> update(Function<Updater, Uni<T>> updaterFunction);
	
	Uni<Instant> findUpdatedAt();
	
	interface Updater {
		Uni<Long> insertBlocks(Multi<GeoIPBlock> blocks);
		
		Uni<Long> insertTimezones(Multi<GeoIPTimezone> timezones);
		
		Uni<Long> insertLocations(Multi<GeoIPLocation> locations);
		
		Uni<Long> insertLocales(Multi<String> locales);
	}
}

package com.eternal_search.geoip.service;

public interface GeoIPUpdater {
	void launchUpdate();
	
	boolean isUpdating();
}

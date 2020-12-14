package com.eternal_search.geoip.service;

import java.io.InputStream;

public interface GeoIPUpdater {
	void launchUpdate();
	
	void launchUpdate(InputStream inputStream);
	
	boolean isUpdating();
}

package com.eternal_search.geoip.model.dto;

import lombok.Data;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;

import javax.ws.rs.FormParam;

@Data
public class GeoIPUpdateDTO {
	@FormParam("file")
	private InputPart file;
}

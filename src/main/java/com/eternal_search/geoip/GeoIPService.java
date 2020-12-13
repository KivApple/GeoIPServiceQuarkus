package com.eternal_search.geoip;

import com.eternal_search.geoip.model.dto.GeoIPAddressDTO;
import com.eternal_search.geoip.model.dto.GeoIPStatusDTO;
import com.eternal_search.geoip.service.GeoIPStorage;
import com.eternal_search.geoip.service.GeoIPUpdater;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.annotations.jaxrs.PathParam;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/geoip")
@Produces(MediaType.APPLICATION_JSON)
public class GeoIPService {
	@Inject
	GeoIPStorage geoIPStorage;
	
	@Inject
	GeoIPUpdater geoIPUpdater;
	
	@GET
	@Path("/address/{address}/{localeCode}")
	public Uni<GeoIPAddressDTO> find(@PathParam String address, @PathParam String localeCode) {
		return geoIPStorage.findAddress(address, localeCode)
				.flatMap(result -> result != null ?
					Uni.createFrom().item(result) :
					Uni.createFrom().failure(new WebApplicationException("Address not found", 404))
				);
	}
	
	@GET
	@Path("/locales")
	public Multi<String> locales() {
		return geoIPStorage.findLocales();
	}
	
	@POST
	@Path("/update")
	public void update() {
		geoIPUpdater.launchUpdate();
	}
	
	@GET
	@Path("/status")
	public Uni<GeoIPStatusDTO> status() {
		return geoIPStorage.findUpdatedAt().map(updatedAt ->
				GeoIPStatusDTO.builder()
						.updatedAt(updatedAt)
						.updating(geoIPUpdater.isUpdating())
						.build()
		);
	}
}

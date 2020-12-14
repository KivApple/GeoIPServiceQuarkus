package com.eternal_search.geoip;

import com.eternal_search.geoip.model.dto.GeoIPAddressDTO;
import com.eternal_search.geoip.model.dto.GeoIPStatusDTO;
import com.eternal_search.geoip.model.dto.GeoIPUpdateDTO;
import com.eternal_search.geoip.service.GeoIPStorage;
import com.eternal_search.geoip.service.GeoIPUpdater;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import lombok.SneakyThrows;
import org.jboss.resteasy.annotations.jaxrs.PathParam;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;

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
	@Consumes("text/plain")
	public void update() {
		geoIPUpdater.launchUpdate();
	}
	
	@POST
	@Path("/update/file")
	@Consumes("multipart/form-data")
	@SneakyThrows(IOException.class)
	public void updateFromFile(@MultipartForm GeoIPUpdateDTO update) {
		geoIPUpdater.launchUpdate(update.getFile().getBody(InputStream.class, null));
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

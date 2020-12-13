package com.eternal_search.geoip.service;

import com.eternal_search.geoip.maxmind.MaxMindBlockParser;
import com.eternal_search.geoip.model.GeoIPBlock;
import com.eternal_search.geoip.model.GeoIPLocation;
import com.eternal_search.geoip.model.GeoIPLocationLevel;
import com.eternal_search.geoip.model.GeoIPTimezone;
import com.eternal_search.geoip.model.dto.GeoIPAddressDTO;
import com.eternal_search.geoip.model.dto.GeoIPLocationDTO;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Transaction;
import io.vertx.mutiny.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
@JBossLog
public class GeoIPDatabase implements GeoIPStorage {
	@Inject
	PgPool client;
	
	@Override
	public Multi<String> findLocales() {
		return client.preparedQuery("SELECT code FROM geoip_locales").execute()
				.onItem().transformToMulti(rows -> Multi.createFrom().iterable(rows))
				.map(row -> row.getString(0));
	}
	
	@Override
	public Uni<GeoIPAddressDTO> findAddress(String address, String localeCode) {
		return client.preparedQuery(
				"SELECT " +
						"b.postal_code, b.latitude, b.longitude, b.accuracy_radius, " +
						"b.is_anonymous_proxy, b.is_satellite_provider, l.is_in_european_union, t.name, l.id " +
						"FROM geoip_blocks b " +
						"LEFT JOIN geoip_locations l ON l.id = b.location_id AND l.locale_code = $1 " +
						"LEFT JOIN geoip_timezones t ON t.id = l.timezone_id " +
						"WHERE $2 BETWEEN b.start AND b.stop LIMIT 1"
		).execute(Tuple.of(localeCode, MaxMindBlockParser.addressToString(address)))
				.onItem().transformToMulti(rows -> Multi.createFrom().iterable(rows))
				.collectItems().first()
				.flatMap(row -> {
					if (row == null) {
						return Uni.createFrom().nullItem();
					}
					return client.preparedQuery(
							"WITH RECURSIVE parents AS (" +
									"SELECT * FROM geoip_locations " +
									"WHERE id = $1 AND locale_code = $2 " +
									"UNION SELECT p.* FROM geoip_locations p " +
									"INNER JOIN parents c " +
									"ON c.parent_id = p.id and c.locale_code = p.locale_code " +
									") SELECT " +
									"id, name, code, level " +
									"FROM parents"
					).execute(Tuple.of(row.getLong(8), localeCode))
							.onItem().transformToMulti(rows -> Multi.createFrom().iterable(rows))
							.collectItems().asList()
							.map(tree -> {
								GeoIPLocationDTO location = null;
								GeoIPLocationDTO current = null;
								for (Row treeRow : tree) {
									GeoIPLocationDTO parent = GeoIPLocationDTO.builder()
											.id(treeRow.getLong(0))
											.name(treeRow.getString(1))
											.code(treeRow.getString(2))
											.level(GeoIPLocationLevel.valueOf(treeRow.getString(3)))
											.build();
									if (current == null) {
										location = parent;
									} else {
										current.setParent(parent);
									}
									current = parent;
								}
								return GeoIPAddressDTO.builder()
										.address(address)
										.localeCode(localeCode)
										.postalCode(row.getString(0))
										.latitude(row.getDouble(1))
										.longtiude(row.getDouble(2))
										.accuracyRadius(row.getInteger(3))
										.isAnonymousProxy(row.getBoolean(4))
										.isSatelliteProvider(row.getBoolean(5))
										.isInEuropeanUnion(row.getBoolean(6))
										.timezone(row.getString(7))
										.location(location)
										.build();
							});
				});
	}
	
	private Uni<Void> clear(Transaction transaction) {
		return transaction.preparedQuery(
				"SET CONSTRAINTS geoip_locations_parent_id_locale_code_fkey, geoip_locations_timezone_id_fkey " +
						"DEFERRED"
		).execute()
				.flatMap(result -> {
					log.info("Deleting exiting blocks");
					return transaction.preparedQuery("DELETE FROM geoip_blocks").execute();
				})
				.flatMap(result -> {
					log.info("Deleting exiting locations");
					return transaction.preparedQuery("DELETE FROM geoip_locations").execute();
				})
				.flatMap(result -> {
					log.info("Deleting exiting timezones");
					return transaction.preparedQuery("DELETE FROM geoip_timezones").execute();
				})
				.flatMap(result -> {
					log.info("Deleting exiting locales");
					return transaction.preparedQuery("DELETE FROM geoip_locales").execute();
				})
				.flatMap(result -> Uni.createFrom().voidItem());
	}
	
	@Override
	public <T> Uni<T> update(Function<Updater, Uni<T>> updaterFunction) {
		return client.begin().flatMap(transaction ->
				clear(transaction)
						.flatMap(result -> updaterFunction.apply(new DatabaseUpdater(transaction)))
						.flatMap(result -> transaction.preparedQuery(
								"INSERT INTO geoip_updates (updated_at) VALUES (CURRENT_TIMESTAMP)"
						).execute().map(rows -> result))
						.onItem().call(transaction::commit)
						.onFailure().call(transaction::rollback)
		);
	}
	
	@Override
	public Uni<Instant> findUpdatedAt() {
		return client.preparedQuery(
				"SELECT updated_at FROM geoip_updates WHERE id = (SELECT MAX(id) FROM geoip_updates)"
		).execute().onItem().transformToMulti(rows -> Multi.createFrom().iterable(rows))
				.map(row -> row.getLocalDateTime(0).atOffset(ZoneOffset.UTC).toInstant())
				.collectItems().first();
	}
	
	@RequiredArgsConstructor
	private static class DatabaseUpdater implements Updater {
		private final Transaction transaction;
		
		@Override
		public Uni<Long> insertBlocks(Multi<GeoIPBlock> blockStream) {
			return blockStream.groupItems().intoLists().of(1024)
					.flatMap(blocks -> {
						log.debugf("Going to import %s blocks", blocks.size());
						return transaction.preparedQuery(
								"INSERT INTO geoip_blocks (start, stop, location_id, postal_code, " +
										"latitude, longitude, accuracy_radius, " +
										"is_anonymous_proxy, is_satellite_provider" +
										") VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)"
						).executeBatch(blocks.stream().map(block -> Tuple.tuple(Arrays.asList(
								block.getStart(), block.getStop(), block.getLocationId(), block.getPostalCode(),
								block.getLatitude(), block.getLongitude(), block.getAccuracyRadius(),
								block.getIsAnonymousProxy(), block.getIsSatelliteProvider()
						))).collect(Collectors.toList()))
								.toMulti()
								.map(rows -> (long) blocks.size());
					})
					.collectItems().with(Collectors.summingLong(Long::longValue));
		}
		
		@Override
		public Uni<Long> insertLocations(Multi<GeoIPLocation> locationStream) {
			return locationStream.groupItems().intoLists().of(1024)
					.flatMap(locations -> {
						log.debugf("Going to import %s locations", locations.size());
						return transaction.preparedQuery(
								"INSERT INTO geoip_locations (id, locale_code, parent_id, level, " +
										"name, code, is_in_european_union, timezone_id" +
										") VALUES ($1, $2, $3, $4, $5, $6, $7, $8)"
						).executeBatch(locations.stream().map(location -> Tuple.tuple(Arrays.asList(
								location.getId(), location.getLocaleCode(), location.getParentId(),
								location.getLevel().toString(), location.getName(), location.getCode(),
								location.getIsInEuropeanUnion(), location.getTimezoneId()
						))).collect(Collectors.toList()))
								.toMulti()
								.map(rows -> (long) locations.size());
					})
					.collectItems().with(Collectors.summingLong(Long::longValue));
		}
		
		@Override
		public Uni<Long> insertTimezones(Multi<GeoIPTimezone> timezoneStream) {
			return timezoneStream.groupItems().intoLists().of(1024)
					.flatMap(timezones -> {
						log.debugf("Going to import %s timezones", timezones.size());
						return transaction.preparedQuery(
								"INSERT INTO geoip_timezones (id, name) VALUES ($1, $2) ON CONFLICT DO NOTHING"
						)
								.executeBatch(timezones.stream().map(timezone -> Tuple.of(
										timezone.getId(), timezone.getName()
								)).collect(Collectors.toList()))
								.toMulti()
								.map(rows -> (long) timezones.size());
					})
					.collectItems().with(Collectors.summingLong(Long::longValue));
		}
		
		@Override
		public Uni<Long> insertLocales(Multi<String> localeStream) {
			return localeStream.groupItems().intoLists().of(1024)
					.flatMap(locales -> {
						log.debugf("Going to import %s locales", locales.size());
						return transaction.preparedQuery("INSERT INTO geoip_locales (code) VALUES ($1)")
								.executeBatch(locales.stream().map(Tuple::of).collect(Collectors.toList()))
								.toMulti()
								.map(rows -> (long) locales.size());
					})
					.collectItems().with(Collectors.summingLong(Long::longValue));
		}
	}
}

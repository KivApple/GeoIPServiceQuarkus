package com.eternal_search.geoip.maxmind;

import com.eternal_search.geoip.maxmind.model.MaxMindLocation;
import com.eternal_search.geoip.model.GeoIPLocation;
import com.eternal_search.geoip.model.GeoIPLocationLevel;
import com.eternal_search.geoip.model.GeoIPTimezone;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class MaxMindLocationParser implements Collector<
		MaxMindLocation,
		Map<String, MaxMindLocationParser.LocationTreeNode>,
		MaxMindLocationParser.Result
> {
	private static final Set<Characteristics> CHARACTERISTICS = Collections.emptySet();
	private static final List<LocationPathItemExtractor> PATH_ITEM_EXTRACTORS = Arrays.asList(
			new LocationPathItemExtractor(
					GeoIPLocationLevel.CONTINENT,
					MaxMindLocation::getContinentName,
					MaxMindLocation::getContinentCode
			),
			new LocationPathItemExtractor(
					GeoIPLocationLevel.COUNTRY,
					MaxMindLocation::getCountryName,
					MaxMindLocation::getCountryIsoCode
			),
			new LocationPathItemExtractor(
					GeoIPLocationLevel.SUBDIVISION_1,
					MaxMindLocation::getSubdivision1Name,
					MaxMindLocation::getSubdivision1IsoCode
			),
			new LocationPathItemExtractor(
					GeoIPLocationLevel.SUBDIVISION_2,
					MaxMindLocation::getSubdivision2Name,
					MaxMindLocation::getSubdivision2IsoCode
			),
			new LocationPathItemExtractor(
					GeoIPLocationLevel.CITY,
					MaxMindLocation::getCityName,
					null
			),
			new LocationPathItemExtractor(
					GeoIPLocationLevel.METRO,
					null,
					MaxMindLocation::getMetroCode
			)
	);
	
	@Override
	public Supplier<Map<String, LocationTreeNode>> supplier() {
		return HashMap::new;
	}
	
	@Override
	public BiConsumer<Map<String, LocationTreeNode>, MaxMindLocation> accumulator() {
		return (accumulator, location) -> {
			StringBuilder pathBuilder = new StringBuilder();
			LocationTreeNode current = null;
			String currentPath = null;
			for (LocationPathItemExtractor extractor : PATH_ITEM_EXTRACTORS) {
				GeoIPLocationLevel level = extractor.getLevel();
				String name = extractor.extractName(location);
				String code = extractor.extractCode(location);
				if (name == null && code == null) continue;
				pathBuilder.append('/');
				pathBuilder.append(name);
				pathBuilder.append('|');
				pathBuilder.append(code);
				LocationTreeNode parent = current;
				String parentPath = currentPath;
				currentPath = pathBuilder.toString();
				current = accumulator.computeIfAbsent(currentPath, path ->
						new LocationTreeNode(
								GeoIPLocation.builder()
										.id((long) -Math.abs(path.hashCode()))
										.localeCode(location.getLocaleCode())
										.level(level)
										.name(name)
										.code(code)
										.build(),
								parent,
								parentPath,
								location.getTimeZone()
						)
				);
			}
			assert current != null;
			GeoIPLocation geoIPLocation = current.getLocation();
			geoIPLocation.setId(location.getGeonameId());
			geoIPLocation.setIsInEuropeanUnion(location.getIsInEuropeanUnion());
		};
	}
	
	@Override
	public BinaryOperator<Map<String, LocationTreeNode>> combiner() {
		return (target, source) -> {
			for (Map.Entry<String, LocationTreeNode> entry : source.entrySet()) {
				if (target.containsKey(entry.getKey())) continue;
				LocationTreeNode node = entry.getValue();
				LocationTreeNode existingParent = target.get(node.getParentPath());
				if (existingParent != null) {
					node.setParent(existingParent);
				}
				target.put(entry.getKey(), node);
			}
			return target;
		};
	}
	
	@Override
	public Function<Map<String, LocationTreeNode>, Result> finisher() {
		return accumulator -> {
			Set<String> timezones = new HashSet<>();
			List<GeoIPLocation> locations = accumulator.values().stream().map(node -> {
				if (node.getParent() != null) {
					node.getLocation().setParentId(node.getParent().getLocation().getId());
				}
				timezones.add(node.getTimezone());
				node.getLocation().setTimezoneId((long) Math.abs(node.getTimezone().hashCode()));
				return node.getLocation();
			}).collect(Collectors.toList());
			return new Result(locations, timezones.stream().map(name ->
					GeoIPTimezone.builder()
							.id((long) Math.abs(name.hashCode()))
							.name(name)
							.build()
			).collect(Collectors.toList()));
		};
	}
	
	@Override
	public Set<Characteristics> characteristics() {
		return CHARACTERISTICS;
	}
	
	@Data
	@AllArgsConstructor
	public static class LocationTreeNode {
		private GeoIPLocation location;
		private LocationTreeNode parent;
		private String parentPath;
		private String timezone;
	}
	
	@Data
	@AllArgsConstructor
	public static class Result {
		private Collection<GeoIPLocation> locations;
		private Collection<GeoIPTimezone> timezones;
	}
	
	@Data
	@AllArgsConstructor
	private static class LocationPathItemExtractor {
		private GeoIPLocationLevel level;
		
		private Function<MaxMindLocation, String> nameExtractor;
		
		private Function<MaxMindLocation, String> codeExtractor;
		
		public String extractName(MaxMindLocation location) {
			return nameExtractor != null ? nameExtractor.apply(location) : null;
		}
		
		public String extractCode(MaxMindLocation location) {
			return codeExtractor != null ? codeExtractor.apply(location) : null;
		}
	}
}

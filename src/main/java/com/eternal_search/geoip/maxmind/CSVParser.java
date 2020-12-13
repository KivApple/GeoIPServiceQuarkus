package com.eternal_search.geoip.maxmind;

import io.smallrye.mutiny.Multi;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@JBossLog
@SuppressWarnings("CdiInjectionPointsInspection")
public class CSVParser<T> {
	private static final Map<Class<?>, ValueConverter> CONVERTERS = new HashMap<Class<?>, ValueConverter>() {{
		put(Boolean.class, value -> !value.equals("0"));
		put(Byte.class, Byte::valueOf);
		put(Short.class, Short::valueOf);
		put(Integer.class, Integer::valueOf);
		put(Long.class, Long::valueOf);
		put(Float.class, Float::valueOf);
		put(Double.class, Double::valueOf);
		put(String.class, value -> value);
	}};
	
	private final Scanner scanner;
	private final Constructor<T> constructor;
	private final Method[] setters;
	private final ValueConverter[] converters;
	
	public CSVParser(Scanner scanner, Class<T> cls) {
		this.scanner = scanner;
		try {
			constructor = cls.getDeclaredConstructor();
		} catch (NoSuchMethodException e) {
			throw new RuntimeException("Entry class should have default constructor", e);
		}
		String[] headers = scanner.nextLine().split(",");
		setters = new Method[headers.length];
		converters = new ValueConverter[headers.length];
		for (int i = 0; i < headers.length; i++) {
			String fieldName = snakeCaseToCamelCase(headers[i]);
			String setterName = getSetterMethodName(fieldName);
			Field field;
			Method method;
			try {
				field = cls.getDeclaredField(fieldName);
				method = cls.getDeclaredMethod(setterName, field.getType());
			} catch (NoSuchFieldException e) {
				log.warnf("Class %s doesn't have field %s found in input stream", cls.getName(), fieldName);
				continue;
			} catch (NoSuchMethodException e) {
				log.warnf("Class %s doesn't have setter for field %s", cls.getName(), fieldName);
				continue;
			}
			ValueConverter converter = CONVERTERS.get(field.getType());
			if (converter == null) {
				log.warnf(
						"Unsupported class %s field %s type: %s",
						cls.getName(), fieldName, field.getType().getName()
				);
				continue;
			}
			setters[i] = method;
			converters[i] = converter;
		}
	}
	
	private static final Pattern CSV_PATTERN = Pattern.compile(
			"(?:^|,)\\s*(?:(?:(?=\")\"([^\"].*?)\")|(?:(?!\")(.*?)))(?=,|$)"
	);
	
	private static String[] parseLine(String line) {
		List<String> result = new ArrayList<>();
		Matcher matcher = CSV_PATTERN.matcher(line);
		while (matcher.find()) {
			if (matcher.start(1) >= 0) {
				result.add(matcher.group(1));
			} else {
				result.add(matcher.group(2));
			}
		}
		return result.toArray(new String[0]);
	}
	
	@SneakyThrows({
			IllegalAccessException.class, IllegalArgumentException.class,
			InstantiationException.class, InvocationTargetException.class
	})
	public T readEntry() {
		String[] entry;
		do {
			if (!hasNextEntry()) return null;
			String line = scanner.nextLine();
			entry = parseLine(line);
			if (entry.length != setters.length) {
				log.warnf("Invalid line format: %s", line);
			}
		} while (entry.length != setters.length);
		T entryObj = constructor.newInstance();
		for (int i = 0; i < entry.length; i++) {
			if (entry[i].isEmpty()) continue;
			if (setters[i] == null || converters[i] == null) continue;
			Object value = converters[i].convert(entry[i]);
			setters[i].invoke(entryObj, value);
		}
		return entryObj;
	}
	
	public boolean hasNextEntry() {
		return scanner.hasNextLine();
	}
	
	public Multi<T> multi() {
		return Multi.createFrom().emitter(emitter -> {
			AtomicBoolean isTerminated = new AtomicBoolean();
			emitter.onTermination(() -> isTerminated.set(true));
			while (!isTerminated.get() && hasNextEntry()) {
				T value = readEntry();
				if (value != null) {
					emitter.emit(value);
				}
			}
			log.info("Reached the end of input");
			emitter.complete();
		});
	}
	
	private static String getSetterMethodName(String fieldName) {
		return "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
	}
	
	private static String snakeCaseToCamelCase(String snakeCase) {
		StringBuilder builder = new StringBuilder();
		for (String part : snakeCase.split("_")) {
			if (builder.length() == 0) {
				builder.append(part);
			} else {
				builder.append(Character.toUpperCase(part.charAt(0)));
				builder.append(part, 1, part.length());
			}
		}
		return builder.toString();
	}
	
	private interface ValueConverter {
		Object convert(String value);
	}
}

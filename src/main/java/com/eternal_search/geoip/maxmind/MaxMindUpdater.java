package com.eternal_search.geoip.maxmind;

import com.eternal_search.geoip.maxmind.model.MaxMindBlock;
import com.eternal_search.geoip.maxmind.model.MaxMindLocation;
import com.eternal_search.geoip.service.GeoIPStorage;
import com.eternal_search.geoip.service.GeoIPUpdater;
import io.quarkus.runtime.ShutdownEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.subscription.MultiEmitter;
import io.smallrye.mutiny.tuples.Tuple2;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ApplicationScoped
@JBossLog
public class MaxMindUpdater implements GeoIPUpdater {
	@ConfigProperty(name = "maxmind.download-url")
	String downloadUrl;
	
	@ConfigProperty(name = "maxmind.licence-key")
	Optional<String> licenceKey;
	
	@Inject
	GeoIPStorage storage;
	
	private final AtomicReference<Cancellable> updateCancellable = new AtomicReference<>();
	
	private final AtomicReference<Executor> updateExecutor = new AtomicReference<>();
	
	@Override
	public void launchUpdate() {
		if (updateCancellable.compareAndSet(null, () -> {})) {
			updateCancellable.set(doLaunchUpdate());
		} else {
			log.info("Update is already running");
		}
	}
	
	@Override
	public boolean isUpdating() {
		return updateCancellable.get() != null;
	}
	
	void onStop(@Observes ShutdownEvent event) {
		Optional.ofNullable(updateCancellable.get()).ifPresent(Cancellable::cancel);
	}
	
	@SneakyThrows(MalformedURLException.class)
	private URL buildDownloadUrl() {
		return new URL(licenceKey.map(key -> downloadUrl.replace("@", key)).orElse(downloadUrl));
	}
	
	private InputStream openDownloadStream() throws IOException {
		return new BufferedInputStream(buildDownloadUrl().openStream());
	}
	
	@SneakyThrows(IOException.class)
	private Path downloadArchive(Path filePath) {
		Files.copy(openDownloadStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
		log.infof("Using archive file %s", filePath.toAbsolutePath());
		return filePath;
	}
	
	@SneakyThrows(IOException.class)
	private static void emitZipEntries(
			ZipInputStream zipStream,
			MultiEmitter<? super Tuple2<ZipEntry, InputStream>> emitter
	) {
		AtomicBoolean isTerminated = new AtomicBoolean();
		emitter.onTermination(() -> isTerminated.set(true));
		ZipEntry zipEntry;
		while (!isTerminated.get() && (zipEntry = zipStream.getNextEntry()) != null) {
			log.infof("Found: %s", zipEntry.getName());
			emitter.emit(Tuple2.of(zipEntry, zipStream));
		}
		emitter.complete();
	}
	
	private static Multi<Tuple2<ZipEntry, InputStream>> zipInputStreamToMulti(ZipInputStream zipStream) {
		return Multi.createFrom().emitter(
				emitter -> emitZipEntries(zipStream, emitter),
				BackPressureStrategy.ERROR
		);
	}
	
	private Uni<Void> processBlocks(Scanner scanner, String type, GeoIPStorage.Updater updater) {
		return new CSVParser<>(scanner, MaxMindBlock.class)
				.multi()
				.map(MaxMindBlockParser::parse)
				.onCompletion().invoke(() -> log.infof("Finished parsing %s blocks", type))
				.stage(updater::insertBlocks)
				.invoke(count -> log.infof("Imported %s %s blocks", count, type))
				.flatMap(count -> Uni.createFrom().voidItem());
	}
	
	private Uni<Void> processLocations(Scanner scanner, String localeCode, GeoIPStorage.Updater updater) {
		return new CSVParser<>(scanner, MaxMindLocation.class).multi()
				.collectItems().with(new MaxMindLocationParser())
				.flatMap(result -> {
					log.infof(
							"Parsed %s locations and %s timezones",
							result.getLocations().size(),
							result.getTimezones().size()
					);
					return updater.insertLocales(Multi.createFrom().item(localeCode))
							.flatMap(cnt ->
									updater.insertTimezones(Multi.createFrom().iterable(result.getTimezones()))
											.invoke(count -> log.infof("Imported %s timezones", count))
							)
							.flatMap(cnt ->
									updater.insertLocations(Multi.createFrom().iterable(result.getLocations()))
											.invoke(count -> log.infof("Imported %s locations", count))
							)
							.flatMap(cnt -> Uni.createFrom().voidItem());
				});
	}
	
	private Uni<Void> processFile(String filePath, InputStream inputStream, GeoIPStorage.Updater updater) {
		int fileNameStart = filePath.lastIndexOf("/");
		String fileName = fileNameStart >= 0 ? filePath.substring(fileNameStart + 1) : filePath;
		if (!fileName.toLowerCase().endsWith(".csv")) return Uni.createFrom().voidItem();
		log.infof("Processing: %s", fileName);
		String fileNameWithoutExt = fileName.substring(0, fileName.length() - 4);
		String[] fileNameParts = fileNameWithoutExt.split("-", 4);
		if (fileNameParts.length < 4) return Uni.createFrom().voidItem();
		String type = fileNameParts[2];
		String subtype = fileNameParts[3];
		Scanner scanner = new Scanner(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
		switch (type) {
			case "Blocks":
				return processBlocks(scanner, subtype, updater);
			case "Locations":
				return processLocations(scanner, subtype, updater);
			default:
				log.warnf("Unsupported data type: %s", type);
				return Uni.createFrom().voidItem();
		}
	}
	
	@SneakyThrows(IOException.class)
	private void closeAndDeleteArchive(ZipInputStream zipStream, Path path) {
		log.info("Closing archive");
		zipStream.close();
		Files.deleteIfExists(path);
	}
	
	@SneakyThrows(IOException.class)
	private Uni<Void> performUpdate(Path filePath, GeoIPStorage.Updater updater) {
		ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(Files.newInputStream(filePath)));
		return Multi.createFrom().item(zipStream)
				.emitOn(updateExecutor.get())
				.flatMap(MaxMindUpdater::zipInputStreamToMulti)
				.flatMap(entry ->
						processFile(entry.getItem1().getName(), entry.getItem2(), updater)
								.onItemOrFailure().invoke(() ->
									log.infof("Finished processing: %s", entry.getItem1().getName())
								)
								.toMulti()
				)
				.collectItems().asList()
				.<Void>map(entry -> null)
				.onItemOrFailure().invoke(() -> closeAndDeleteArchive(zipStream, filePath));
	}
	
	@SneakyThrows(IOException.class)
	private Cancellable doLaunchUpdate() {
		updateExecutor.set(
				ManagedExecutor.builder()
						.maxAsync(1)
						.maxQueued(1)
						.build()
		);
		return Uni.createFrom().item(Files.createTempFile("maxmind", ".zip"))
				.emitOn(updateExecutor.get())
				.map(this::downloadArchive)
				.flatMap(filePath -> storage.update(updater -> performUpdate(filePath, updater)))
				.onTermination().invoke(() -> {
					((ExecutorService) updateExecutor.getAndSet(null)).shutdownNow();
					updateCancellable.set(null);
					log.info("Update executor service terminated");
				})
				.subscribe()
				.with(
						result -> log.info("Update completed"),
						error -> log.error("Update failed", error)
				);
	}
}

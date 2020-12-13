package com.eternal_search.geoip.maxmind;

import com.eternal_search.geoip.maxmind.model.MaxMindBlock;
import com.eternal_search.geoip.model.GeoIPBlock;
import lombok.SneakyThrows;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class MaxMindBlockParser {
	private static final String DIGITS = "0123456789abcdef";
	
	private MaxMindBlockParser() {
		throw new UnsupportedOperationException();
	}
	
	private static String addressBytesToString(byte[] bytes) {
		char[] chars = new char[bytes.length * 2];
		int j = 0;
		for (byte b : bytes) {
			chars[j++] = DIGITS.charAt((b >> 4) & 15);
			chars[j++] = DIGITS.charAt(b & 15);
		}
		return String.valueOf(chars);
	}
	
	private static byte[] addressToBytes(InetAddress address) {
		byte[] addressBytes = address.getAddress();
		if (addressBytes.length < 16) {
			byte[] newBytes = new byte[16];
			System.arraycopy(
					addressBytes, 0,
					newBytes, newBytes.length - addressBytes.length,
					addressBytes.length
			);
			for (
					int i = newBytes.length - addressBytes.length - 1;
					i > Math.max(newBytes.length - addressBytes.length - 2 - 1, 0);
					i--
			) {
				newBytes[i] = (byte) 255;
			}
			addressBytes = newBytes;
		}
		return addressBytes;
	}
	
	private static String addressToString(InetAddress address) {
		return addressBytesToString(addressToBytes(address));
	}
	
	@SneakyThrows(UnknownHostException.class)
	public static String addressToString(String address) {
		return addressToString(InetAddress.getByName(address));
	}
	
	@SneakyThrows(UnknownHostException.class)
	public static GeoIPBlock parse(MaxMindBlock block) {
		String[] parts = block.getNetwork().split("/", 2);
		InetAddress netAddress = InetAddress.getByName(parts[0]);
		int netSize = Integer.parseInt(parts[1]);
		if (netAddress instanceof Inet4Address) {
			netSize += 96;
		}
		byte[] netAddressBytes = addressToBytes(netAddress);
		byte[] start = new byte[netAddressBytes.length];
		byte[] stop = new byte[netAddressBytes.length];
		for (int i = 0; i < netAddressBytes.length; i++) {
			if ((i + 1) * 8 <= netSize) {
				start[i] = netAddressBytes[i];
				stop[i] = netAddressBytes[i];
			} else if (i * 8 > netSize || netSize % 8 == 0) {
				start[i] = 0;
				stop[i] = (byte) 255;
			} else {
				byte mask = (byte) ((1 << (8 - netSize % 8)) - 1);
				start[i] = (byte) (netAddressBytes[i] & ~mask);
				stop[i] = (byte) (netAddressBytes[i] | mask);
			}
		}
		
		return GeoIPBlock.builder()
				.start(addressBytesToString(start))
				.stop(addressBytesToString(stop))
				.locationId(block.getGeonameId())
				.postalCode(block.getPostalCode())
				.latitude(block.getLatitude())
				.longitude(block.getLongitude())
				.accuracyRadius(block.getAccuracyRadius())
				.isAnonymousProxy(block.getIsAnonymousProxy())
				.isSatelliteProvider(block.getIsSatelliteProvider())
				.build();
	}
}

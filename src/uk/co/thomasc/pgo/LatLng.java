package uk.co.thomasc.pgo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;
import com.google.protobuf.ByteString;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class LatLng {
	
	@Getter private double lat;
	@Getter private double lng;

	public S2CellId getParent() {
		return S2CellId.fromLatLng(S2LatLng.fromDegrees(lat, lng)).parent(15);
	}

	public static LatLng fromS2(S2LatLng latLng) {
		return new LatLng(latLng.latDegrees(), latLng.lngDegrees());
	}

	public long getLatL() {
		return Double.doubleToLongBits(this.lat);
	}
	
	public long getLngL() {
		return Double.doubleToLongBits(this.lng);
	}

	public ByteString getWalk() throws IOException {
		S2CellId origin = this.getParent();
		
		List<Long> walk = new ArrayList<Long>();
		walk.add(origin.id());
		
		S2CellId next = origin.next();
		S2CellId prev = origin.prev();
		for (int i = 0; i < 10; i++) {
			walk.add(prev.id());
			walk.add(next.id());
			next = next.next();
			prev = prev.prev();
		}
		
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		Collections.sort(walk);
		for (Long l : walk) {
			encode(l, stream);
		}
		
		return ByteString.copyFrom(stream.toByteArray());
	}
	
	private static void encode(long input, ByteArrayOutputStream stream) {
		long bits = input & 0x7f;
		input >>= 7;
		
		while (input != 0) {
			stream.write((byte) (0x80 | bits));
			bits = input & 0x7f;
			input >>= 7;
		}
		
		stream.write((byte) bits);
	}
}
package uk.co.thomasc.pgo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import uk.co.thomasc.pgo.proto.PTCConnection;
import uk.co.thomasc.pgo.proto.PokemonOuterClass.ResponseEnvelop.ClientMapCell;
import uk.co.thomasc.pgo.proto.PokemonOuterClass.ResponseEnvelop.HeartbeatPayload;
import uk.co.thomasc.pgo.proto.PokemonOuterClass.ResponseEnvelop.PokemonFortProto;
import uk.co.thomasc.pgo.proto.PokemonOuterClass.ResponseEnvelop.WildPokemonProto;
import uk.co.thomasc.pgo.util.LatLng;
import uk.co.thomasc.pgo.util.Mongo;

import com.google.common.geometry.S2CellId;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class WorkerThread implements Callable<Integer> {

	private LatLng pos;
	private PTCConnection conn;
	private Mongo mongo;
	private Main main;
	
	public WorkerThread(PTCConnection conn, Mongo mongo, LatLng pos, Main main) {
		this.pos = pos;
		this.conn = conn;
		this.mongo = mongo;
		this.main = main;
	}

	@Override
	public Integer call() throws Exception {
		List<HeartbeatPayload> results = new ArrayList<HeartbeatPayload>();
		int wildC = 0;
		
		try {
			results.add(conn.findPokemon(pos));
		
			S2CellId id = pos.getParent();
			for(S2CellId c = id.childBegin(); !c.equals(id.childEnd()); c = c.next()) {
				LatLng point = LatLng.fromS2(c.toLatLng());
				results.add(conn.findPokemon(point));
			}
			
			for (HeartbeatPayload heartbeat : results) {
				if (heartbeat == null) continue;
				
				for (ClientMapCell cell : heartbeat.getCellsList()) {
					for (WildPokemonProto wild : cell.getWildPokemonList()) {
						wildC++;
						savePokemon(wild);
					}
					for (PokemonFortProto fort : cell.getFortList()) {
						if (fort.getEnabled()) saveFort(fort);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		main.count();
		return wildC;
	}
	
	private void savePokemon(WildPokemonProto wild) {
		String hash = wild.getSpawnPointId() + ":" + wild.getPokemon().getPokemonId();
		
		BasicDBList coords = new BasicDBList();
		coords.add(wild.getLongitude());
		coords.add(wild.getLatitude());
		DBObject point = new BasicDBObject("type", "Point").append("coordinates", coords);
		
		mongo.update(
			"wild",
			new BasicDBObject("_id", hash),
			new BasicDBObject("$setOnInsert",
				new BasicDBObject("found", new Date())
			).append("$set",
				new BasicDBObject("dex", wild.getPokemon().getPokemonId())
				.append("gone", new Date((new Date()).getTime() + wild.getTimeTillHiddenMs()))
				.append("loc", point)
				.append("lat", wild.getLatitude())
				.append("lon", wild.getLongitude())
			),
			true
		);
	}
	
	private void saveFort(PokemonFortProto fort) {
		BasicDBList coords = new BasicDBList();
		coords.add(fort.getLongitude());
		coords.add(fort.getLatitude());
		DBObject point = new BasicDBObject("type", "Point").append("coordinates", coords);
		
		BasicDBObject set = new BasicDBObject("updated", new Date(fort.getLastModifiedMs()))
			.append("loc", point)
			.append("lat", fort.getLatitude())
			.append("lon", fort.getLongitude());
		
		if (fort.getGymPoints() > 0) {
			set.append("team", fort.getTeam());
			set.append("guard", fort.getGuardPokemonId());
			set.append("guardcp", fort.getGuardPokemonLevel());
			set.append("points", fort.getGymPoints());
		}
		
		if (fort.getLureInfo().getLureExpiresTimestampMs() > 0) {
			set.append("lure", new Date(fort.getLureInfo().getLureExpiresTimestampMs()));
		}
		
		mongo.update(
			"waypoints",
			new BasicDBObject("_id", fort.getFortId()),
			new BasicDBObject("$set", set),
			true
		);
	}

}

package uk.co.thomasc.pgo;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;

public class Mongo {
	
	private DB pokemonDB;

	public Mongo(JSONObject json) {
		System.out.println("Connecting!");
		try {
			List<ServerAddress> seeds = new ArrayList<ServerAddress>();
			seeds.add(new ServerAddress("dibble"));
			seeds.add(new ServerAddress("choo-choo"));
			
			MongoClient mongoClient = new MongoClient(seeds);
			mongoClient.setReadPreference(ReadPreference.nearest());
			
			pokemonDB = mongoClient.getDB("pokemon");
			pokemonDB.authenticate(json.getString("user"), json.getString("pass").toCharArray());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		System.out.println("Connected");
	}
	
	public long count(String collection) {
		return pokemonDB.getCollection(collection).count();
	}

	public long count(String collection, DBObject query) {
		return pokemonDB.getCollection(collection).count(query);
	}
	
	public boolean exists(String collection, DBObject query) {
		return count(collection, query) > 0;
	}
	
	public DBObject updateAndReturn(String collection, DBObject query, DBObject update) {
		return pokemonDB.getCollection(collection).findAndModify(query, update);
	}

	public WriteResult update(String collection, DBObject query, DBObject update) {
		return update(collection, query, update, false);
	}
	
	public WriteResult update(String collection, DBObject query, DBObject update, boolean upsert) {
		return update(collection, query, update, upsert, false);
	}
	
	public WriteResult update(String collection, DBObject query, DBObject update, boolean upsert, boolean multi) {
		return update(collection, query, update, upsert, multi, WriteConcern.ACKNOWLEDGED);
	}
	
	public WriteResult update(String collection, DBObject query, DBObject update, boolean upsert, boolean multi, WriteConcern concern) {
		return pokemonDB.getCollection(collection).update(query, update, upsert, multi, concern);
	}
	
	public WriteResult delete(String collection, DBObject query) {
		return delete(collection, query, WriteConcern.ACKNOWLEDGED);
	}
	
	public WriteResult delete(String collection, DBObject query, WriteConcern concern) {
		return pokemonDB.getCollection(collection).remove(query, concern);
	}

	public WriteResult insert(String collection, DBObject row) {
		return insert(collection, row, WriteConcern.ACKNOWLEDGED);
	}
	
	public WriteResult insert(String collection, DBObject row, WriteConcern concern) {
		return pokemonDB.getCollection(collection).insert(row, concern);
	}
	
	public DBCursor find(String collection) {
		return pokemonDB.getCollection(collection).find();
	}
	
	public DBCursor find(String collection, DBObject query) {
		return find(collection, query, null);
	}

	public DBCursor find(String collection, DBObject query, DBObject projection) {
		return pokemonDB.getCollection(collection).find(query, projection);
	}

	public DBObject findOne(String collection, DBObject query) {
		return findOne(collection, query, null);
	}
	
	public DBObject findOne(String collection, DBObject query, DBObject projection) {
		return pokemonDB.getCollection(collection).findOne(query, projection);
	}

	public DBObject findRandom(String collection) {
		long count = count(collection);
		int id = (int) (Math.random() * count);
		
		return pokemonDB.getCollection(collection).find().skip(id).next();
	}

	public int incCounter(String counter) {
		return incCounter(counter, 1);
	}
	
	public int incCounter(String counter, int ammount) {
		DBObject obj = pokemonDB.getCollection("counters").findAndModify(new BasicDBObject("_id", counter), new BasicDBObject("_id", 0), null, false, new BasicDBObject("$inc", new BasicDBObject("seq", ammount)), true, false);
		return ((Number) obj.get("seq")).intValue();
	}

	public DBObject findAndModify(String collection, DBObject query, DBObject update) {
		return pokemonDB.getCollection(collection).findAndModify(query, update);
	}
	
	public DBObject findAndModify(String collection, DBObject query, DBObject fields, DBObject update, boolean returnNew, boolean upsert) {
		return pokemonDB.getCollection(collection).findAndModify(query, fields, null, false, update, returnNew, upsert);
	}

}

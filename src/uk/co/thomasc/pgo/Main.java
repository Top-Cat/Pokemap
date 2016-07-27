package uk.co.thomasc.pgo;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import uk.co.thomasc.pgo.proto.PTCConnection;
import uk.co.thomasc.pgo.util.LatLng;
import uk.co.thomasc.pgo.util.Mongo;
import uk.co.thomasc.pgo.util.Util;

public class Main {
	
	private ExecutorService executor;
	
	public static void main(String args[]) throws Exception {
		JSONObject obj = Util.JSONfromStream(new FileInputStream(args[0]));
		new Main(obj);
	}
	
	public Main(JSONObject obj) {
		executor = Executors.newFixedThreadPool(obj.getInt("threads"));
		searchPokemon(obj);
	}
	
	public int c = 0, t = 0;
	public void count() {
		if (++c % 50 == 0) System.out.println(c + "/" + t);
	}
	
	public void searchPokemon(JSONObject obj) {
		Set<LatLng> searchSpace = new HashSet<LatLng>();
		for (Object loc : obj.getJSONArray("locations")) {
			JSONObject _loc = (JSONObject) loc;
			searchSpace.addAll(generateSearchGrid(new LatLng(_loc.getDouble("lat"), _loc.getDouble("lng")), _loc.getInt("size")));
		}
		//searchSpace.addAll(generateSearchGrid(new LatLng(53.8017278,-1.5619364), size)); // Leeds
		//searchSpace.addAll(generateSearchGrid(new LatLng(53.3777196,-1.4635990), size)); // Sheffield - 53.4095535,-1.3798237

		Mongo mongo = new Mongo(obj.getJSONObject("mongo"));
		PTCConnection conn = new PTCConnection(obj.getJSONObject("pgo"));
		
		while (true) {
			try {
				if (!conn.connect()) {
					System.out.println("Error connecting to pokemon go api");
					Thread.sleep(5000);
					continue;
				}
				
				t = c = 0;
				List<Callable<Integer>> tasks = new ArrayList<Callable<Integer>>();
				for (LatLng pos : searchSpace) {
					t++;
					tasks.add(new WorkerThread(conn, mongo, pos, this));
				}
				List<Future<Integer>> futures = executor.invokeAll(tasks, 5L, TimeUnit.MINUTES);
				
				int totalPokes = 0;
				for (Future<Integer> f : futures) {
					try {
						totalPokes += f.get();
					} catch (ExecutionException e) {
						// Will have already complained
					}
				}
				
				System.out.println("Done, found " + totalPokes + " pokemon");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private Set<LatLng> generateSearchGrid(LatLng center, int size) {
		return generateSearchGrid(center, size, 2);
	}
	
	private Set<LatLng> generateSearchGrid(LatLng center, int size, int stretch) {
		Set<LatLng> searchGrid = new HashSet<LatLng>();
		
		int steplimit = (int) (Math.pow(size, 2) * stretch);
		for (int steps = 0; steps < steplimit; steps++) {
			int x = (steps / (size * stretch)) - (size / stretch);
			int y = (steps % (size * stretch)) - size;
			
			searchGrid.add(
				new LatLng(
					center.getLat() + (x * 0.0025),
					center.getLng() + (y * 0.0025)
				)
			);
		}
		
		return searchGrid;
	}
}
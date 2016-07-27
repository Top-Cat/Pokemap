package uk.co.thomasc.pgo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import uk.co.thomasc.pgo.PokemonOuterClass.RequestEnvelop;
import uk.co.thomasc.pgo.PokemonOuterClass.ResponseEnvelop;
import uk.co.thomasc.pgo.PokemonOuterClass.UnknownAuth;
import uk.co.thomasc.pgo.PokemonOuterClass.RequestEnvelop.AuthInfo;
import uk.co.thomasc.pgo.PokemonOuterClass.RequestEnvelop.MessageQuad;
import uk.co.thomasc.pgo.PokemonOuterClass.RequestEnvelop.MessageSingleInt;
import uk.co.thomasc.pgo.PokemonOuterClass.RequestEnvelop.MessageSingleString;
import uk.co.thomasc.pgo.PokemonOuterClass.RequestEnvelop.Requests;
import uk.co.thomasc.pgo.PokemonOuterClass.RequestEnvelop.AuthInfo.JWT;
import uk.co.thomasc.pgo.PokemonOuterClass.ResponseEnvelop.HeartbeatPayload;
import uk.co.thomasc.pgo.PokemonOuterClass.ResponseEnvelop.ProfilePayload;
import uk.co.thomasc.pgo.PokemonOuterClass.ResponseEnvelop.Profile.Currency;

public class PTCConnection {

	private static String API_URL = "https://pgorelease.nianticlabs.com/plfe/rpc";
	private static String LOGIN_URL = "https://sso.pokemon.com/sso/login?service=https%3A%2F%2Fsso.pokemon.com%2Fsso%2Foauth2.0%2FcallbackAuthorize";
	private static String LOGIN_OAUTH = "https://sso.pokemon.com/sso/oauth2.0/accessToken";
	private static String PTC_CLIENT_SECRET = "w8ScCUXJQc6kXKw8FiOhd8Fixzht18Dq3PEVkUCP5ZPxtgyWsbTvWHFLm2wNY0JR";
	
	PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
	CloseableHttpClient httpClient;
	
	private String user;
	private String pass;
	
	private String login_token;
	private String endpoint;
	private UnknownAuth auth;
	
	public PTCConnection(String user, String pass) {
		this.user = user;
		this.pass = pass;
		
		cm.setMaxTotal(2000);
		cm.setDefaultMaxPerRoute(2000);
		this.httpClient = HttpClientBuilder.create().setConnectionManager(cm).build();
		this.connect();
	}
	
	public PTCConnection(JSONObject jsonObject) {
		this(jsonObject.getString("user"), jsonObject.getString("pass"));
	}

	public void disconnect() {
		try {
			this.httpClient.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void connect() {
		String token = getLoginToken();
		
		if (token != null)  {
			System.out.println("Got login token: " + token);
			this.login_token = token;
		}
		
		if (this.login_token == null) return;
		
		String endpoint = getApiEndpoint();
		if (endpoint != null) {
			this.endpoint = endpoint;
		}
		
		if (this.endpoint == null) return;
		
		ResponseEnvelop response = getProfile(this.endpoint);
		
		if (response == null || response.getPayloadCount() == 0) return;
		
		this.auth = response.getUnknown7();
		
		try {
			ProfilePayload profile = ProfilePayload.parseFrom(response.getPayload(0));
			
			System.out.println(String.format("Username: %s", profile.getProfile().getUsername()));
			
			for (Currency curr : profile.getProfile().getCurrencyList()) {
				System.out.println(String.format("%s: %s", curr.getType(), curr.getAmount()));
			}
		} catch (InvalidProtocolBufferException e) {
			e.printStackTrace();
		}
	}
	
	private String getApiEndpoint() {
		String endpoint = getProfile(API_URL).getApiUrl();
		if (endpoint != null) {
			return String.format("https://%s/rpc", endpoint);
		}
		
		return null;
	}
	
	private ResponseEnvelop getProfile(String url) {
		return getProfile(url, 0, 0, null);
	}
	
	private ResponseEnvelop getProfile(String url, long lat, long lng, List<Requests> req) {
		RequestEnvelop.Builder msg = RequestEnvelop.newBuilder()
			.setLatitude(lat)
			.setLongitude(lng);
		
		int[] types = {2, 126, 4, 129, 5};
		for (int i = 0; i < types.length; i++) {
			Requests.Builder builder = Requests.newBuilder().setType(types[i]);
			if (req != null && req.size() > i) {
				builder.mergeFrom(req.get(i));
			}
			msg.addRequests(builder);
		}
		
		return apiReq(url, msg);
	}
	
	private ResponseEnvelop apiReq(String api_endpoint, RequestEnvelop.Builder msg) {
		HttpPost post = new HttpPost(api_endpoint);
		
		if (this.auth == null) {
			msg.setAuth(AuthInfo.newBuilder()
				.setProvider("ptc")
				.setToken(JWT.newBuilder()
					.setContents(login_token)
					.setUnknown13(14)
				)
			);
		} else {
			msg.setUnknown11(this.auth);
		}
		
		RequestEnvelop newMsg = msg
			.setRpcId(1469378659230941192L)
			.setUnknown1(2)
			.setAltitude(0)
			.setUnknown12(989)
			.build();
		post.setEntity(new ByteArrayEntity(newMsg.toByteArray()));
		
		try {
			HttpResponse future = httpClient.execute(post);
			
			byte[] response = EntityUtils.toByteArray(future.getEntity());
			return ResponseEnvelop.parseFrom(response);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private String getLoginToken() {
		try {
			HttpGet conn = new HttpGet(LOGIN_URL);
			conn.setHeader("User-Agent", "Niantic App");
			HttpResponse future = httpClient.execute(conn);
			HttpPost post = new HttpPost(LOGIN_URL);
			post.setHeader("User-Agent", "Niantic App");
			
			JSONObject obj = Util.JSONfromStream(future.getEntity().getContent());
			
			List<NameValuePair> nvps = new ArrayList <NameValuePair>();
			nvps.add(new BasicNameValuePair("lt", (String) obj.get("lt")));
			nvps.add(new BasicNameValuePair("execution", (String) obj.get("execution")));
			nvps.add(new BasicNameValuePair("_eventId", "submit"));
			nvps.add(new BasicNameValuePair("username", user));
			nvps.add(new BasicNameValuePair("password", pass));
			post.setEntity(new UrlEncodedFormEntity(nvps));
			
			future = httpClient.execute(post);
			String loc = future.getFirstHeader("Location").getValue();
			int index = loc.indexOf("ticket=");
			if (index >= 0) {
				String ticket = loc.substring(index + 7);
				
				post = new HttpPost(LOGIN_OAUTH);
				
				nvps = new ArrayList <NameValuePair>();
				nvps.add(new BasicNameValuePair("client_id", "mobile-app_pokemon-go"));
				nvps.add(new BasicNameValuePair("redirect_uri", "https://www.nianticlabs.com/pokemongo/error"));
				nvps.add(new BasicNameValuePair("client_secret", PTC_CLIENT_SECRET));
				nvps.add(new BasicNameValuePair("grant_type", "refresh_token"));
				nvps.add(new BasicNameValuePair("code", ticket));
				post.setEntity(new UrlEncodedFormEntity(nvps));
				
				future = httpClient.execute(post);
				String token = EntityUtils.toString(future.getEntity()).split("&expires")[0];
				return token.substring(token.indexOf("access_token=") + 13);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return "";
	}

	public HeartbeatPayload findPokemon(LatLng pos) throws IOException {
		List<Requests> req = new ArrayList<Requests>();
		
		req.add(Requests.newBuilder()
			.setType(106)
			.setMessage(
				MessageQuad.newBuilder()
					.setF1(pos.getWalk())
					.setF2(ByteString.copyFrom(new byte[21]))
					.setLat(pos.getLatL())
					.setLong(pos.getLngL())
					.build().toByteString()
			).buildPartial()
		);
		
		req.add(Requests.newBuilder().buildPartial());
		
		req.add(Requests.newBuilder()
			.setMessage(
				MessageSingleInt.newBuilder()
					.setF1(System.currentTimeMillis())
					.build().toByteString()
			).buildPartial()
		);
		
		req.add(Requests.newBuilder().buildPartial());
		
		req.add(Requests.newBuilder()
			.setMessage(
				MessageSingleString.newBuilder()
					.setBytes(ByteString.copyFrom("05daf51635c82611d1aac95c0b051d3ec088a930".getBytes()))
					.build().toByteString()
			).buildPartial()
		);
		
		ResponseEnvelop response = getProfile(this.endpoint, pos.getLatL(), pos.getLngL(), req);
		if (response == null) return null;
		return HeartbeatPayload.parseFrom(response.getPayload(0).toByteArray());
	}

}

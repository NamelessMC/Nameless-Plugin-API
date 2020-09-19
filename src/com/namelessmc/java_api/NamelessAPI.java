package com.namelessmc.java_api;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.namelessmc.java_api.RequestHandler.Action;
import com.namelessmc.java_api.Website.Update;

public final class NamelessAPI {

	private static final String DEFAULT_USER_AGENT = "Nameless-Java-API/beta"; // TODO include mavenversion string
	
	private final RequestHandler requests;

	public NamelessAPI(final URL apiUrl) {
		this(apiUrl, DEFAULT_USER_AGENT);
	}
	
	/**
	 * @param apiUrl URL of API to connect to, in the format http(s)://yoursite.com/index.php?route=/api/v2/API_KEY
	 * @param debug If debug is set to true, debug messages are enabled for <i>every</i> NamelessAPI instance.
	 */
	public NamelessAPI(final URL apiUrl, final boolean debug) {
		this(apiUrl, DEFAULT_USER_AGENT, debug);
	}
	
	public NamelessAPI(final URL apiUrl, final String userAgent) {
		this(apiUrl, userAgent, false);
	}

	/**
	 * @param host URL of your website, in the format http(s)://yoursite.com
	 * @param apiKey API key
	 * @param debug If debug is set to true, debug messages are enabled for <i>every</i> NamelessAPI instance.
	 * @throws MalformedURLException
	 */
	public NamelessAPI(final String host, final String apiKey, final String userAgent, final boolean debug) throws MalformedURLException {
		this(new URL(host + "/index.php?route=/api/v2/" + apiKey), userAgent, debug);
	}
	
	public NamelessAPI(final String host, final String apiKey, final boolean debug) throws MalformedURLException {
		this(host, apiKey, DEFAULT_USER_AGENT, debug);
	}
	
	public NamelessAPI(final String host, final String apiKey, final String userAgent) throws MalformedURLException {
		this(host, apiKey, userAgent, false);
	}
	
	public NamelessAPI(final String host, final String apiKey) throws MalformedURLException {
		this(host, apiKey, DEFAULT_USER_AGENT);
	}
	
	public NamelessAPI(final URL apiUrl, final String userAgent, final boolean debug) {
		this.requests = new RequestHandler(apiUrl, userAgent, debug);
	}
	
	RequestHandler getRequestHandler() {
		return this.requests;
	}

	/**
	 * Checks if a web API connection can be established
	 * throws {@link NamelessException} if the connection was unsuccessful
	 */
	public void checkWebAPIConnection() throws NamelessException {
		final JsonObject response = this.requests.get(Action.INFO);
		if (!response.has("nameless_version")) {
			throw new NamelessException("Invalid respose: " + response.getAsString());
		}
	}

	/**
	 * Get all announcements
	 * @return list of current announcements
	 * @throws NamelessException if there is an error in the request
	 */
	public List<Announcement> getAnnouncements() throws NamelessException {
		final JsonObject response = this.requests.get(Action.GET_ANNOUNCEMENTS);

		final List<Announcement> announcements = new ArrayList<>();

		response.getAsJsonArray().forEach((element) -> {
			final JsonObject announcementJson = element.getAsJsonObject();
			final String content = announcementJson.get("content").getAsString();
			final String[] display = jsonToArray(announcementJson.get("display").getAsJsonArray());
			final String[] permissions = jsonToArray(announcementJson.get("permissions").getAsJsonArray());
			announcements.add(new Announcement(content, display, permissions));
		});

		return announcements;
	}

	/**
	 * Get all announcements visible for the player with the specified uuid
	 * @param uuid UUID of player to get visibile announcements for
	 * @return list of current announcements visible to the player
	 * @throws NamelessException if there is an error in the request
	 */
	public List<Announcement> getAnnouncements(final NamelessUser user) throws NamelessException {
		final JsonObject response = this.requests.get(Action.GET_ANNOUNCEMENTS, "id", user.getId());

		final List<Announcement> announcements = new ArrayList<>();

		response.get("announcements").getAsJsonArray().forEach((element) -> {
			final JsonObject announcementJson = element.getAsJsonObject();
			final String content = announcementJson.get("content").getAsString();
			final String[] display = jsonToArray(announcementJson.get("display").getAsJsonArray());
			final String[] permissions = jsonToArray(announcementJson.get("permissions").getAsJsonArray());
			announcements.add(new Announcement(content, display, permissions));
		});

		return announcements;
	}

	public void submitServerInfo(final String jsonData) throws NamelessException {
		this.requests.post(Action.SERVER_INFO, jsonData);
	}

	public Website getWebsite() throws NamelessException {
		final JsonObject json = this.requests.get(Action.INFO);

		final String version = json.get("nameless_version").getAsString();

		final String[] modules = jsonToArray(json.get("modules").getAsJsonArray());

		final JsonObject updateJson = json.get("version_update").getAsJsonObject();
		final boolean updateAvailable = updateJson.get("update").getAsBoolean();
		Update update;
		if (updateAvailable) {
			final String updateVersion = updateJson.get("version").getAsString();
			final boolean isUrgent = updateJson.get("urgent").getAsBoolean();
			update = new Update(isUrgent, updateVersion);
		} else {
			update = null;
		}

		return new Website(version, update, modules);

	}
	
	public List<NamelessUser> getRegisteredUsers() throws NamelessException {
		final JsonObject response = this.requests.get(Action.LIST_USERS);
		final JsonArray array = response.getAsJsonArray("users");
		final List<NamelessUser> users = new ArrayList<>(array.size());
		for (final JsonElement e : array) {
			final JsonObject o = e.getAsJsonObject();
			final int id = o.get("id").getAsInt();
			final String username = o.get("username").getAsString();
			Optional<UUID> uuid;
			if (o.has("uuid")) {
				uuid = Optional.of(NamelessAPI.websiteUuidToJavaUuid(o.get("uuid").getAsString()));
			} else {
				uuid = Optional.empty();
			}
			users.add(new NamelessUser(this, id, username, uuid));
		};
		return Collections.unmodifiableList(users);
	}
	
	public NamelessUser getUser(final int id) throws NamelessException {
		return new NamelessUser(this, id, null, null);
	}
	
	public NamelessUser getUser(final String username) throws NamelessException {
		return new NamelessUser(this, null, username, null);
	}
	
	public NamelessUser getUser(final UUID uuid) throws NamelessException {
		return new NamelessUser(this, null, null, Optional.of(uuid));
	}
	
	public NamelessUser getUser(final UUID uuid, final String username) throws NamelessException {
		return new NamelessUser(this, null, username, Optional.of(uuid));
	}
	
	public void submitRankList(final List<String> rankNames) {
		
	}
	
	/**
	 * Registers a new account. The user will be sent an email to set a password.
	 * @param username Username
	 * @param email Email address
	 * @return Email verification disabled: A link which the user needs to click to complete registration
	 * <br>Email verification enabled: An empty string (the user needs to check their email to complete registration)
	 * @throws NamelessException
	 */
	public Optional<String> registerUser(final String username, final String email, final Optional<UUID> uuid) throws NamelessException {
		final JsonObject post = new JsonObject();
		post.addProperty("username", username);
		post.addProperty("email", email);
		if (uuid.isPresent()) {
			post.addProperty("uuid", uuid.get().toString());
		}
		
		final JsonObject response = this.requests.post(Action.REGISTER, post.toString());
		
		if (response.has("link")) {
			return Optional.of(response.get("link").getAsString());
		} else {
			return Optional.empty();
		}
	}

	static String encode(final Object object) {
		try {
			return URLEncoder.encode(object.toString(), "UTF-8");
		} catch (final UnsupportedEncodingException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	@Deprecated
	static String[] jsonToArray(final JsonArray jsonArray) {
		final List<String> list = new ArrayList<>();
		jsonArray.iterator().forEachRemaining((element) -> list.add(element.getAsString()));
		return list.toArray(new String[] {});
	}

	static UUID websiteUuidToJavaUuid(final String uuid) {
		// Add dashes to uuid
		// https://bukkit.org/threads/java-adding-dashes-back-to-minecrafts-uuids.272746/
		StringBuffer sb = new StringBuffer(uuid);
		sb.insert(8, "-");

		sb = new StringBuffer(sb.toString());
		sb.insert(13, "-");

		sb = new StringBuffer(sb.toString());
		sb.insert(18, "-");

		sb = new StringBuffer(sb.toString());
		sb.insert(23, "-");

		return UUID.fromString(sb.toString());
	}


}
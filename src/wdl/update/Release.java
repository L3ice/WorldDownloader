package wdl.update;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * An individual GitHub release.
 * <br/>
 * Does not contain all of the info, but the used info is included.
 * 
 * @see https://developer.github.com/v3/repos/releases/#get-a-release-by-tag-name
 */
public class Release {
	/**
	 * Regular expression used to match the hidden metadata text.
	 * 
	 * Basically, some hidden JSON is put in the body markdown, in the form of
	 * <pre>[](# '{"someKey":"someValue"}')MainBodyText</pre>, which visually
	 * only appears as "MainBodyText" since the link is 0 characters long.
	 * (The quotes are normally used to make tooltips.)
	 */
	private static final Pattern HIDDEN_JSON_REGEX = Pattern.compile(
			"^\\[\\]\\(# '(.+?)'\\)");
	private static final JsonParser PARSER = new JsonParser();
	
	/**
	 * Further info hidden inside of the body.
	 */
	public class HiddenInfo {
		/**
		 * Main version of Minecraft supported by this version.
		 */
		public final String mainMinecraftVersion;
		/**
		 * Additional versions that can be connected to, but aren't
		 * used by default.
		 */
		public final String[] supportedMinecraftVersions;
		/**
		 * Loader used for this mod (Coremod, liteloader, ect)
		 */
		public final String loader;
		/**
		 * Post announcing this new version (EG on the minecraftforums).
		 * May be null.
		 */
		public final String post;
		/**
		 * Hashes for each of the classes in this release.
		 */
		public final HashData[] hashes;
		
		private HiddenInfo(JsonObject object) {
			this.mainMinecraftVersion = object.get("Minecraft").getAsString();
			JsonArray compatibleVersions = object.get("MinecraftCompatible")
					.getAsJsonArray();
			this.supportedMinecraftVersions = new String[compatibleVersions
					.size()];
			for (int i = 0; i < compatibleVersions.size(); i++) {
				this.supportedMinecraftVersions[i] = compatibleVersions.get(i)
						.getAsString();
			}
			
			this.loader = object.get("Loader").getAsString();
			JsonElement post = object.get("Post");
			if (post.isJsonNull()) {
				this.post = null;
			} else {
				this.post = post.getAsString();
			}
			
			JsonArray hashes = object.get("Hashes").getAsJsonArray();
			this.hashes = new HashData[hashes.size()];
			for (int i = 0; i < hashes.size(); i++) {
				this.hashes[i] = new HashData(hashes.get(i).getAsJsonObject());
			}
		}

		@Override
		public String toString() {
			return "HiddenInfo [mainMinecraftVersion=" + mainMinecraftVersion
					+ ", supportedMinecraftVersions="
					+ Arrays.toString(supportedMinecraftVersions) + ", loader="
					+ loader + ", post=" + post + ", hashes="
					+ Arrays.toString(hashes) + "]";
		}
	}
	
	public class HashData {
		/**
		 * Class name to check relative to. Used for
		 * {@link Class#getResourceAsStream(String)}.
		 */
		public final String relativeTo;
		/**
		 * File to hash.
		 */
		public final String file;
		/**
		 * Valid values for the hash to be.  Should be in all caps hexadecimal.
		 * This is an array because there is sometimes a case where there are
		 * two legal values &ndash; for instance, 1.8 had two editions, one
		 * with debug enabled and one with debug disabled (now replaced with
		 * the Messages gui).
		 */
		public final String[] validHashes;
		
		public HashData(JsonObject object) {
			this.relativeTo = object.get("RelativeTo").getAsString();
			this.file = object.get("File").getAsString();
			JsonArray hashes = object.get("Hash").getAsJsonArray();
			this.validHashes = new String[hashes.size()];
			for (int i = 0; i < validHashes.length; i++) {
				this.validHashes[i] = hashes.get(i).getAsString();
			}
		}

		@Override
		public String toString() {
			return "HashData [relativeTo=" + relativeTo + ", file=" + file
					+ ", validHashes=" + Arrays.toString(validHashes) + "]";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((file == null) ? 0 : file.hashCode());
			result = prime * result
					+ ((relativeTo == null) ? 0 : relativeTo.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof HashData)) {
				return false;
			}
			HashData other = (HashData) obj;
			if (!getOuterType().equals(other.getOuterType())) {
				return false;
			}
			if (file == null) {
				if (other.file != null) {
					return false;
				}
			} else if (!file.equals(other.file)) {
				return false;
			}
			if (relativeTo == null) {
				if (other.relativeTo != null) {
					return false;
				}
			} else if (!relativeTo.equals(other.relativeTo)) {
				return false;
			}
			return true;
		}

		private Release getOuterType() {
			return Release.this;
		}
	}
	
	public Release(JsonObject object) {
		this.object = object;
		
		String body = object.get("body").getAsString();
		Matcher hiddenJSONMatcher = HIDDEN_JSON_REGEX.matcher(body);
		if (hiddenJSONMatcher.find()) {
			// Before the hidden JSON
			// (will be empty on most of the normal WDL releases)
			String before = body.substring(0, hiddenJSONMatcher.start());
			// After the hidden JSON
			String after = body.substring(hiddenJSONMatcher.end());
			
			this.body = before + after;
			
			// Grab capture group #1 (inside the single quotes)
			String hiddenJSONStr = body.substring(hiddenJSONMatcher.start(1),
					hiddenJSONMatcher.end(1));
			JsonObject hiddenJSON = PARSER.parse(hiddenJSONStr)
					.getAsJsonObject();
			this.hiddenInfo = new HiddenInfo(hiddenJSON);
		} else {
			// No hidden information.
			this.body = body;
			this.hiddenInfo = null;
		}
		
		this.URL = object.get("html_url").getAsString();
		this.tag = object.get("tag_name").getAsString();
		this.title = object.get("name").getAsString();
		this.date = object.get("published_at").getAsString();
		this.prerelease = object.get("prerelease").getAsBoolean();
	}
	
	/**
	 * {@link JsonObject} used to create this.
	 */
	public final JsonObject object;
	/**
	 * URL to the release page.
	 */
	public final String URL;
	/**
	 * Tag name.
	 */
	public final String tag;
	/**
	 * Title for this release.
	 */
	public final String title;
	/**
	 * Date that the release was published on.
	 */
	public final String date;
	/**
	 * Whether the release is a prerelease.
	 */
	public final boolean prerelease;
	/**
	 * Markdown body of the release.
	 */
	public final String body;
	/**
	 * Further information.  May be <code>null</code>.
	 */
	public final HiddenInfo hiddenInfo;
	@Override
	public String toString() {
		return "Release [URL=" + URL + ", tag=" + tag + ", title=" + title
				+ ", date=" + date + ", prerelease=" + prerelease + ", body="
				+ body + ", hiddenInfo=" + hiddenInfo + "]";
	}
}

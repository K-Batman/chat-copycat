package com.kaius.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-side half of Chat Copycat.
 *
 * The server broadcasts copied messages formatted like "[MobName] text".
 * Every client that receives one of those messages recognizes the pattern
 * and asks macOS to actually speak it out loud, using one of the built-in
 * "fun" system voices so different mobs sound different. No extra installs
 * needed - these voices ship with every Mac.
 *
 * Mac only for now (macOS's "say" command is what makes this work).
 */
public class ChatCopycatClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("chatcopycat");

	// Matches messages we generated on the server, e.g. "[Zombie] hello".
	private static final Pattern COPYCAT_PATTERN = Pattern.compile("^\\[(.+?)] (.*)$");

	private static final boolean IS_MAC =
			System.getProperty("os.name", "").toLowerCase().contains("mac");

	// Built-in macOS "novelty" voices, mapped to whichever mob fits the vibe best.
	// Full list ships with every Mac - no downloads required.
	private static final Map<String, String> VOICE_MAP = new HashMap<>();

	static {
		VOICE_MAP.put("chicken", "Bubbles");
		VOICE_MAP.put("cow", "Bad News");
		VOICE_MAP.put("pig", "Boing");
		VOICE_MAP.put("sheep", "Bahh");
		VOICE_MAP.put("zombie", "Zarvox");
		VOICE_MAP.put("skeleton", "Whisper");
		VOICE_MAP.put("creeper", "Deranged");
		VOICE_MAP.put("enderman", "Trinoids");
		VOICE_MAP.put("spider", "Hysterical");
		VOICE_MAP.put("villager", "Good News");
	}

	@Override
	public void onInitializeClient() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) {
				return;
			}

			Matcher matcher = COPYCAT_PATTERN.matcher(message.getString());
			if (!matcher.matches()) {
				return;
			}

			String mobName = matcher.group(1);
			String spokenText = matcher.group(2);
			speak(mobName, spokenText);
		});
	}

	private static void speak(String mobName, String text) {
		if (!IS_MAC || text.isBlank()) {
			return;
		}

		Thread worker = new Thread(() -> speakBlocking(mobName, text), "chatcopycat-tts");
		worker.setDaemon(true);
		worker.start();
	}

	private static void speakBlocking(String mobName, String text) {
		String voice = VOICE_MAP.get(mobName.toLowerCase());

		if (voice != null && trySpeak(voice, text)) {
			return;
		}

		// Either no special voice mapped for this mob, or that voice isn't
		// installed on this Mac - fall back to the normal system voice
		// rather than staying silent.
		trySpeak(null, text);
	}

	private static boolean trySpeak(String voice, String text) {
		try {
			ProcessBuilder builder = voice == null
					? new ProcessBuilder("say", text)
					: new ProcessBuilder("say", "-v", voice, text);
			builder.redirectErrorStream(true);
			Process process = builder.start();
			int exitCode = process.waitFor();
			return exitCode == 0;
		} catch (IOException e) {
			LOGGER.warn("Chat Copycat: couldn't run 'say'", e);
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}

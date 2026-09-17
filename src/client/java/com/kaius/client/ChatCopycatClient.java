package com.kaius.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-side half of Chat Copycat.
 *
 * The server broadcasts copied messages formatted like "[MobName] text".
 * Every client that receives one of those messages recognizes the pattern,
 * renders the text with macOS's built-in "say" command, then pitch-shifts
 * (and sometimes adds a small effect to) the result with sox so each mob
 * type sounds different - a chicken sounds squeaky, a zombie sounds low
 * and rough, etc.
 *
 * Requires macOS, plus the free "sox" command (install with: brew install sox).
 * If sox isn't installed, falls back to plain unshifted speech instead of
 * silently doing nothing.
 */
public class ChatCopycatClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("chatcopycat");

	// Matches messages we generated on the server, e.g. "[Zombie] hello".
	private static final Pattern COPYCAT_PATTERN = Pattern.compile("^\\[(.+?)] (.*)$");

	private static final boolean IS_MAC =
			System.getProperty("os.name", "").toLowerCase().contains("mac");

	// Only warn about missing sox once, not on every single message.
	private static final AtomicBoolean WARNED_NO_SOX = new AtomicBoolean(false);

	private record MobVoice(int pitchCents, List<String> extraSoxEffects) {
		MobVoice(int pitchCents, String... extraSoxEffects) {
			this(pitchCents, List.of(extraSoxEffects));
		}
	}

	// Pitch is in cents: 1200 cents = one octave up, -1200 = one octave down.
	private static final Map<String, MobVoice> VOICE_MAP = new HashMap<>();

	static {
		VOICE_MAP.put("chicken", new MobVoice(1200));
		VOICE_MAP.put("cow", new MobVoice(-700));
		VOICE_MAP.put("pig", new MobVoice(300));
		VOICE_MAP.put("sheep", new MobVoice(200, "tremolo", "6", "40"));
		VOICE_MAP.put("zombie", new MobVoice(-500, "overdrive", "10"));
		VOICE_MAP.put("skeleton", new MobVoice(-300, "reverb", "60"));
		VOICE_MAP.put("creeper", new MobVoice(-400, "tremolo", "8", "60"));
		VOICE_MAP.put("enderman", new MobVoice(100, "echo", "0.8", "0.9", "60", "0.4"));
		VOICE_MAP.put("spider", new MobVoice(800, "tremolo", "12", "50"));
		VOICE_MAP.put("villager", new MobVoice(0));
	}

	private static final MobVoice DEFAULT_VOICE = new MobVoice(0);

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

		// Run everything off the game thread so we never stall rendering
		// while the shell commands run.
		Thread worker = new Thread(() -> speakBlocking(mobName, text), "chatcopycat-tts");
		worker.setDaemon(true);
		worker.start();
	}

	private static void speakBlocking(String mobName, String text) {
		MobVoice voice = VOICE_MAP.getOrDefault(mobName.toLowerCase(), DEFAULT_VOICE);

		File raw = null;
		File processed = null;
		try {
			raw = File.createTempFile("copycat_raw", ".aiff");
			processed = File.createTempFile("copycat_out", ".aiff");

			runAndWait("say", "-o", raw.getPath(), text);

			List<String> soxCommand = new ArrayList<>();
			soxCommand.add("sox");
			soxCommand.add(raw.getPath());
			soxCommand.add(processed.getPath());
			soxCommand.add("pitch");
			soxCommand.add(String.valueOf(voice.pitchCents()));
			soxCommand.addAll(voice.extraSoxEffects());

			try {
				runAndWait(soxCommand.toArray(new String[0]));
				runAndWait("afplay", processed.getPath());
			} catch (IOException soxMissing) {
				if (WARNED_NO_SOX.compareAndSet(false, true)) {
					LOGGER.warn("Chat Copycat: 'sox' isn't installed, falling back to " +
							"plain voice with no pitch effects. Run 'brew install sox' " +
							"on your Mac to get the per-mob voices.");
				}
				runAndWait("afplay", raw.getPath());
			}
		} catch (IOException | InterruptedException e) {
			LOGGER.warn("Chat Copycat: couldn't play voice line", e);
			if (Thread.currentThread().isInterrupted()) {
				Thread.currentThread().interrupt();
			}
		} finally {
			if (raw != null) {
				raw.delete();
			}
			if (processed != null) {
				processed.delete();
			}
		}
	}

	private static void runAndWait(String... command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command)
				.redirectErrorStream(true)
				.start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IOException(command[0] + " exited with code " + exitCode);
		}
	}
}

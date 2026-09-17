package com.kaius.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-side half of Chat Copycat.
 *
 * The server broadcasts copied messages formatted like "[MobName] text".
 * Every client that receives one of those messages recognizes the pattern
 * and asks the operating system to actually speak the text out loud, so it
 * really sounds like the mob is talking.
 *
 * Currently only works on macOS, which ships a built-in "say" command.
 */
public class ChatCopycatClient implements ClientModInitializer {
	// Matches messages we generated on the server, e.g. "[Zombie] hello".
	private static final Pattern COPYCAT_PATTERN = Pattern.compile("^\\[(.+?)] (.*)$");

	private static final boolean IS_MAC =
			System.getProperty("os.name", "").toLowerCase().contains("mac");

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

			String spokenText = matcher.group(2);
			speak(spokenText);
		});
	}

	private static void speak(String text) {
		if (!IS_MAC || text.isBlank()) {
			return;
		}

		try {
			// Passing the text as its own argument (not through a shell)
			// keeps this safe even if the message contains weird characters.
			new ProcessBuilder("say", text).start();
		} catch (IOException e) {
			// "say" isn't available - just skip the voice, chat text still shows.
		}
	}
}

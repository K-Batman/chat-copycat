package com.kaius.client;

import net.fabricmc.api.ClientModInitializer;

public class ChatCopycatClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Nothing client-side needed yet. The prank runs on the server side
		// so it works the same for everyone in the world, including on a
		// dedicated server with friends.
	}
}

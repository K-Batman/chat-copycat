package com.kaius;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * Chat Copycat: the nearest living mob to you has a small chance to
 * "repeat" whatever you just said in chat.
 *
 * How it works:
 *  1. Listen for every chat message sent on the server.
 *  2. Roll a die - most of the time nothing happens, so it stays a surprise.
 *  3. Look for the closest living entity (that isn't a player) within
 *     COPY_RANGE blocks of the sender.
 *  4. If one is found, announce that entity "repeating" the message.
 */
public class ChatCopycat implements ModInitializer {
	public static final String MOD_ID = "chatcopycat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// 1 in 5 chance a chat message gets copied.
	private static final int TRIGGER_CHANCE = 5;
	// How far away (in blocks) a mob can be and still "hear" you.
	private static final double COPY_RANGE = 32.0;

	private static final Random RANDOM = new Random();

	@Override
	public void onInitialize() {
		LOGGER.info("Chat Copycat loaded - nearby mobs might start repeating you!");

		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			if (sender == null) {
				return;
			}
			if (RANDOM.nextInt(TRIGGER_CHANCE) != 0) {
				return;
			}

			LivingEntity nearest = findNearestMob(sender);
			if (nearest == null) {
				return;
			}

			String text = message.signedContent();
			String mobName = nearest.getDisplayName().getString();

			Component echo = Component.literal("[" + mobName + "] ").append(Component.literal(text));

			// Broadcast to everyone so friends actually see the "gotcha" moment.
			net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) sender.level();
			level.getServer().getPlayerList().broadcastSystemMessage(echo, false);
		});
	}

	/**
	 * Finds the closest living, non-player entity within COPY_RANGE blocks
	 * of the given player. Returns null if nothing is close enough.
	 */
	private static LivingEntity findNearestMob(ServerPlayer player) {
		AABB searchBox = player.getBoundingBox().inflate(COPY_RANGE);
		List<LivingEntity> nearby = player.level().getEntitiesOfClass(
				LivingEntity.class,
				searchBox,
				entity -> entity != player && entity.isAlive()
		);

		LivingEntity closest = null;
		double closestDistSq = COPY_RANGE * COPY_RANGE;

		for (LivingEntity entity : nearby) {
			double distSq = entity.distanceToSqr(player);
			if (distSq < closestDistSq) {
				closestDistSq = distSq;
				closest = entity;
			}
		}

		return closest;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

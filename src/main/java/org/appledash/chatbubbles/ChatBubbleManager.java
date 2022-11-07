package org.appledash.chatbubbles;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatBubbleManager {
	public static final long DURATION = 5000; /* in milliseconds */
	private static final Pattern[] PATTERNS = {
		Pattern.compile("^<(?<username>.+)> (?<body>.+)$"),  /* Vanilla */
		Pattern.compile("^(?<username>[^:]+): (?<body>.+)$") /* Hypixel */
	};

	private final Map<String, Queue<Entry>> playerEntries = new HashMap<>();

	public void handleMessage(String message) {
		for (Pattern pattern : PATTERNS) {
			Matcher m = pattern.matcher(message);

			if (m.matches()) {
				this.handleMessage(m.group("username"), m.group("body"));
				break;
			}
		}
	}

	private void handleMessage(String username, String body) {
		/* Don't want to queue up chat messages for players that aren't loaded. */
		if (!this.isPlayerLoaded(username)) {
			return;
		}

		Queue<Entry> queue;

		if (this.playerEntries.containsKey(username)) {
			queue = this.playerEntries.get(username);
		} else {
			queue = new ArrayDeque<>();
			this.playerEntries.put(username, queue);
		}

		queue.add(new Entry(body));
	}

	private boolean isPlayerLoaded(String username) {
		MinecraftClient mc = MinecraftClient.getInstance();

		/* This function should only be called when in a world */
		assert mc.world != null;

		return mc.world.getPlayers().stream()
				.anyMatch(p -> p.getName().getString().equals(username));
	}

	public Entry getCurrentEntry(String username) {
		if (!this.playerEntries.containsKey(username)) {
			return null;
		}

		long now = System.currentTimeMillis();
		Queue<Entry> entries = this.playerEntries.get(username);

		while (!entries.isEmpty()) {
			Entry entry = entries.peek();

			/* Top of the queue is an entry that's currently being displayed */
			if (entry.displayedAt() != 0) {
				/* It's expired, get rid of it. */
				if (now >= (entry.displayedAt() + DURATION)) {
					entries.remove();
					continue;
				}

				return entry;
			}

			/* Encountered an entry that hasn't been displayed yet, so start displaying it. */
			entry.displayedAt = System.currentTimeMillis();
			return entry;
		}

		return null;
	}

	public static final class Entry {
		private final String body;
		private long displayedAt;

		public Entry(String body) {
			this.body = body;
		}

		public String body() {
			return this.body;
		}

		public long displayedAt() {
			return this.displayedAt;
		}
	}
}

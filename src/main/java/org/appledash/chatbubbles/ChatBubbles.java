package org.appledash.chatbubbles;

import net.fabricmc.api.ModInitializer;

public class ChatBubbles implements ModInitializer {
	private static ChatBubbles instance;
	private final ChatBubbleManager chatBubbleManager = new ChatBubbleManager();
	private final ChatBubbleRenderer chatBubbleRenderer = new ChatBubbleRenderer(this.chatBubbleManager);

	public ChatBubbles() {
		instance = this;
	}

	@Override
	public void onInitialize() {
	}

	public ChatBubbleManager getChatBubbleManager() {
		return this.chatBubbleManager;
	}

	public ChatBubbleRenderer getChatBubbleRenderer() {
		return this.chatBubbleRenderer;
	}

	public static ChatBubbles getInstance() {
		return instance;
	}
}

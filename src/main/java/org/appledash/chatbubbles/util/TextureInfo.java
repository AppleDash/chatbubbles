package org.appledash.chatbubbles.util;

import net.minecraft.util.Identifier;

/**
 * A simple class to hold the location of a texture, as well as its dimensions.
 */
public record TextureInfo(
	Identifier location,
	int width,
	int height
) {
}

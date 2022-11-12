package org.appledash.chatbubbles;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Matrix4f;
import org.appledash.chatbubbles.util.TextureInfo;

import java.util.List;

public class ChatBubbleRenderer {
	private static final TextureInfo TEXTURE_LEFT = new TextureInfo(new Identifier("chatbubbles", "textures/bubble/left.png"), 13, 32);
	private static final TextureInfo TEXTURE_RIGHT = new TextureInfo(new Identifier("chatbubbles", "textures/bubble/right.png"), 13, 32);
	private static final TextureInfo TEXTURE_MIDDLE = new TextureInfo(new Identifier("chatbubbles", "textures/bubble/middle.png"), 13, 32);
	private static final TextureInfo TEXTURE_FILL = new TextureInfo(new Identifier("chatbubbles", "textures/bubble/fill.png"), 512, 32);

	private final ChatBubbleManager manager;
	private boolean registeredTextures;

	public ChatBubbleRenderer(ChatBubbleManager manager) {
		this.manager = manager;
	}

	/**
	 * Render the chat bubbles for a given player, if any exists.
	 * This function should be called in the context of name tag rendering.
	 *
	 * @param matrices Matrix stack for positioning.
	 * @param entity   Player entity to render name tags for.
	 */
	public void renderBubblesFor(MatrixStack matrices, PlayerEntity entity) {
		String username = entity.getGameProfile().getName();
		ChatBubbleManager.Entry entry = this.manager.getCurrentEntry(username);

		if (!this.registeredTextures) {
			this.registerTextures();
			this.registeredTextures = true;
		}

		if (entry != null) {
			this.renderEntry(matrices, entry);
		}
	}

	/**
	 * Render a single chat bubble entry.
	 *
	 * @param matrices MatrixStack for positioning.
	 * @param entry    Entry to render.
	 */
	private void renderEntry(MatrixStack matrices, ChatBubbleManager.Entry entry) {
		final int maxWidth = 192; /* Maximum width of the text before we split/scale it. */
		final float padding = 6;  /* Padding on left/right side of the text inside the bubble. */

		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		float width = textRenderer.getWidth(entry.body());
		float scale = this.calculateEntryScale(entry);
		float textScale = 1.0F;

		List<OrderedText> texts;

		MutableText bodyText = Text.literal(entry.body());

		/* Too wide? Let's split it across multiple lines. */
		if (width > maxWidth) {
			texts = textRenderer.wrapLines(bodyText, maxWidth);

			/* Too many lines, even after splitting? Scale the text down.
			 * Need to re-split it, since scaling by 0.5 will mean we can fit
			 * twice the text on a line.
			 */
			if (texts.size() > 3) {
				texts = textRenderer.wrapLines(bodyText, maxWidth * 2);
				textScale /= 2;
			}

			width = maxWidth;
		} else {
			texts = List.of(bodyText.asOrderedText());
		}

		/* Need to set these in order to prevent issues with water rendering on top of the chat bubble. */
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();

		matrices.push();
		matrices.scale(scale, scale, scale);
		matrices.translate(-((width + padding) / 2.0F), -32, 0);
		this.renderBubble(matrices, width + padding);
		matrices.scale(textScale, textScale, textScale);

		/* Translate forward just a bit so the text doesn't z-fight with the bubble, since we have depth enabled. */
		matrices.translate(0, 0, -0.005);

		for (int i = 0; i < texts.size(); i++) {
			textRenderer.draw(matrices, texts.get(i), padding / 2, ((textRenderer.fontHeight + 2) * i) + 6, 0xFF000000);
		}

		matrices.pop();
	}

	private void renderBubble(MatrixStack matrices, float desiredWidth) {
		float startY = 0; /* The textures should all be the same height */

		/* The minimum width a bubble can be, dictated by the width of the three required parts of the bubble texture */
		float minWidth = TEXTURE_LEFT.width() + TEXTURE_MIDDLE.width() + TEXTURE_RIGHT.width();
		float fillerWidth = 0.0F; /* How wide the filler texture needs to be, if used. */

		/* Need to use filler, since it's wider than the three base textures on their own would be. */
		if (desiredWidth > minWidth) {
			fillerWidth = (desiredWidth - minWidth) / 2.0F;
		}

		/* Left side of the bubble */
		this.renderTexture(matrices, TEXTURE_LEFT.location(), 0, startY, TEXTURE_LEFT.width(), TEXTURE_LEFT.height());

		/* Filler between the left and the middle, if needed */
		if (fillerWidth != 0) {
			this.renderTexture(matrices, TEXTURE_FILL.location(), TEXTURE_LEFT.width(), startY, fillerWidth, TEXTURE_LEFT.height());
		}

		/* Middle of the bubble, with the arrow */
		this.renderTexture(matrices, TEXTURE_MIDDLE.location(), TEXTURE_LEFT.width() + fillerWidth, startY, TEXTURE_MIDDLE.width(), TEXTURE_MIDDLE.height());

		/* Filler between the middle and the right, if needed */
		if (fillerWidth != 0) {
			this.renderTexture(matrices, TEXTURE_FILL.location(), TEXTURE_LEFT.width() + fillerWidth + TEXTURE_MIDDLE.width(), startY, fillerWidth, TEXTURE_LEFT.height());
		}

		/* Right side of the bubble */
		this.renderTexture(matrices, TEXTURE_RIGHT.location(), TEXTURE_LEFT.width() + TEXTURE_MIDDLE.width() + (fillerWidth * 2), startY, TEXTURE_RIGHT.width(), TEXTURE_RIGHT.height());
	}

	/**
	 * Calculate the scale factor for a given bubble Entry.
	 * We start at 0 and scale up if the bubble is new (fading in,)
	 * or start at 1.0 and scale down if the bubble is old (fading out.)
	 *
	 * @param entry Entry we're calculating the scale for.
	 * @return Scale factor.
	 */
	private float calculateEntryScale(ChatBubbleManager.Entry entry) {
		final long threshold = 200L;

		long now = System.currentTimeMillis();
		long elapsedTime = (now - entry.displayedAt());
		long remainingTime = ChatBubbleManager.DURATION - elapsedTime;

		/* The smallest one will always be the one we care about:
		 * if we're on the way in, the elapsed time will be the smallest,
		 * if we're on the way out, the remaining time will be the smallest. */
		long time = Math.min(elapsedTime, remainingTime);

		if (time <= threshold) {
			return 1.0F / (threshold / Math.min((float) threshold, time));
		}

		return 1.0F;
	}

	/**
	 * Render a texture at the given x and y co-ordinates.
	 *
	 * @param matrices MatrixStack for positioning.
	 * @param texture  Identifier representing the texture.
	 * @param sX       Start X co-ordinate.
	 * @param sY       Start Y co-ordinate.
	 * @param width    Texture width on the screen.
	 * @param height   Texture height on the screen.
	 */
	private void renderTexture(MatrixStack matrices, Identifier texture, float sX, float sY, float width, float height) {
		Matrix4f model = matrices.peek().getPositionMatrix();

		RenderSystem.enableTexture();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, texture);

		BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();

		bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
		bufferBuilder.vertex(model, sX, sY + height, 0).texture(0, 1).next();
		bufferBuilder.vertex(model, sX + width, sY + height, 0).texture(1, 1).next();
		bufferBuilder.vertex(model, sX + width, sY, 0).texture(1, 0).next();
		bufferBuilder.vertex(model, sX, sY, 0).texture(0, 0).next();
		BufferRenderer.drawWithShader(bufferBuilder.end());

		RenderSystem.disableTexture();
	}

	/**
	 * Register the textures we're using with Minecraft's TextureManager.
	 */
	private void registerTextures() {
		TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();

		textureManager.registerTexture(TEXTURE_LEFT.location(), new ResourceTexture(TEXTURE_LEFT.location()));
		textureManager.registerTexture(TEXTURE_RIGHT.location(), new ResourceTexture(TEXTURE_RIGHT.location()));
		textureManager.registerTexture(TEXTURE_MIDDLE.location(), new ResourceTexture(TEXTURE_MIDDLE.location()));
		textureManager.registerTexture(TEXTURE_FILL.location(), new ResourceTexture(TEXTURE_FILL.location()));
	}
}

package dev.evvie.waylandcraft.desktop;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.mixin.NativeImageMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

public class WindowIcon {

	private final Identifier identifier;
	private final int width;
	private final int height;
	private final int[] argb;
	private IconTexture texture = null;

	public WindowIcon(String key, int width, int height, int[] argb) {
		this.identifier = Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "window_icon_" + key);
		this.width = width;
		this.height = height;
		this.argb = Arrays.copyOf(argb, argb.length);
	}

	public Identifier getTextureLocation() {
		upload();
		if(texture == null) return null;
		return identifier;
	}

	private void upload() {
		if(texture != null) return;
		if(width <= 0 || height <= 0 || argb.length != width * height) return;

		ByteBuffer backing = ByteBuffer.allocateDirect(width * height * 4);
		for(int pixel : argb) {
			backing.put((byte) ((pixel >> 16) & 0xff));
			backing.put((byte) ((pixel >> 8) & 0xff));
			backing.put((byte) (pixel & 0xff));
			backing.put((byte) ((pixel >> 24) & 0xff));
		}
		backing.flip();

		long addr = MemoryUtil.memAddress(backing);
		NativeImage image = NativeImageMixin.createImage(NativeImage.Format.RGBA, width, height, false, addr);
		texture = new IconTexture(image, backing);
		texture.upload();

		TextureManager textureManager = Minecraft.getInstance().getTextureManager();
		textureManager.register(identifier, texture);
	}

	private static class IconTexture extends AbstractTexture {

		private final NativeImage image;

		@SuppressWarnings("unused")
		private ByteBuffer backing;

		public IconTexture(NativeImage image, ByteBuffer backing) {
			this.image = image;
			this.backing = backing;
		}

		public void upload() {
			this.texture = RenderSystem.getDevice().createTexture("window icon texture", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, TextureFormat.RGBA8, image.getWidth(), image.getHeight(), 1, 1);
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, image);
			this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
			this.backing = null;
		}

	}

}

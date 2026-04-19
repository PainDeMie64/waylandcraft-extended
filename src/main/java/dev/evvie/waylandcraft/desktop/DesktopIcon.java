package dev.evvie.waylandcraft.desktop;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.commons.codec.digest.DigestUtils;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.mixin.NativeImageMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public class DesktopIcon {
	
	public final String path;
	
	private WaylandCraft wlc;
	
	private IconImage image = null;
	private IconTexture texture = null;
	private final ResourceLocation location;
	
	public DesktopIcon(String appId, String path) {
		this.path = path;
		this.location = ResourceLocation.fromNamespaceAndPath(WaylandCraft.MOD_ID, "icon_" + DigestUtils.sha1Hex(appId));
		this.wlc = WaylandCraft.instance;
	}
	
	public synchronized void preload() {
		if(image != null) return; // image already preloaded
		if(path == null) return;
		
		File file = new File(path);
		
		/* These "file type checks" are valid because according to the Icon Theme Specification
		 * the extension has to be one of ".png", ".xpm" and ".svg" (lowercase) and the extension
		 * signals what type of file we should expect.
		 */
		
		if(getExtension(file).equals("png")) {
			try {
				FileInputStream stream = new FileInputStream(file);
				this.image = IconImage.standard(NativeImage.read(stream));
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		else if(getExtension(file).equals("svg")) {
			final int width = 128;
			final int height = 128;
			
			ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
			long addr = MemoryUtil.memAddress(buf);
			
			if(wlc.bridge.renderSVG(file, width, height, addr)) {
				this.image = IconImage.direct(NativeImageMixin.createImage(NativeImage.Format.RGBA, width, height, false, addr), buf);
			}
		}
	}
	
	public void upload() {
		if(texture != null) return; // image already uploaded
		
		if(image == null) {
			// When upload is called before preload, that necessary step is completed first.
			preload();
		}
		if(image == null) return;
		
		texture = new IconTexture(image);
		texture.upload();
		
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();
		textureManager.register(location, texture);
	}
	
	public ResourceLocation getTextureLocation() {
		this.upload();
		if(texture == null) return null;
		return location;
	}
	
	private String getExtension(File file) {
		String path = file.getAbsolutePath();
		int idx = path.lastIndexOf('.');
		if(idx < 0 || idx >= path.length() - 1) return "";
		
		return path.substring(idx + 1);
	}
	
	private static class IconImage {
		
		public NativeImage nativeImage;
		public boolean close;
		
		// This field holds the NativeImage backing data allocated during image preload (if any).
		// This has to be a field here because otherwise Java would garbage collect the ByteBuffer
		// before the data is uploaded to OpenGL causing a use-after-free bug.
		@SuppressWarnings("unused")
		private ByteBuffer backing;
		
		private IconImage(NativeImage nativeImage, ByteBuffer backing, boolean close) {
			this.nativeImage = nativeImage;
			this.backing = backing;
			this.close = close;
		}
		
		public static IconImage standard(NativeImage image) {
			return new IconImage(image, null, true);
		}
		
		public static IconImage direct(NativeImage image, ByteBuffer backing) {
			return new IconImage(image, backing, false);
		}
		
	}
	
	private static class IconTexture extends AbstractTexture {
		
		public final IconImage image;
		
		public IconTexture(IconImage image) {
			this.image = image;
		}
		
		@Override
		public void load(ResourceManager resourceManager) throws IOException {
		}
		
		public void upload() {
			NativeImage nativeImage = image.nativeImage;
			TextureUtil.prepareImage(getId(), nativeImage.getWidth(), nativeImage.getHeight());
			nativeImage.upload(0, 0, 0, false);
			if(image.close) nativeImage.close();
			
			image.backing = null; // Allow java to garbage collect the data now
		}
		
		@Override
		public void close() {
			releaseId();
		}
		
	}
	
}

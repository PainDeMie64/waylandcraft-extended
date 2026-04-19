package dev.evvie.waylandcraft.render;

import org.lwjgl.opengl.GL33;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.WLCSurface.ViewportSource;
import net.minecraft.client.renderer.CoreShaders;

public class WindowFramebuffer {
	
	public final WLCSurface surfaceTree;
	private RenderTarget target = null;
	
	private int width = 0;
	private int height = 0;
	private int xoff;
	private int yoff;
	
	private WindowFramebuffer(WLCSurface surfaceTree) {
		this.surfaceTree = surfaceTree;
	}
	
	public static WindowFramebuffer renderSurfaceTree(WLCSurface surfaceTree) {
		WindowFramebuffer buf = new WindowFramebuffer(surfaceTree);
		buf.init();
		return buf;
	}
	
	private void init() {
		updateDimensions();
		render();
	}
	
	private void updateDimensions() {
		int minX = 0;
		int minY = 0;
		int maxX = 0;
		int maxY = 0;
		
		for(WLCSurface surface = surfaceTree; surface != null; surface = surface.getNextChild()) {
			int sMinX = surface.xSubpos;
			int sMinY = surface.ySubpos;
			int sMaxX = sMinX + surface.width();
			int sMaxY = sMinY + surface.height();
			
			if(sMinX < minX) minX = sMinX;
			if(sMinY < minY) minY = sMinY;
			if(sMaxX > maxX) maxX = sMaxX;
			if(sMaxY > maxY) maxY = sMaxY;
		}
		
		this.xoff = -minX;
		this.yoff = -minY;
		this.width = maxX - minX;
		this.height = maxY - minY;
	}
	
	private void render() {
		if(width == 0 || height == 0) return;
		
		target = new TextureTarget(width, height, false);
		target.setFilterMode(GL33.GL_NEAREST);
		target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		target.clear();
		
		target.bindWrite(true);
		
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.getModelViewMatrix().identity();
		RenderSystem.getProjectionMatrix().identity();
		
		PoseStack poseStack = new PoseStack();
		poseStack.setIdentity();
		poseStack.translate(-1.0, -1.0, 0.0);
		poseStack.scale(2.0f / width, 2.0f / height, 1.0f);
		
		for(WLCSurface surface = surfaceTree; surface != null; surface = surface.getNextChild()) {
			renderSurface(poseStack, surface, xoff + surface.xSubpos, yoff + surface.ySubpos);
		}
		
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		
		target.unbindWrite();
	}
	
	private void renderSurface(PoseStack poseStack, WLCSurface surface, float x, float y) {
		BufferTexture buf = surface.getBuffer();
		if(buf == null) return;
		
		float w = surface.width();
		float h = surface.height();
		
		float crop_x1 = 0.0f;
		float crop_y1 = 0.0f;
		float crop_x2 = 1.0f;
		float crop_y2 = 1.0f;
		
		ViewportSource src = surface.getViewportSource();
		if(src != null) {
			crop_x1 = (float) (src.x / buf.width);
			crop_y1 = (float) (src.y / buf.height);
			crop_x2 = (float) ((src.x + src.width) / buf.width);
			crop_y2 = (float) ((src.y + src.height) / buf.height);
		}
		
		renderBuffer(poseStack, buf, x, y, w, h, crop_x1, crop_y1, crop_x2, crop_y2);
	}
	
	private void renderBuffer(PoseStack poseStack, BufferTexture buf, float x, float y, float w, float h, float u1, float v1, float u2, float v2) {
		RenderSystem.setShader(buf.format == BufferTexture.FORMAT_XRGB8888 ? CoreShaders.POSITION_TEX : RenderUtils.POSITION_TEX_TRANSLUCENT);
		RenderSystem.setShaderTexture(0, buf.id);
		
		Pose pose = poseStack.last();
		Tesselator tess = Tesselator.getInstance();
		BufferBuilder builder = tess.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		builder.addVertex(pose, x, y, 0).setUv(u1, v1);
		builder.addVertex(pose, x + w, y, 0).setUv(u2, v1);
		builder.addVertex(pose, x + w, y + h, 0).setUv(u2, v2);
		builder.addVertex(pose, x, y + h, 0).setUv(u1, v2);
		BufferUploader.drawWithShader(builder.build());
	}
	
	public void free() {
		if(target != null) target.destroyBuffers();
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}
	
	public int getXOff() {
		return xoff;
	}
	
	public int getYOff() {
		return yoff;
	}
	
	public int getTexture() {
		if(target != null) return target.getColorTextureId();
		return -1;
	}
	
	public boolean isValid() {
		return target != null;
	}
	
}

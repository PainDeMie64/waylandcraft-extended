package dev.evvie.waylandcraft.render;

import java.awt.Color;
import java.io.IOException;
import java.util.function.Function;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;

import dev.evvie.waylandcraft.WaylandCraft;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class RenderUtils {
	
	private static ShaderInstance RENDERTYPE_WINDOW;
	private static ShaderInstance RENDERTYPE_WINDOW_CUTOUT;
	private static ShaderInstance RENDERTYPE_WINDOW_COLORLESS;
	private static ShaderInstance RENDERTYPE_WINDOW_COLORLESS_CUTOUT;
	private static ShaderInstance POSITION_TEX_TRANSLUCENT;
	
	public static void registerShaders(CoreShaderRegistrationCallback.RegistrationContext context) throws IOException {
		context.register(ResourceLocation.fromNamespaceAndPath(WaylandCraft.MOD_ID, "rendertype_window"), DefaultVertexFormat.NEW_ENTITY, shader -> {
			RENDERTYPE_WINDOW = shader;
		});
		context.register(ResourceLocation.fromNamespaceAndPath(WaylandCraft.MOD_ID, "rendertype_window_cutout"), DefaultVertexFormat.NEW_ENTITY, shader -> {
			RENDERTYPE_WINDOW_CUTOUT = shader;
		});
		context.register(ResourceLocation.fromNamespaceAndPath(WaylandCraft.MOD_ID, "rendertype_window_colorless"), DefaultVertexFormat.NEW_ENTITY, shader -> {
			RENDERTYPE_WINDOW_COLORLESS = shader;
		});
		context.register(ResourceLocation.fromNamespaceAndPath(WaylandCraft.MOD_ID, "rendertype_window_colorless_cutout"), DefaultVertexFormat.NEW_ENTITY, shader -> {
			RENDERTYPE_WINDOW_COLORLESS_CUTOUT = shader;
		});
		context.register(ResourceLocation.fromNamespaceAndPath(WaylandCraft.MOD_ID, "position_tex_translucent"), DefaultVertexFormat.POSITION_TEX, shader -> {
			POSITION_TEX_TRANSLUCENT = shader;
		});
	}
	
	public static ShaderInstance getRendertypeWindowShader() {
		return RENDERTYPE_WINDOW;
	}
	
	public static ShaderInstance getRendertypeWindowColorlessShader() {
		return RENDERTYPE_WINDOW_COLORLESS;
	}
	
	public static ShaderInstance getRendertypeWindowCutoutShader() {
		return RENDERTYPE_WINDOW_CUTOUT;
	}
	
	public static ShaderInstance getRendertypeWindowColorlessCutoutShader() {
		return RENDERTYPE_WINDOW_COLORLESS_CUTOUT;
	}
	
	public static ShaderInstance getPositionTexTranslucentShader() {
		return POSITION_TEX_TRANSLUCENT;
	}
	
	public static RenderType rendertypeWindow(int texture) {
		return DummyRenderType.WINDOW.apply(texture);
	}
	
	public static RenderType rendertypeWindowColorless(int texture) {
		return DummyRenderType.WINDOW_COLORLESS.apply(texture);
	}
	
	public static RenderType rendertypeWindowCutout(int texture) {
		return DummyRenderType.WINDOW_CUTOUT.apply(texture);
	}
	
	public static RenderType rendertypeWindowColorlessCutout(int texture) {
		return DummyRenderType.WINDOW_COLORLESS_CUTOUT.apply(texture);
	}
	
	/* This whole subclass dummy is necessary to access the RenderType.CompositeState class */
	private static class DummyRenderType extends RenderType {
		
		public DummyRenderType(String string, VertexFormat vertexFormat, Mode mode, int i, boolean bl, boolean bl2, Runnable runnable, Runnable runnable2) {
			super(string, vertexFormat, mode, i, bl, bl2, runnable, runnable2);
			throw new IllegalStateException("DummyRenderType constructor called");
		}
		
		public static Function<Integer, RenderType> WINDOW = Util.memoize(DummyRenderType::window);
		public static Function<Integer, RenderType> WINDOW_COLORLESS = Util.memoize(DummyRenderType::windowColorless);
		public static Function<Integer, RenderType> WINDOW_CUTOUT = Util.memoize(DummyRenderType::windowCutout);
		public static Function<Integer, RenderType> WINDOW_COLORLESS_CUTOUT = Util.memoize(DummyRenderType::windowColorlessCutout);
		private static final RenderStateShard.ShaderStateShard RENDERTYPE_WINDOW = new RenderStateShard.ShaderStateShard(RenderUtils::getRendertypeWindowShader);
		private static final RenderStateShard.ShaderStateShard RENDERTYPE_WINDOW_COLORLESS = new RenderStateShard.ShaderStateShard(RenderUtils::getRendertypeWindowColorlessShader);
		private static final RenderStateShard.ShaderStateShard RENDERTYPE_WINDOW_CUTOUT = new RenderStateShard.ShaderStateShard(RenderUtils::getRendertypeWindowCutoutShader);
		private static final RenderStateShard.ShaderStateShard RENDERTYPE_WINDOW_COLORLESS_CUTOUT = new RenderStateShard.ShaderStateShard(RenderUtils::getRendertypeWindowColorlessCutoutShader);
		
		private static RenderType window(int texture) {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
					.setShaderState(RENDERTYPE_WINDOW)
					.setTextureState(new TextureIdShard(texture))
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setOutputState(TRANSLUCENT_TARGET)
					.setLightmapState(NO_LIGHTMAP)
					.setOverlayState(NO_OVERLAY)
					.setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
					.createCompositeState(true);
			return create("wlc_window", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE, true, true, compositeState);
		}
		
		private static RenderType windowColorless(int texture) {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
					.setShaderState(RENDERTYPE_WINDOW_COLORLESS)
					.setTextureState(new TextureIdShard(texture))
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setOutputState(TRANSLUCENT_TARGET)
					.setLightmapState(NO_LIGHTMAP)
					.setOverlayState(NO_OVERLAY)
					.setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
					.createCompositeState(true);
			return create("wlc_window_colorless", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE, true, true, compositeState);
		}
		
		private static RenderType windowCutout(int texture) {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
					.setShaderState(RENDERTYPE_WINDOW_CUTOUT)
					.setTextureState(new TextureIdShard(texture))
					.setOutputState(MAIN_TARGET)
					.setLightmapState(NO_LIGHTMAP)
					.setOverlayState(NO_OVERLAY)
					.setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
					.createCompositeState(true);
			return create("wlc_window_cutout", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE, true, true, compositeState);
		}
		
		private static RenderType windowColorlessCutout(int texture) {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
					.setShaderState(RENDERTYPE_WINDOW_COLORLESS_CUTOUT)
					.setTextureState(new TextureIdShard(texture))
					.setOutputState(MAIN_TARGET)
					.setLightmapState(NO_LIGHTMAP)
					.setOverlayState(NO_OVERLAY)
					.setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
					.createCompositeState(true);
			return create("wlc_window_colorless_cutout", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE, true, true, compositeState);
		}
		
		private static class TextureIdShard extends RenderStateShard.EmptyTextureStateShard {
			
			public TextureIdShard(int texture) {
				super(() -> {
					RenderSystem.setShaderTexture(0, texture);
				}, () -> {});
			}
			
		}
		
	}
	
	public static void renderWindow(WindowFramebuffer framebuffer, boolean cutout, Pose pose, Vec3 pos1, Vec3 pos2, Vec3 pos3, Vec3 pos4, Vec2 uv1, Vec2 uv2, Vec2 uv3, Vec2 uv4) {
		Vector3f vec1 = pose.pose().transformPosition((float) pos1.x, (float) pos1.y, (float) pos1.z, new Vector3f());
		Vector3f vec2 = pose.pose().transformPosition((float) pos2.x, (float) pos2.y, (float) pos2.z, new Vector3f());
		Vector3f vec3 = pose.pose().transformPosition((float) pos3.x, (float) pos3.y, (float) pos3.z, new Vector3f());
		Vector3f vec4 = pose.pose().transformPosition((float) pos4.x, (float) pos4.y, (float) pos4.z, new Vector3f());
		
		Vector3f normal = pose.transformNormal(0, 0, 1, new Vector3f());
		
		int overlayCoords = OverlayTexture.NO_OVERLAY;
		int light = LightTexture.FULL_BRIGHT;
		
		BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
		VertexConsumer buffer;
		
		// Front quad
		buffer = source.getBuffer(cutout ? RenderUtils.rendertypeWindowCutout(framebuffer.getTexture()) : RenderUtils.rendertypeWindow(framebuffer.getTexture()));
		buffer.addVertex(/* pos */ vec1.x, vec1.y, vec1.z, /* color */ Color.white.getRGB(), /* uv */ uv1.x, uv1.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		buffer.addVertex(/* pos */ vec2.x, vec2.y, vec2.z, /* color */ Color.white.getRGB(), /* uv */ uv2.x, uv2.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		buffer.addVertex(/* pos */ vec3.x, vec3.y, vec3.z, /* color */ Color.white.getRGB(), /* uv */ uv3.x, uv3.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		buffer.addVertex(/* pos */ vec4.x, vec4.y, vec4.z, /* color */ Color.white.getRGB(), /* uv */ uv4.x, uv4.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		source.endBatch();
		
		// Back quad
		buffer = source.getBuffer(cutout ? RenderUtils.rendertypeWindowColorlessCutout(framebuffer.getTexture()) : RenderUtils.rendertypeWindowColorless(framebuffer.getTexture()));
		buffer.addVertex(/* pos */ vec4.x, vec4.y, vec4.z, /* color */ Color.white.getRGB(), /* uv */ uv4.x, uv4.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		buffer.addVertex(/* pos */ vec3.x, vec3.y, vec3.z, /* color */ Color.white.getRGB(), /* uv */ uv3.x, uv3.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		buffer.addVertex(/* pos */ vec2.x, vec2.y, vec2.z, /* color */ Color.white.getRGB(), /* uv */ uv2.x, uv2.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		buffer.addVertex(/* pos */ vec1.x, vec1.y, vec1.z, /* color */ Color.white.getRGB(), /* uv */ uv1.x, uv1.y, /* overlay */ overlayCoords, /* uv2 */ light, /* normal */ normal.x, normal.y, normal.z);
		source.endBatch();
	}
	
	public static Pose cameraTransformPose(Camera camera) {
		PoseStack matrixStack = new PoseStack();
		matrixStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
		matrixStack.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
		matrixStack.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
		
		return matrixStack.last();
	}
	
	public static void blit(GuiGraphics context, ResourceLocation location, float x, float y, float width, float height) {
		RenderSystem.setShaderTexture(0, location);
		RenderSystem.setShader(RenderUtils::getPositionTexTranslucentShader);
		RenderSystem.enableBlend();
		Matrix4f matrix4f = context.pose().last().pose();
		BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		bufferBuilder.addVertex(matrix4f, x        , y,          0).setUv(0, 0);
		bufferBuilder.addVertex(matrix4f, x        , y + height, 0).setUv(0, 1);
		bufferBuilder.addVertex(matrix4f, x + width, y + height, 0).setUv(1, 1);
		bufferBuilder.addVertex(matrix4f, x + width, y,          0).setUv(1, 0);
		BufferUploader.drawWithShader(bufferBuilder.build());
		RenderSystem.disableBlend();
	}
	
}

package dev.evvie.waylandcraft.render;

import java.util.function.Function;
import java.util.function.Supplier;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.debug.TextureDebug;
import dev.evvie.waylandcraft.mixin.IGuiGraphicsExtractor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

public class RenderUtils {
	
	private static final RenderPipeline.Snippet WINDOW_PIPELINE_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withVertexShader(Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "core/rendertype_window"))
			.withFragmentShader(Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "core/rendertype_window"))
			.withSampler("Sampler0")
			.withDepthStencilState(DepthStencilState.DEFAULT)
			.withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
			.buildSnippet();
	
	private static final RenderPipeline WINDOW_CUTOUT_PIPELINE = RenderPipeline.builder(WINDOW_PIPELINE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "pipeline/window_cutout"))
			.withShaderDefine("ALPHA_CUTOUT")
			.build();
	
	private static final RenderPipeline WINDOW_TRANSLUCENT_PIPELINE = RenderPipeline.builder(WINDOW_PIPELINE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "pipeline/window_translucent"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.build();
	
	private static final RenderPipeline WINDOW_CUTOUT_BACKGROUND_PIPELINE = RenderPipeline.builder(WINDOW_PIPELINE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "pipeline/window_cutout_background"))
			.withShaderDefine("ALPHA_CUTOUT")
			.withShaderDefine("NO_COLOR")
			.build();
	
	private static final RenderPipeline WINDOW_TRANSLUCENT_BACKGROUND_PIPELINE = RenderPipeline.builder(WINDOW_PIPELINE_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "pipeline/window_translucent_background"))
			.withShaderDefine("NO_COLOR")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.build();
	
	public static final Supplier<GpuSampler> WINDOW_SAMPLER = () -> RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, false);
	
	public static final Function<Identifier, RenderType> WINDOW_CUTOUT = Util.memoize(
		(identifier) -> {
			RenderSetup setup = RenderSetup.builder(WINDOW_CUTOUT_PIPELINE)
					.withTexture("Sampler0", identifier, WINDOW_SAMPLER)
					.createRenderSetup();
			return RenderType.create("window_cutout", setup);
		}
	);
	
	public static final Function<Identifier, RenderType> WINDOW_TRANSLUCENT = Util.memoize(
		(identifier) -> {
			RenderSetup setup = RenderSetup.builder(WINDOW_TRANSLUCENT_PIPELINE)
					.withTexture("Sampler0", identifier, WINDOW_SAMPLER)
					.createRenderSetup();
			return RenderType.create("window_translucent", setup);
		}
	);
	
	public static final Function<Identifier, RenderType> WINDOW_BACKGROUND_CUTOUT = Util.memoize(
		(identifier) -> {
			RenderSetup setup = RenderSetup.builder(WINDOW_CUTOUT_BACKGROUND_PIPELINE)
					.withTexture("Sampler0", identifier, WINDOW_SAMPLER)
					.createRenderSetup();
			return RenderType.create("window_cutout_background", setup);
		}
	);
	
	public static final Function<Identifier, RenderType> WINDOW_BACKGROUND_TRANSLUCENT = Util.memoize(
		(identifier) -> {
			RenderSetup setup = RenderSetup.builder(WINDOW_TRANSLUCENT_BACKGROUND_PIPELINE)
					.withTexture("Sampler0", identifier, WINDOW_SAMPLER)
					.createRenderSetup();
			return RenderType.create("window_translucent_background", setup);
		}
	);
	
	public static final RenderPipeline WINDOW_BLIT = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "pipeline/window_blit"))
			.withVertexShader(Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "core/window_blit"))
			.withFragmentShader(Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "core/window_blit"))
			.withSampler("Sampler0")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
			.build();
	
	public static void renderFramebuffer(WindowFramebuffer framebuffer, PoseStack poseStack, SubmitNodeCollector collector, boolean cutout, Vec3 tl, Vec3 bl, Vec3 br, Vec3 tr) {
		renderFramebuffer(framebuffer, poseStack, collector, cutout, tl, bl, br, tr, "3d");
	}

	public static void renderFramebuffer(WindowFramebuffer framebuffer, PoseStack poseStack, SubmitNodeCollector collector, boolean cutout, Vec3 tl, Vec3 bl, Vec3 br, Vec3 tr, String owner) {
		if(!framebuffer.isValid()) return;
		TextureDebug.framebufferPresented(framebuffer, owner);
		
		Function<Identifier, RenderType> renderType;
		
		// Front quad
		renderType = cutout ? WINDOW_CUTOUT : WINDOW_TRANSLUCENT;
		TextureDebug.beforePresentLocation(framebuffer.getTextureLocation(), owner + "/front");
		collector.submitCustomGeometry(poseStack, renderType.apply(framebuffer.getTextureLocation()), new FramebufferRenderInstance(tl, bl, br, tr, false));
		
		// Back quad
		renderType = cutout ? WINDOW_BACKGROUND_CUTOUT : WINDOW_BACKGROUND_TRANSLUCENT;
		TextureDebug.beforePresentLocation(framebuffer.getTextureLocation(), owner + "/back");
		collector.submitCustomGeometry(poseStack, renderType.apply(framebuffer.getTextureLocation()), new FramebufferRenderInstance(tl, bl, br, tr, true));
	}
	
	public static final record FramebufferRenderInstance(Vec3 tl, Vec3 bl, Vec3 br, Vec3 tr, boolean reverse) implements CustomGeometryRenderer {
		
		@Override
		public void render(Pose pose, VertexConsumer buffer) {
			if(!reverse) {
				buffer.addVertex(pose, tl.toVector3f()).setUv(0.0f, 0.0f);
				buffer.addVertex(pose, bl.toVector3f()).setUv(0.0f, 1.0f);
				buffer.addVertex(pose, br.toVector3f()).setUv(1.0f, 1.0f);
				buffer.addVertex(pose, tr.toVector3f()).setUv(1.0f, 0.0f);
			}
			else {
				buffer.addVertex(pose, tr.toVector3f()).setUv(1.0f, 0.0f);
				buffer.addVertex(pose, br.toVector3f()).setUv(1.0f, 1.0f);
				buffer.addVertex(pose, bl.toVector3f()).setUv(0.0f, 1.0f);
				buffer.addVertex(pose, tl.toVector3f()).setUv(0.0f, 0.0f);
			}
		}
		
	}
	
	public static void renderFramebuffer2D(GuiGraphicsExtractor context, WindowFramebuffer framebuffer, int x, int y, int w, int h) {
		renderFramebuffer2D(context, framebuffer, x, y, w, h, "2d");
	}

	public static void renderFramebuffer2D(GuiGraphicsExtractor context, WindowFramebuffer framebuffer, int x, int y, int w, int h, String owner) {
		if(!framebuffer.isValid()) return;
		TextureDebug.framebufferPresented(framebuffer, owner);
		TextureDebug.beforePresentLocation(framebuffer.getTextureLocation(), owner);
		((IGuiGraphicsExtractor) context).invokeInnerBlit(WINDOW_BLIT, framebuffer.getTextureLocation(), x, x + w, y, y + h, 0.0f, 1.0f, 0.0f, 1.0f, -1);
	}

	public static void renderFramebuffer2D(GuiGraphicsExtractor context, WindowFramebuffer framebuffer, FitRect fit) {
		renderFramebuffer2D(context, framebuffer, fit, "2d-fit");
	}

	public static void renderFramebuffer2D(GuiGraphicsExtractor context, WindowFramebuffer framebuffer, FitRect fit, String owner) {
		renderFramebuffer2D(context, framebuffer, (int) Math.round(fit.x()), (int) Math.round(fit.y()), (int) Math.round(fit.width()), (int) Math.round(fit.height()), owner);
	}

	public static FitRect aspectFit(double sourceWidth, double sourceHeight, double destX, double destY, double destWidth, double destHeight) {
		if(sourceWidth <= 0 || sourceHeight <= 0 || destWidth <= 0 || destHeight <= 0) {
			return new FitRect(destX, destY, 0, 0, 0, sourceWidth, sourceHeight);
		}

		double scale = Math.min(destWidth / sourceWidth, destHeight / sourceHeight);
		double width = sourceWidth * scale;
		double height = sourceHeight * scale;
		double x = destX + (destWidth - width) / 2.0;
		double y = destY + (destHeight - height) / 2.0;

		return new FitRect(x, y, width, height, scale, sourceWidth, sourceHeight);
	}

	public static FitRect aspectFit(WindowFramebuffer framebuffer, double destX, double destY, double destWidth, double destHeight) {
		return aspectFit(framebuffer.getWidth(), framebuffer.getHeight(), destX, destY, destWidth, destHeight);
	}

	public static record FitRect(double x, double y, double width, double height, double scale, double sourceWidth, double sourceHeight) {

		public double right() {
			return x + width;
		}

		public double bottom() {
			return y + height;
		}

		public boolean contains(double px, double py) {
			return scale > 0 && px >= x && py >= y && px <= right() && py <= bottom();
		}

		public double sourceX(double px) {
			return (px - x) / scale;
		}

		public double sourceY(double py) {
			return (py - y) / scale;
		}

	}
	
}

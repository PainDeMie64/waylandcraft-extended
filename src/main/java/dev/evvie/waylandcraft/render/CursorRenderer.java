package dev.evvie.waylandcraft.render;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.CursorShape;
import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.IconSurface;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.render.RenderUtils.FitRect;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class CursorRenderer {

	private static final Identifier DEFAULT_CURSOR = Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "textures/gui/cursor/default.png");
	private static final double CURSOR_DEPTH = 0.018;
	private static final double DEFAULT_CURSOR_SIZE = 24;

	public static void renderWorldCursor(LevelRenderContext ctx, PoseStack poseStack, Vec3 localX, Vec3 localY, Vec3 normal, WLCAbstractWindow window, FitRect windowFit) {
		CursorPlacement placement = placementForWindow(window, windowFit);
		if(placement == null) return;

		Vec3 depth = normal.scale(CURSOR_DEPTH);
		IconSurface cursor = WaylandCraft.instance.bridge.cursorIcon;
		if(cursor != null && cursor.framebuffer != null && cursor.framebuffer.isValid()) {
			WindowFramebuffer buf = cursor.framebuffer;
			double x = placement.x - (WaylandCraft.instance.bridge.cursorHotspotX + buf.getXOff()) * windowFit.scale();
			double y = placement.y - (WaylandCraft.instance.bridge.cursorHotspotY + buf.getYOff()) * windowFit.scale();
			double w = buf.getWidth() * windowFit.scale();
			double h = buf.getHeight() * windowFit.scale();
			renderFramebuffer3D(ctx, poseStack, localX, localY, depth, buf, x, y, w, h, "world-cursor");
			return;
		}

		if(WaylandCraft.instance.cursorShape == CursorShape.HIDE) return;
		double size = DEFAULT_CURSOR_SIZE * windowFit.scale();
		renderTexture3D(ctx, poseStack, localX, localY, depth, DEFAULT_CURSOR, placement.x, placement.y, size, size);
	}

	public static void renderScreenCursor(GuiGraphicsExtractor context, WLCAbstractWindow window, FitRect windowFit) {
		CursorPlacement placement = placementForWindow(window, windowFit);
		if(placement == null) return;

		IconSurface cursor = WaylandCraft.instance.bridge.cursorIcon;
		if(cursor != null && cursor.framebuffer != null && cursor.framebuffer.isValid()) {
			WindowFramebuffer buf = cursor.framebuffer;
			double x = placement.x - (WaylandCraft.instance.bridge.cursorHotspotX + buf.getXOff()) * windowFit.scale();
			double y = placement.y - (WaylandCraft.instance.bridge.cursorHotspotY + buf.getYOff()) * windowFit.scale();
			double w = buf.getWidth() * windowFit.scale();
			double h = buf.getHeight() * windowFit.scale();
			RenderUtils.renderFramebuffer2D(context, buf, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), "screen-cursor");
			return;
		}

		if(WaylandCraft.instance.cursorShape == CursorShape.HIDE) return;
		double size = DEFAULT_CURSOR_SIZE * windowFit.scale();
		RenderUtils.renderTexture2D(context, DEFAULT_CURSOR, placement.x, placement.y, size, size);
	}

	private static CursorPlacement placementForWindow(WLCAbstractWindow window, FitRect windowFit) {
		if(WaylandCraft.instance == null || WaylandCraft.instance.bridge == null || WaylandCraft.instance.pointerCapture == null) return null;
		WaylandCraft.PointerCapture capture = WaylandCraft.instance.pointerCapture;
		if(!capture.hard) return null;
		if(window.framebuffer == null) return null;
		if(!surfaceInTree(window.getSurfaceTree(), capture.surface)) return null;

		double x = windowFit.x() + (capture.surface.xSubpos + capture.x + window.framebuffer.getXOff()) * windowFit.scale();
		double y = windowFit.y() + (capture.surface.ySubpos + capture.y + window.framebuffer.getYOff()) * windowFit.scale();
		if(!windowFit.contains(x, y)) return null;
		return new CursorPlacement(x, y);
	}

	private static void renderFramebuffer3D(LevelRenderContext ctx, PoseStack poseStack, Vec3 localX, Vec3 localY, Vec3 depth, WindowFramebuffer framebuffer, double x, double y, double w, double h, String owner) {
		Vec3 tl = localX.scale(x).add(localY.scale(y)).add(depth);
		Vec3 bl = localX.scale(x).add(localY.scale(y + h)).add(depth);
		Vec3 br = localX.scale(x + w).add(localY.scale(y + h)).add(depth);
		Vec3 tr = localX.scale(x + w).add(localY.scale(y)).add(depth);
		RenderUtils.renderFramebuffer(framebuffer, poseStack, ctx.submitNodeCollector(), true, tl, bl, br, tr, owner);
	}

	private static void renderTexture3D(LevelRenderContext ctx, PoseStack poseStack, Vec3 localX, Vec3 localY, Vec3 depth, Identifier texture, double x, double y, double w, double h) {
		Vec3 tl = localX.scale(x).add(localY.scale(y)).add(depth);
		Vec3 bl = localX.scale(x).add(localY.scale(y + h)).add(depth);
		Vec3 br = localX.scale(x + w).add(localY.scale(y + h)).add(depth);
		Vec3 tr = localX.scale(x + w).add(localY.scale(y)).add(depth);
		RenderUtils.renderTexture(poseStack, ctx.submitNodeCollector(), texture, true, tl, bl, br, tr);
	}

	private static boolean surfaceInTree(WLCSurface root, WLCSurface target) {
		for(WLCSurface surface = root; surface != null; surface = surface.getNextChild()) {
			if(surface == target) return true;
		}
		return false;
	}

	private static record CursorPlacement(double x, double y) {}

}

package dev.evvie.waylandcraft.gui;

import java.awt.Color;
import java.util.Calendar;

import org.joml.Matrix3x2fStack;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraft.KeyboardCaptureMode;
import dev.evvie.waylandcraft.bridge.IconSurface;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow.SurfaceGeometry;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.render.RenderUtils;
import dev.evvie.waylandcraft.render.RenderUtils.FitRect;
import dev.evvie.waylandcraft.render.WindowFramebuffer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class WaylandHudRenderer {
	
	private WaylandCraft wlc;
	private static final Identifier TIME_DATE = Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "time-date");
	private static final Identifier APP_LIST = Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "app-list");
	private static final Identifier PINNED_TOPLEVEL = Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "pinned-toplevel");
	private static final Identifier DND_ICON = Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "dnd-icon");
	private static final Identifier ALPHA_SEAL = Identifier.fromNamespaceAndPath(WaylandCraft.MOD_ID, "alpha-seal");
	
	public WaylandHudRenderer(WaylandCraft wlc) {
		this.wlc = wlc;
	}
	
	public void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, TIME_DATE, this::extractTimeDateRenderState);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, APP_LIST, this::extractAppListRenderState);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, PINNED_TOPLEVEL, this::extractPinnedToplevelRenderState);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, DND_ICON, this::extractDNDIconRenderState);
		HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, ALPHA_SEAL, this::extractAlphaSealRenderState);
	}

	private void extractAlphaSealRenderState(GuiGraphicsExtractor context, DeltaTracker deltaTracker) {
		context.fill(RenderUtils.ALPHA_SEAL, 0, 0, context.guiWidth(), context.guiHeight(), 0xffffffff);
	}
	
	private void extractAppListRenderState(GuiGraphicsExtractor context, DeltaTracker deltaTracker) {
		Font font = Minecraft.getInstance().font;
		StatusLine line = focusedStatusLine(font, context.guiWidth());
		if(line == null) return;
		renderStatusLine(context, font, line, context.guiWidth() - line.width - 10, 30);
	}

	private StatusLine focusedStatusLine(Font font, int maxWidth) {
		if(wlc.bridge == null) return null;
		WLCToplevel focused = wlc.bridge.getMostRecentFocus();
		if(focused == null) return null;

		KeyboardCaptureMode mode = WaylandCraft.instance.keyboardCaptureMode;
		String title = focused == null || focused.title == null || focused.title.isBlank() ? "focused window" : focused.title;
		String prefix;
		String suffix;
		int prefixColor;
		int suffixColor;
		if(mode == KeyboardCaptureMode.HARD_CAPTURE) {
			prefix = "Mouse+keys -> ";
			suffix = " (Alt+Q)";
			prefixColor = Color.red.getRGB();
			suffixColor = Color.red.getRGB();
		}
		else if(mode == KeyboardCaptureMode.CAPTURE) {
			prefix = "Keys -> ";
			suffix = " (Alt+G)";
			prefixColor = Color.red.getRGB();
			suffixColor = Color.red.getRGB();
		}
		else {
			prefix = "Focus -> ";
			suffix = "";
			prefixColor = Color.lightGray.getRGB();
			suffixColor = Color.lightGray.getRGB();
		}
		int available = Math.max(40, maxWidth - 20 - font.width(prefix) - font.width(suffix));
		String fittedTitle = fitText(font, title, available);
		return new StatusLine(prefix, fittedTitle, suffix, prefixColor, Color.white.getRGB(), suffixColor, font.width(prefix) + font.width(fittedTitle) + font.width(suffix));
	}

	private void renderStatusLine(GuiGraphicsExtractor context, Font font, StatusLine line, int x, int y) {
		context.text(font, line.prefix, x, y, line.prefixColor, true);
		x += font.width(line.prefix);
		context.text(font, line.title, x, y, line.titleColor, true);
		x += font.width(line.title);
		if(!line.suffix.isEmpty()) context.text(font, line.suffix, x, y, line.suffixColor, true);
	}

	private String fitText(Font font, String text, int maxWidth) {
		if(font.width(text) <= maxWidth) return text;
		String ellipsis = "...";
		while(text.length() > 1 && font.width(text + ellipsis) > maxWidth) {
			text = text.substring(0, text.length() - 1);
		}
		return text + ellipsis;
	}

	private record StatusLine(String prefix, String title, String suffix, int prefixColor, int titleColor, int suffixColor, int width) {}
	
	private void extractPinnedToplevelRenderState(GuiGraphicsExtractor context, DeltaTracker deltaTracker) {
		int guiScale = (int) Minecraft.getInstance().getWindow().getGuiScale();
		
		if(wlc.pinnedToplevel != null && !wlc.pinnedToplevel.isAlive()) wlc.pinnedToplevel = null;
		if(wlc.pinnedToplevel != null) {
			WindowFramebuffer buf = wlc.pinnedToplevel.framebuffer;
			if(buf == null) return;
			
			SurfaceGeometry geometry = wlc.pinnedToplevel.geometry;
			
			FitRect fit = RenderUtils.aspectFit(buf, 0, 0, geometry.width(), geometry.height());
			
			Matrix3x2fStack stack = context.pose();
			stack.pushMatrix();
			stack.scale(1.0f / guiScale * 0.5f, 1.0f / guiScale * 0.5f);
			RenderUtils.renderFramebuffer2D(context, buf, fit, "pinned-hud window=" + wlc.pinnedToplevel.getHandle());
			stack.popMatrix();
		}
	}
	
	private void extractDNDIconRenderState(GuiGraphicsExtractor context, DeltaTracker tracker) {
		int guiScale = (int) Minecraft.getInstance().getWindow().getGuiScale();
		
		IconSurface dndIcon = wlc.bridge.dndIcon;
		if(dndIcon != null && dndIcon.framebuffer != null) {
			WindowFramebuffer buf = dndIcon.framebuffer;
			
			int x = -buf.getXOff();
			int y = -buf.getYOff();
			int w = buf.getWidth();
			int h = buf.getHeight();
			
			Matrix3x2fStack stack = context.pose();
			stack.pushMatrix();
			stack.translate(context.guiWidth() / 2, context.guiHeight() / 2);
			stack.scale(1.0f / guiScale, 1.0f / guiScale);
			RenderUtils.renderFramebuffer2D(context, buf, x, y, w, h, "dnd-icon");
			stack.popMatrix();
		}
	}
	
	private void extractTimeDateRenderState(GuiGraphicsExtractor context, DeltaTracker deltaTracker) {
		Font font = Minecraft.getInstance().font;
		String datetime = String.format("%1$tF %1$tR", Calendar.getInstance());
		
		context.text(font, datetime, context.guiWidth() - font.width(datetime) - 2, 2, Color.white.getRGB(), true);
	}
	
}

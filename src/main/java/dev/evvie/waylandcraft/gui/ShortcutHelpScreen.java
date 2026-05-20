package dev.evvie.waylandcraft.gui;

import java.awt.Color;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public class ShortcutHelpScreen extends Screen {

	private static final Entry[] SHORTCUTS = new Entry[] {
			new Entry("B", "Window manager"),
			new Entry("V", "App launcher"),
			new Entry("Alt+G", "Toggle keyboard capture for focused window"),
			new Entry("Alt+Q", "Toggle mouse and keyboard capture"),
			new Entry("Alt+R", "Rotate the aimed in-world monitor"),
			new Entry("X / Y / Z", "Lock rotation to that axis while rotating"),
			new Entry("Alt tap", "Toggle block and 5 deg snapping"),
			new Entry("Alt+P", "Move the desktop panel in front of you"),
			new Entry("Esc", "Close WaylandCraft screens"),
	};

	private final Screen parent;

	public ShortcutHelpScreen(Screen parent) {
		super(Component.literal("WaylandCraft Shortcuts"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.pos(width / 2 - 55, Math.min(height - 32, panelBottom() + 14))
				.size(110, 20)
				.build());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if(event.key() == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(context, mouseX, mouseY, partialTicks);

		int panelWidth = Math.min(360, width - 32);
		int x = width / 2 - panelWidth / 2;
		int y = panelTop();
		int lineHeight = font.lineHeight + 6;
		int panelHeight = panelBottom() - y;

		context.fill(x, y, x + panelWidth, y + panelHeight, ARGB.color(220, 12, 15, 20));
		context.outline(x, y, panelWidth, panelHeight, Color.white.getRGB());
		context.text(font, title, width / 2 - font.width(title) / 2, y + 10, Color.white.getRGB(), true);

		int keyColumn = x + 18;
		int textColumn = x + 105;
		int rowY = y + 34;
		for(Entry entry : SHORTCUTS) {
			context.text(font, entry.keys(), keyColumn, rowY, 0xfff0c36a, true);
			context.text(font, entry.action(), textColumn, rowY, 0xffeeeeee, true);
			rowY += lineHeight;
		}
	}

	private int panelTop() {
		int panelHeight = 34 + SHORTCUTS.length * (font.lineHeight + 6) + 18;
		return Math.max(14, height / 2 - panelHeight / 2 - 12);
	}

	private int panelBottom() {
		return panelTop() + 34 + SHORTCUTS.length * (font.lineHeight + 6) + 18;
	}

	private static record Entry(String keys, String action) {}

}

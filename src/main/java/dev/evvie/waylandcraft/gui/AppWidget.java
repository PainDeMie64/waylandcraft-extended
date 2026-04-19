package dev.evvie.waylandcraft.gui;

import java.awt.Color;
import java.util.function.Consumer;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import dev.evvie.waylandcraft.render.RenderUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;

public class AppWidget extends AbstractWidget {
	
	private static final ResourceLocation SLOT_THINGY = ResourceLocation.fromNamespaceAndPath(WaylandCraft.MOD_ID, "textures/gui/slot_thingy.png");
	private static final ResourceLocation SLOT_THINGY_SELECTED = ResourceLocation.fromNamespaceAndPath(WaylandCraft.MOD_ID, "textures/gui/slot_thingy_selected_overlay.png");
	
	public final DesktopEntry entry;
	private Consumer<DesktopEntry> launchAction;
	private Font font;
	
	public AppWidget(DesktopEntry entry, Consumer<DesktopEntry> launchAction) {
		super(0, 0, 0, 0, Component.literal(getTitle(entry)));
		this.entry = entry;
		this.launchAction = launchAction;
		this.font = Minecraft.getInstance().font;
	}
	
	private static String getTitle(DesktopEntry entry) {
		return entry.name != null ? entry.name : entry.appId;
	}
	
	@Override
	protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
		int x = getX() + 1;
		int y = getY() + 1;
		int width = getWidth() - 2;
		int height = getHeight() - 2;
		boolean selected = isFocused();
		
		RenderUtils.blit(context, SLOT_THINGY, x, y, width, height);
		if(selected) RenderUtils.blit(context, SLOT_THINGY_SELECTED, x - 1, y - 1, width + 2, height + 2);
		
		ResourceLocation icon = entry.getIcon();
		int iconSize = icon != null ? height - 10 : 0;
		
		MutableComponent text = Component.literal(getTitle(entry));
		if(isHoveredOrFocused()) text = text.withStyle(ChatFormatting.UNDERLINE);
		
		context.enableScissor(x + 4, y + 4, x + width - 4, y + height - 4);
		if(icon != null) RenderUtils.blit(context, icon, x + 5, y + 5, iconSize, iconSize);
		context.drawString(font, text, x + 5 + iconSize + 5, y + height / 2 - font.lineHeight / 2, Color.white.getRGB());
		context.disableScissor();
		
		if(selected) {
			context.renderOutline(x - 1, y - 1, width + 2, height + 2, Color.white.getRGB());
			context.fill(x + 4, y + 4, x + width - 4, y + height - 4, 1, ARGB.color(64, Color.black.getRGB()));
		}
	}
	
	public void launch() {
		launchAction.accept(entry);
	}
	
	@Override
	public void onClick(double mouseX, double mouseY) {
		launch();
	}
	
	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
		if(!visible || !active) return false;
		if(!CommonInputs.selected(key)) return false;
		launch();
		return true;
	}
	
	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		this.defaultButtonNarrationText(narrationElementOutput);
	}
	
}

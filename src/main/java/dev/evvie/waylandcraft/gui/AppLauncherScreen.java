package dev.evvie.waylandcraft.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.joml.Random;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import dev.evvie.waylandcraft.render.RenderUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

public class AppLauncherScreen extends Screen {
	
	private WaylandCraft wlc;
	private AppList list;
	
	public AppLauncherScreen(WaylandCraft wlc) {
		super(Component.literal("App Launcher"));
		
		this.wlc = wlc;
	}
	
	@Override
	protected void init() {
		int listWidth = AppList.ELEMENT_WIDTH;
		int listHeight = 170;
		list = new AppList(this, width / 2 - listWidth / 2, height / 2 - listHeight / 2, listWidth, listHeight);
		this.addRenderableWidget(this.list);
		
		Random random = new Random();
		List<DesktopEntry> entries = wlc.xdgManager.entries().stream().filter((e) -> e.visible && random.nextFloat() < 0.2f).toList();
		list.setEntries(entries);
	}
	
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	
	public void launch(DesktopEntry entry) {
		wlc.bridge.execApp(entry.appId);
		this.onClose();
	}
	
	private static class AppList extends AbstractContainerWidget {
		
		private static final ResourceLocation SCROLLER_SPRITE = new ResourceLocation("widget/scroller");
		private static final ResourceLocation SCROLLER_BACKGROUND_SPRITE = new ResourceLocation("widget/scroller_background");
		
		private static final int SLOT_GAPS = 2;
		private static final int ELEMENT_WIDTH = 200 + 2;
		private static final int ELEMENT_HEIGHT = 32 + 2;
		
		private AppLauncherScreen screen;
		private ArrayList<AppWidget> children = new ArrayList<AppWidget>();
		
		private int maxScroll = 0;
		private int scroll = 0;
		private int contentHeight = 0;
		
		public AppList(AppLauncherScreen screen, int x, int y, int width, int height) {
			super(x, y, width, height, Component.literal("App List"));
			this.screen = screen;
		}
		
		public void setEntries(List<DesktopEntry> entries) {
			children.clear();
			for(DesktopEntry entry : entries) {
				children.add(new AppWidget(screen, entry));
			}
			scroll = 0;
			rearrangeChildren();
		}
		
		private void rearrangeChildren() {
			contentHeight = children.size() * (ELEMENT_HEIGHT + SLOT_GAPS) - SLOT_GAPS;
			maxScroll = Math.max(contentHeight - height, 0);
			
			if(scroll < 0) scroll = 0;
			if(scroll > maxScroll) scroll = maxScroll;
			
			int x = getX();
			int y = getY();
			y -= scroll;
			
			for(int i = 0; i < children.size(); i++) {
				AppWidget widget = children.get(i);
				widget.setRectangle(ELEMENT_WIDTH, ELEMENT_HEIGHT, x + width / 2 - ELEMENT_WIDTH / 2, y + i * (ELEMENT_HEIGHT + SLOT_GAPS));
			}
			
		}
		
		private void scrollTo(AppWidget widget) {
			boolean topCondition = widget.getY() >= getY();
			boolean bottomCondition = widget.getBottom() <= getBottom();
			if(topCondition && bottomCondition) {
				/* Widget already in view */
				return;
			}
			
			int top = children.get(0).getY();
			int bottomScroll = widget.getBottom() - top - height;
			int topScroll = widget.getY() - top;
			
			if(!bottomCondition) scroll = bottomScroll;
			else scroll = topScroll;
			
			if(scroll < 0) scroll = 0;
			if(scroll > maxScroll) scroll = maxScroll;
		}
		
		@Override
		public void setFocused(GuiEventListener guiEventListener) {
			super.setFocused(guiEventListener);
			if(guiEventListener instanceof AppWidget) scrollTo((AppWidget) guiEventListener);
		}
		
		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
			scroll -= (int) scrollY * 10;
			if(scroll < 0) scroll = 0;
			if(scroll > maxScroll) scroll = maxScroll;
			
			return true;
		}
		
		@Override
		protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
			rearrangeChildren();
			
			int x = getX();
			int y = getY();
			int width = getWidth();
			int height = getHeight();
			
			context.renderOutline(x - 1, y - 1, width + 2, height + 2, Color.black.getRGB());
			context.renderOutline(x - 2, y - 2, width + 4, height + 4, Color.black.getRGB());
			
			context.enableScissor(x, y, x + width, y + height);
			
			for(AppWidget child : children) {
				child.render(context, mouseX, mouseY, partialTicks);
			}
			
			context.disableScissor();
			
			int scrollerHeight = height + 4;
			
			int scrollerSize = Math.round(height / (float) contentHeight * scrollerHeight);
			int scrollerPos = Math.round(scroll / (float) contentHeight * scrollerHeight);
			
			context.blitSprite(SCROLLER_BACKGROUND_SPRITE, x + width + 8, y - 2, 6, scrollerHeight);
			context.blitSprite(SCROLLER_SPRITE, x + width + 8, y - 2 + scrollerPos, 6, scrollerSize);
		}
		
		@Override
		protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		}
		
		@Override
		public List<? extends GuiEventListener> children() {
			return children;
		}
		
	}
	
	private static class AppWidget extends AbstractWidget {
		
		private static final ResourceLocation SLOT_THINGY = new ResourceLocation(WaylandCraft.MOD_ID, "textures/gui/slot_thingy.png");
		private static final ResourceLocation SLOT_THINGY_SELECTED = new ResourceLocation(WaylandCraft.MOD_ID, "textures/gui/slot_thingy_selected_overlay.png");
		
		public final DesktopEntry entry;
		private AppLauncherScreen screen;
		private Font font;
		
		public AppWidget(AppLauncherScreen screen, DesktopEntry entry) {
			super(0, 0, 0, 0, Component.literal(getTitle(entry)));
			this.entry = entry;
			this.screen = screen;
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
				context.fill(x + 4, y + 4, x + width - 4, y + height - 4, 1, FastColor.ARGB32.color(64, Color.black.getRGB()));
			}
		}
		
		public void launch() {
			screen.launch(entry);
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
	
}

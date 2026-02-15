package dev.evvie.waylandcraft.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL33;

import dev.evvie.waylandcraft.BufferTexture;
import dev.evvie.waylandcraft.RenderUtils;
import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WindowFramebuffer;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.bridge.WLCPopup;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge;
import dev.evvie.waylandcraft.mixin.IMouseHandlerMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WindowManagerScreen extends Screen {
	
	private WaylandCraft wlc;
	
	private SelectorWidget<WLCToplevel> selector;
	private Button grabButton;
	private Button hideButton;
	private Button resizeButton;
	private Button stickyButton;
	
	private final int margin = 3;
	private final int topMargin = 52;
	private final int rootHeight = 1080;
	
	private int areaWidth;
	private int areaHeight;
	private float scale;
	
	private boolean resizeMode = false;
	private WLCToplevel resizeToplevel = null;
	private double resizeLastX = Double.NaN;
	private double resizeLastY = Double.NaN;
	private int resizeWidth = -1;
	private int resizeHeight = -1;
	
	private WLCToplevel focused = null;
	
	// All window elements currently displayed, sorted by depth from bottom-most (root) to top-most (last leaf)
	private ArrayList<WindowElement> windows = new ArrayList<WindowElement>();
	
	public WindowManagerScreen(WaylandCraft wlc) {
		super(Component.literal("Window Manager"));
		this.wlc = wlc;
	}
	
	@Override
	protected void init() {
		areaWidth = width - margin * 2;
		areaHeight = height - margin - topMargin;
		scale = rootHeight / (float) areaHeight;
		
		selector = new SelectorWidget<WLCToplevel>(margin, topMargin - 15, areaWidth, 15) {
			@Override
			public Component titleForElement(WLCToplevel element) {
				return Component.literal(Optional.ofNullable(element.title).or(() -> Optional.ofNullable(element.appID)).orElse(""));
			}
			
			@Override
			public boolean elementDimColor(WLCToplevel element) {
				return !wlc.hasDisplayFor(element);
			}
			
			@Override
			public @Nullable BufferTexture iconForElement(WLCToplevel element) {
				String appID = element.appID;
				if(appID == null) return null;
				
				BufferTexture icon = wlc.xdgManager.getIcon(appID);
				if(icon == null) return null;
				
				return icon;
			}
		};
		addRenderableWidget(selector);
		
		int buttonWidth = width / 6;
		int buttonHeight = 17;
		
		grabButton = Button.builder(Component.literal("Grab"), this::onGrabPressed)
				.pos(width - margin - buttonWidth, margin)
				.size(buttonWidth, buttonHeight)
				.build();
		
		hideButton = Button.builder(Component.literal("Hide"), this::onHidePressed)
				.pos(width - margin - buttonWidth, margin + buttonHeight)
				.size(buttonWidth, buttonHeight)
				.build();
		
		resizeButton = Button.builder(Component.literal("Resize"), this::onResizePressed)
				.pos(width / 2 - buttonWidth / 2, margin + buttonHeight)
				.size(buttonWidth, buttonHeight)
				.build();
		
		stickyButton = Button.builder(Component.literal("Sticky"), this::onStickyPressed)
				.pos(margin, margin)
				.size(buttonWidth, buttonHeight)
				.build();
		
		addRenderableWidget(grabButton);
		addRenderableWidget(hideButton);
		addRenderableWidget(resizeButton);
		addRenderableWidget(stickyButton);
	}
	
	private void onGrabPressed(Button button) {
		if(focused == null) return;
		
		wlc.grabbedDisplay = wlc.getOrCreateDisplay(focused);
		this.onClose();
	}
	
	private void onHidePressed(Button button) {
		if(focused == null) return;
		
		wlc.displays.removeIf((w) -> w.window == focused);
	}
	
	private void onResizePressed(Button button) {
		if(focused == null) return;
		
		wlc.bridge.sendMotionOutside();
		GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
		
		resizeMode = true;
		resizeToplevel = focused;
		resizeWidth = focused.geometry.width();
		resizeHeight = focused.geometry.height();
		resizeLastX = resizeLastY = Double.NaN;
	}
	
	private void onStickyPressed(Button button) {
		if(focused == null) return;
		
		if(wlc.stickyToplevel != focused) wlc.stickyToplevel = focused;
		else wlc.stickyToplevel = null;
	}
	
	private void exitResizeMode() {
		if(resizeToplevel != null && resizeToplevel.isAlive()) wlc.bridge.resizeToplevel(resizeToplevel, resizeWidth, resizeHeight);
		
		long window = Minecraft.getInstance().getWindow().getWindow();
		GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
		
		/* <HACK> */
		/* The following code makes the game remember at what position the cursor is after it was moved in disabled mode during resize */
		double mouseX[] = new double[1];
		double mouseY[] = new double[1];
		GLFW.glfwGetCursorPos(window, mouseX, mouseY);
		
		MouseHandler mouseHandler = Minecraft.getInstance().mouseHandler;
		mouseHandler.setIgnoreFirstMove(); // don't accumulate any movement in accumulatedDX,DY
		((IMouseHandlerMixin) mouseHandler).invokeOnMove(window, mouseX[0], mouseY[0]);
		/* </HACK> */
		
		resizeMode = false;
		resizeToplevel = null;
	}
	
	@Override
	public boolean isPauseScreen() {
		return false;
	}
	
	@Override
	public void render(GuiGraphics context, int i, int j, float f) {
		super.render(context, i, j, f);
		
		context.hLine(margin, width - margin, topMargin - 1, Color.white.getRGB());
		context.hLine(margin, width - margin, height - margin, Color.white.getRGB());
		
		context.vLine(margin, topMargin - 1, height - margin, Color.white.getRGB());
		context.vLine(width - margin, topMargin - 1, height - margin, Color.white.getRGB());
		
		WLCToplevel[] toplevels = wlc.bridge.getToplevels();
		selector.setEntries(toplevels);
		
		if(resizeMode && !resizeToplevel.isAlive()) {
			exitResizeMode();
		}
		
		WLCToplevel renderToplevel = null;
		
		if(!resizeMode) {
			WLCToplevel lastFocus = focused;
			
			// Update focus to toplevel that has highest focus priority
			focused = wlc.bridge.getMostRecentFocus();
			wlc.bridge.focusSurface(focused);
			
			// Update selected toplevel in selector to currently focused toplevel, only if it changed
			if(selector.selection() == null || focused != lastFocus) {
				selector.select(focused);
			}
			
			// When the selection has changed, change the currently focused toplevel
			if(selector.selection() != focused) {
				focused = selector.selection();
				wlc.bridge.focusSurface(focused);
			}
			
			renderToplevel = focused;
		}
		else {
			focused = null;
			renderToplevel = resizeToplevel;
			
			wlc.bridge.focusSurface(null);
			setFocused(null); // Unfocus any widgets too
		}
		
		windows.clear();
		
		GL33.glEnable(GL33.GL_BLEND);
		
		if(renderToplevel != null) {
			prepareToplevel(renderToplevel);
			
			for(WindowElement element : windows) {
				WindowFramebuffer buf = element.window.framebuffer;
				float x = element.x - buf.getXOff() / scale;
				float y = element.y - buf.getYOff() / scale;
				float w = buf.getWidth() / scale;
				float h = buf.getHeight() / scale;
				RenderUtils.blitGUI(context, buf.getTexture(), x, y, x + w, y + h);
			}
		}
		
		GL33.glDisable(GL33.GL_BLEND);
		
		if(focused != null) {
			grabButton.active = true;
			hideButton.active = wlc.hasDisplayFor(focused);
			resizeButton.active = true;
			stickyButton.active = true;
			stickyButton.setMessage(Component.literal(wlc.stickyToplevel == focused ? "Unsticky" : "Sticky"));
		}
		else {
			grabButton.active = false;
			hideButton.active = false;
			resizeButton.active = false;
			stickyButton.active = false;
			stickyButton.setMessage(Component.literal("Sticky"));
		}
	}
	
	private HoveredSurface surfaceUnderPointer(double x, double y) {
		for(int i = windows.size() - 1; i >= 0; i--) {
			WindowElement element = windows.get(i);
			
			float gx = (float) x - element.x;
			float gy = (float) y - element.y;
			
			float sx = scale * gx;
			float sy = scale * gy;
			
			for(WLCSurface surface = element.window.getSurfaceTreeLast(); surface != null; surface = surface.getPrevChild()) {
				float rx = sx - surface.xSubpos;
				float ry = sy - surface.ySubpos;
				
				int width = surface.width();
				int height = surface.height();
				
				if(rx < 0 || ry < 0 || rx > width || ry > height) {
					continue;
				}
				
				if(!surface.isAlive()) continue;
				
				if(wlc.bridge.inputRegionContains(surface, rx, ry)) {
					return new HoveredSurface(surface, rx, ry);
				}
			}
		}
		
		return null;
	}
	
	@Override
	public void mouseMoved(double x, double y) {
		if(resizeMode) {
			wlc.bridge.sendMotionOutside();
			
			if(!resizeToplevel.isAlive()) {
				exitResizeMode();
				return;
			}
			
			if(Double.isNaN(resizeLastX) || Double.isNaN(resizeLastY)) {
				resizeLastX = x;
				resizeLastY = y;
			}
			
			int dx = (int) ((x - resizeLastX) * scale) / 2;
			int dy = (int) ((y - resizeLastY) * scale) / 2;
			resizeLastX = x;
			resizeLastY = y;
			
			resizeWidth += dx;
			resizeHeight += dy;
			
			resizeWidth = Math.clamp(resizeWidth, 0, 1920);
			resizeHeight = Math.clamp(resizeHeight, 0, 1080);
			
//			WaylandCraft.LOGGER.info("RESIZE " + resizeWidth + ", " + resizeHeight + " [" + resizeInitialWidth + ", " + resizeInitialHeight + "]");
			wlc.bridge.resizeToplevelInteractive(resizeToplevel, resizeWidth, resizeHeight);
			
			return;
		}
		
		HoveredSurface hovered = surfaceUnderPointer(x, y);
		
		if(hovered != null) wlc.bridge.sendMotion(hovered.surface, hovered.rx, hovered.ry);
		else wlc.bridge.sendMotionOutside();
	}
	
	@Override
	public boolean mouseClicked(double x, double y, int mouseButton) {
		if(resizeMode) return true;
		
		if(super.mouseClicked(x, y, mouseButton)) return true;
		
		HoveredSurface hovered = surfaceUnderPointer(x, y);
		if(hovered != null) {
			wlc.bridge.sendButton(0x110 + mouseButton, 1);
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean mouseReleased(double x, double y, int mouseButton) {
		if(resizeMode) {
			exitResizeMode();
			return true;
		}
		
		if(super.mouseReleased(x, y, mouseButton)) return true;
		
		HoveredSurface hovered = surfaceUnderPointer(x, y);
		if(hovered != null) {
			wlc.bridge.sendButton(0x110 + mouseButton, 0);
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
		if(key == GLFW.GLFW_KEY_ESCAPE) {
			this.onClose();
			return true;
		}
		
		if(resizeMode) return true;
		
		// Forward key press to currently focused widget
		if(getFocused() != null && getFocused().keyPressed(key, scancode, modifiers)) return true;
		
		// Forward key press to current window
		if(focused != null) {
			wlc.bridge.pressKey(scancode);
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean keyReleased(int key, int scancode, int modifiers) {
		if(resizeMode) return true;
		
		if(super.keyReleased(key, scancode, modifiers)) return true;
		
		if(focused != null) {
			wlc.bridge.releaseKey(scancode);
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(resizeMode) return true;
		
		if(super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
		
		HoveredSurface hovered = surfaceUnderPointer(mouseX, mouseY);
		
		if(hovered != null) {
			wlc.bridge.sendScroll(0, -scrollY * 10);
			wlc.bridge.sendScroll(1, -scrollX * 10);
			return true;
		}
		
		return false;
	}
	
	@Override
	public void removed() {
		if(resizeMode) exitResizeMode();
		wlc.bridge.sendMotionOutside();
	}
	
	private void prepareToplevel(WLCToplevel toplevel) {
		float x = margin + Math.max(0, areaWidth / 2 - (toplevel.geometry.width() / 2) / scale);
		float y = topMargin + Math.max(0, areaHeight / 2 - (toplevel.geometry.height() / 2) / scale);
		x -= toplevel.geometry.x() / scale;
		y -= toplevel.geometry.y() / scale;
		
		windows.add(new WindowElement(toplevel, x, y));
		
		WindowTree tree = WindowTree.constructTree(wlc.bridge, toplevel);
		preparePopupTree(tree, x, y);
	}
	
	private void preparePopupTree(WindowTree tree, float x, float y) {
		if(tree.window instanceof WLCPopup) {
			WLCPopup popup = (WLCPopup) tree.window;
			
			x += popup.getParent().geometry.x() / scale;
			y += popup.getParent().geometry.y() / scale;
			
			x += popup.offsetX / scale;
			y += popup.offsetY / scale;
			
			x -= popup.geometry.x() / scale;
			y -= popup.geometry.y() / scale;
			
			windows.add(new WindowElement(popup, x, y));
		}
		
		for(WindowTree child : tree.children) {
			preparePopupTree(child, x, y);
		}
	}
	
	public static class WindowElement {
		
		public WLCAbstractWindow window;
		public float x;
		public float y;
		
		public WindowElement(WLCAbstractWindow window, float x, float y) {
			this.window = window;
			this.x = x;
			this.y = y;
		}
		
	}
	
	public static class WindowTree {
		
		public WLCAbstractWindow window;
		public ArrayList<WindowTree> children;
		
		private WindowTree(WLCAbstractWindow window) {
			this.window = window;
			this.children = new ArrayList<WindowTree>();
		}
		
		public static WindowTree constructTree(WaylandCraftBridge bridge, WLCToplevel toplevel) {
			WindowTree tree = new WindowTree(toplevel);
			
			for(WLCPopup popup : bridge.getPopups()) {
				WLCAbstractWindow root;
				for(root = popup; !(root instanceof WLCToplevel); root = ((WLCPopup) root).getParent()) {}
				if(root != toplevel) continue;
				addRecursive(tree, popup);
			}
			
			return tree;
		}
		
		private static WindowTree addRecursive(WindowTree tree, WLCPopup popup) {
			WLCAbstractWindow parentWindow = popup.getParent();
			WindowTree parent;
			if(parentWindow instanceof WLCPopup) {
				parent = addRecursive(tree, (WLCPopup) parentWindow);
			}
			else {
				parent = tree;
			}
			
			for(WindowTree child : parent.children) {
				if(child.window == popup) return child;
			}
			
			WindowTree child = new WindowTree(popup);
			parent.children.add(child);
			return child;
		}
		
	}
	
	private static record HoveredSurface(WLCSurface surface, float rx, float ry) {}
	
}

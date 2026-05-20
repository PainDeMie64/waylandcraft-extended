package dev.evvie.waylandcraft.gui;

import java.awt.Color;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WindowDisplay;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.bridge.WLCPopup;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.bridge.WLCX11Window;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge.Size;
import dev.evvie.waylandcraft.grabs.WindowGrab;
import dev.evvie.waylandcraft.mixin.IMouseHandlerMixin;
import dev.evvie.waylandcraft.render.CursorRenderer;
import dev.evvie.waylandcraft.render.RenderUtils;
import dev.evvie.waylandcraft.render.RenderUtils.FitRect;
import dev.evvie.waylandcraft.render.WindowFramebuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class WindowManagerScreen extends Screen {
	
	private WaylandCraft wlc;
	
	private SelectorWidget<WLCToplevel> selector;
	private ArrayList<Button> buttons = new ArrayList<Button>();
	private Button grabButton;
	private Button resizeButton;
	private Button hideButton;
	private Button pinButton;
	private Button itemButton;
	private Button helpButton;
	
	private boolean resizeMode = false;
	private WLCToplevel resizeToplevel = null;
	private double resizeLastX = Double.NaN;
	private double resizeLastY = Double.NaN;
	private int resizeWidth = -1;
	private int resizeHeight = -1;
	
	// GUI parameters (in GUI scale coordinates!!)
	private final int margin = 3;
	private final int leftMargin = 30;
	private final int topMargin = 40;
	private int areaWidth;
	private int areaHeight;
	private int guiScale;
	
	private WLCToplevel focused = null;
	private WLCToplevel lastFocused = null;
	private int debugOutputBoundsWidth = -1;
	private int debugOutputBoundsHeight = -1;
	private long debugLastPointerLogNanos = 0L;
	
	// All window elements currently displayed, sorted by depth from bottom-most (root) to top-most (last leaf)
	public ArrayList<WindowElement> windows = new ArrayList<WindowElement>();
	
	private ImplicitGrab implicitGrab = null;
	
	public WindowManagerScreen(WaylandCraft wlc) {
		super(Component.literal("Window Manager"));
		this.wlc = wlc;
	}
	
	@Override
	protected void init() {
		areaWidth = width - margin - leftMargin;
		areaHeight = height - margin - topMargin;
		
		int buttonWidth = width / 3 - 5;
		int buttonHeight = 17;
		
		selector = new SelectorWidget<WLCToplevel>(leftMargin - 1, topMargin - 17, areaWidth + 2, 17) {
			@Override
			public Component titleForElement(WLCToplevel element) {
				return Component.literal(Optional.ofNullable(element.title).or(() -> Optional.ofNullable(element.appID)).orElse(""));
			}
			
			@Override
			public boolean elementDimColor(WLCToplevel element) {
				return !wlc.hasDisplayFor(element);
			}
			
			@Override
			public @Nullable Identifier iconForElement(WLCToplevel element) {
				return element.getIcon();
			}
		};
		addRenderableWidget(selector);
		
		grabButton = Button.builder(Component.literal("Grab"), this::onGrabPressed)
				.pos(width - buttonWidth - margin + 1, margin)
				.size(buttonWidth, buttonHeight)
				.build();
		buttons.add(grabButton);
		
		resizeButton = Button.builder(Component.literal("Resize"), this::onResizePressed)
				.pos(width / 2 - buttonWidth / 2, margin)
				.size(buttonWidth, buttonHeight)
				.build();
		buttons.add(resizeButton);
		
		hideButton = SpriteIconButton.builder(Component.literal("Hide"), this::onHidePressed, true)
				.sprite(Identifier.fromNamespaceAndPath("waylandcraft", "hide"), 15, 15)
				.size(22, 22)
				.build();
		hideButton.setPosition(3, topMargin);
		hideButton.setTooltip(Tooltip.create(Component.literal("Hide")));
		hideButton.setTooltipDelay(Duration.ofMillis(700));
		buttons.add(hideButton);
		
		pinButton = SpriteIconButton.builder(Component.literal("Pin"), this::onPinPressed, true)
				.sprite(Identifier.fromNamespaceAndPath("waylandcraft", "pin"), 15, 15)
				.size(22, 22)
				.build();
		pinButton.setPosition(3, topMargin + 30);
		pinButton.setTooltip(Tooltip.create(Component.literal("Pin")));
		pinButton.setTooltipDelay(Duration.ofMillis(700));
		buttons.add(pinButton);
		
		itemButton = SpriteIconButton.builder(Component.literal("Give Window Item"), this::onItemPressed, true)
				.sprite(Identifier.fromNamespaceAndPath("waylandcraft", "window"), 16, 16)
				.size(22, 22)
				.build();
		itemButton.setPosition(3, topMargin + 60);
		itemButton.setTooltip(Tooltip.create(Component.literal("Give Window Item")));
		itemButton.setTooltipDelay(Duration.ofMillis(700));
		buttons.add(itemButton);

		helpButton = SpriteIconButton.builder(Component.literal("Shortcuts"), this::onHelpPressed, true)
				.sprite(Identifier.fromNamespaceAndPath("waylandcraft", "crosshair/help"), 16, 16)
				.size(22, 22)
				.build();
		helpButton.setPosition(3, topMargin + 90);
		helpButton.setTooltip(Tooltip.create(Component.literal("Shortcuts")));
		helpButton.setTooltipDelay(Duration.ofMillis(700));
		buttons.add(helpButton);
		
		addRenderableWidget(grabButton);
		addRenderableWidget(resizeButton);
		addRenderableWidget(hideButton);
		addRenderableWidget(pinButton);
		addRenderableWidget(itemButton);
		addRenderableWidget(helpButton);
		
		wlc.bridge.activateKeyboard();
	}
	
	private void onGrabPressed(Button button) {
		if(focused == null) return;

		WindowDisplay display = wlc.getOrCreateDisplay(focused);
		if(wlc.desktopManager != null) wlc.desktopManager.placeOnCurrentWorkspace(focused, display);
		wlc.pointerGrabs.startExclusive(new WindowGrab(display, 0));
		this.onClose();
	}
	
	private void onHidePressed(Button button) {
		if(focused == null) return;
		
		wlc.displays.removeIf((w) -> w.window == focused);
	}
	
	private void onResizePressed(Button button) {
		if(focused == null || focused.fullscreen) return;
		
		wlc.bridge.sendMotionOutside();
		GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
		
		resizeMode = true;
		resizeToplevel = focused;
		resizeWidth = focused.geometry.width();
		resizeHeight = focused.geometry.height();
		resizeLastX = resizeLastY = Double.NaN;
	}
	
	private void onPinPressed(Button button) {
		if(focused == null) return;
		
		if(wlc.pinnedToplevel != focused) wlc.pinnedToplevel = focused;
		else wlc.pinnedToplevel = null;
	}
	
	private void onItemPressed(Button button) {
		if(focused == null) return;
		wlc.itemManager.giveItem(focused);
	}

	private void onHelpPressed(Button button) {
		Minecraft.getInstance().setScreen(new ShortcutHelpScreen(this));
	}
	
	private void exitResizeMode() {
		if(resizeToplevel != null && resizeToplevel.isAlive()) wlc.bridge.resizeToplevel(resizeToplevel, resizeWidth, resizeHeight);
		
		long window = Minecraft.getInstance().getWindow().handle();
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
	public void extractRenderState(GuiGraphicsExtractor context, int i, int j, float f) {
		super.extractBlurredBackground(context);
		
		context.outline(leftMargin - 1, topMargin - 1, areaWidth + 2, areaHeight + 2, Color.white.getRGB());
		
		guiScale = (int) Minecraft.getInstance().getWindow().getGuiScale();
		int outputBoundsWidth = areaWidth * guiScale;
		int outputBoundsHeight = areaHeight * guiScale;
		wlc.bridge.setOutputBounds(outputBoundsWidth, outputBoundsHeight);
		if(WaylandCraft.DEBUG_WINDOWS && (debugOutputBoundsWidth != outputBoundsWidth || debugOutputBoundsHeight != outputBoundsHeight)) {
			WaylandCraft.LOGGER.info("WLC wm output bounds {}x{} gui={} area={}x{}+{}+{}", outputBoundsWidth, outputBoundsHeight, guiScale, areaWidth, areaHeight, leftMargin, topMargin);
			debugOutputBoundsWidth = outputBoundsWidth;
			debugOutputBoundsHeight = outputBoundsHeight;
		}
		
		WLCToplevel[] toplevels = wlc.bridge.getMappedToplevels();
		selector.setEntries(toplevels);
		
		if(resizeMode && !resizeToplevel.isAlive()) {
			exitResizeMode();
		}
		
		WLCToplevel renderToplevel = null;
		lastFocused = focused;
		
		if(!resizeMode) {
			// Update focus to toplevel that has highest focus priority
			focused = wlc.bridge.getMostRecentFocus();
			wlc.bridge.focusSurface(focused);
			
			// Update selected toplevel in selector to currently focused toplevel, only if it changed
			if(selector.selection() == null || focused != lastFocused) {
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
		
		float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
		Matrix3x2fStack poseStack = context.pose();
		poseStack.pushMatrix();
		poseStack.scale(1 / guiScale, 1 / guiScale);
		
		if(renderToplevel != null) {
			prepareToplevel(renderToplevel);
			
			for(WindowElement element : windows) {
				WindowFramebuffer buf = element.window.framebuffer;
				if(buf == null) continue;
				
				FitRect fit = element.framebufferFit();
				if(WaylandCraft.DEBUG_WINDOWS) logRenderFit(element, fit);
				RenderUtils.renderFramebuffer2D(context, buf, fit, "window-manager window=" + element.window.getHandle());
			}
			for(WindowElement element : windows) {
				WindowFramebuffer buf = element.window.framebuffer;
				if(buf == null) continue;
				CursorRenderer.renderScreenCursor(context, element.window, element.framebufferFit());
			}
		}
		
		poseStack.popMatrix();

		if(WaylandCraft.DEBUG_OVERLAY) {
			renderDebugOverlay(context);
		}
		
		buttons.forEach((b) -> b.setFocused(false));
		
		if(focused != null) {
			grabButton.active = true;
			resizeButton.active = true;
			hideButton.active = wlc.hasDisplayFor(focused);
			pinButton.active = true;
			itemButton.active = true;
		}
		else {
			grabButton.active = false;
			resizeButton.active = false;
			hideButton.active = false;
			pinButton.active = false;
			itemButton.active = false;
		}
		
		buttons.forEach((b) -> b.visible = true);
		selector.visible = true;
		
		super.extractRenderState(context, i, j, f);
	}
	
	@Override
	public void extractBackground(GuiGraphicsExtractor guiGraphics, int i, int j, float f) {
	}
	
	private HoveredSurface surfaceUnderPointer(double x, double y) {
		for(int i = windows.size() - 1; i >= 0; i--) {
			WindowElement element = windows.get(i);
			if(element.scale <= 0.0f) continue;
			
			float sx = element.screenToRootX(x);
			float sy = element.screenToRootY(y);
			
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
					logPointerMapping("hover", element, x, y, sx, sy, surface, rx, ry);
					return new HoveredSurface(element, surface, rx, ry);
				}
			}
			}

			return null;
	}
	
	@Override
	public void mouseMoved(double x, double y) {
		Size bounds = wlc.bridge.getOutputBounds();
		
		x *= guiScale;
		y *= guiScale;
		
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
			
			int dx = (int) (x - resizeLastX) / 2;
			int dy = (int) (y - resizeLastY) / 2;
			resizeLastX = x;
			resizeLastY = y;
			
			resizeWidth += dx;
			resizeHeight += dy;
			
			resizeWidth = Math.clamp(resizeWidth, 0, bounds.width());
			resizeHeight = Math.clamp(resizeHeight, 0, bounds.height());
			
//			WaylandCraft.LOGGER.info("RESIZE " + resizeWidth + ", " + resizeHeight + " [" + resizeInitialWidth + ", " + resizeInitialHeight + "]");
			wlc.bridge.resizeToplevelInteractive(resizeToplevel, resizeWidth, resizeHeight);
			
			return;
		}
		
		HoveredSurface hovered = surfaceUnderPointer(x, y);
		
		if(implicitGrab != null && !implicitGrab.surface.isAlive()) implicitGrab = null;
		
		if(implicitGrab == null) {
			if(hovered != null) wlc.bridge.sendMotionRefocus(hovered.surface, hovered.rx, hovered.ry);
			else wlc.bridge.sendMotionOutside();
		}
		else {
			for(WindowElement elem : windows) {
				WLCSurface surface;
				for(surface = elem.window.getSurfaceTree(); surface != null && surface != implicitGrab.surface; surface = surface.getNextChild()) {}
				if(surface == implicitGrab.surface) {
					// Surface was found in this window elements' surface tree
					
					float rx = elem.screenToRootX(x) - surface.xSubpos;
					float ry = elem.screenToRootY(y) - surface.ySubpos;
					
					logPointerMapping("implicit", elem, x, y, elem.screenToRootX(x), elem.screenToRootY(y), surface, rx, ry);
					wlc.bridge.sendMotion(rx, ry);
					break;
				}
			}
		}
	}
	
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if(resizeMode) return true;
		
		if(super.mouseClicked(event, doubleClick)) return true;
		
		double x = event.x() * guiScale;
		double y = event.y() * guiScale;
		
		HoveredSurface hovered = surfaceUnderPointer(x, y);
		if(implicitGrab == null && hovered != null) {
			implicitGrab = new ImplicitGrab(hovered);
		}
		
		if(implicitGrab != null && !implicitGrab.pressedMouseButtons.contains(event.button())) {
			HoveredSurface target = hoveredForImplicitGrab(x, y);
			if(target != null) focusHoveredSurface(target, "wm-click");
			else logImplicitGrabMissing("wm-click", event.button());

			implicitGrab.pressedMouseButtons.add(event.button());
			wlc.bridge.sendButton(0x110 + event.button(), 1);
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if(resizeMode) {
			exitResizeMode();
			return true;
		}
		
		if(super.mouseReleased(event)) return true;
		
		if(implicitGrab != null && implicitGrab.pressedMouseButtons.contains(event.button())) {
			double x = event.x() * guiScale;
			double y = event.y() * guiScale;
			HoveredSurface target = hoveredForImplicitGrab(x, y);
			if(target != null) focusHoveredSurface(target, "wm-release");
			else logImplicitGrabMissing("wm-release", event.button());

			implicitGrab.pressedMouseButtons.remove(event.button());
			wlc.bridge.sendButton(0x110 + event.button(), 0);
			
			if(implicitGrab.pressedMouseButtons.isEmpty()) implicitGrab = null;
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean keyPressed(KeyEvent event) {
		if(event.key() == GLFW.GLFW_KEY_ESCAPE) {
			this.onClose();
			return true;
		}
		if(event.key() == GLFW.GLFW_KEY_F1) {
			Minecraft.getInstance().setScreen(new ShortcutHelpScreen(this));
			return true;
		}
		
		if(resizeMode) return true;
		
		// Forward key press to currently focused widget
		if(getFocused() != null && getFocused().keyPressed(event)) return true;
		
		// Forward key press to current window
		if(focused != null) {
			int scancode = WaylandCraft.correctScancode(event.scancode());
			wlc.bridge.pressKey(scancode);
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean keyReleased(KeyEvent event) {
		if(resizeMode) return true;
		
		if(super.keyReleased(event)) return true;
		
		if(focused != null) {
			int scancode = WaylandCraft.correctScancode(event.scancode());
			wlc.bridge.releaseKey(scancode);
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if(resizeMode) return true;
		
		if(super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
		
		mouseX *= guiScale;
		mouseY *= guiScale;
		
		HoveredSurface hovered = surfaceUnderPointer(mouseX, mouseY);
		
		if(hovered != null) {
			focusHoveredSurface(hovered, "wm-scroll");
			wlc.bridge.sendScroll(0, -scrollY * 10);
			wlc.bridge.sendScroll(1, -scrollX * 10);
			return true;
		}
		
		return false;
	}
	
	@Override
	public void removed() {
		if(resizeMode) exitResizeMode();
		if(implicitGrab != null) {
			implicitGrab.pressedMouseButtons.forEach((button) -> wlc.bridge.sendButton(0x110 + button, 0));
			implicitGrab = null;
		}
		wlc.bridge.deactivateKeyboard();
	}
	
	private void prepareToplevel(WLCToplevel toplevel) {
		WindowFramebuffer buf = toplevel.framebuffer;
		double sourceWidth = buf != null ? buf.getWidth() : toplevel.geometry.width();
		double sourceHeight = buf != null ? buf.getHeight() : toplevel.geometry.height();
		FitRect fit = toplevelPresentationFit(toplevel, sourceWidth, sourceHeight);
		float x = (float) (fit.x() + (buf == null ? 0 : buf.getXOff()) * fit.scale());
		float y = (float) (fit.y() + (buf == null ? 0 : buf.getYOff()) * fit.scale());
		
		windows.add(new WindowElement(toplevel, x, y, (float) fit.scale()));
		
		WindowTree tree = WindowTree.constructTree(wlc.bridge, toplevel);
		preparePopupTree(tree, x, y, (float) fit.scale());
	}

	private FitRect toplevelPresentationFit(WLCToplevel toplevel, double sourceWidth, double sourceHeight) {
		double contentX = leftMargin * guiScale;
		double contentY = topMargin * guiScale;
		double contentWidth = areaWidth * guiScale;
		double contentHeight = areaHeight * guiScale;

		if(toplevel.fullscreen) {
			return RenderUtils.aspectFit(sourceWidth, sourceHeight, contentX, contentY, contentWidth, contentHeight);
		}

		double naturalWidth = Math.max(sourceWidth, toplevel.geometry.width());
		double naturalHeight = Math.max(sourceHeight, toplevel.geometry.height());
		double presentationScale = Math.min(1.0, Math.min(contentWidth / Math.max(1.0, naturalWidth), contentHeight / Math.max(1.0, naturalHeight)));
		double destWidth = naturalWidth * presentationScale;
		double destHeight = naturalHeight * presentationScale;
		double destX = contentX + (contentWidth - destWidth) / 2.0;
		double destY = contentY + (contentHeight - destHeight) / 2.0;

		return RenderUtils.aspectFit(sourceWidth, sourceHeight, destX, destY, destWidth, destHeight);
	}
	
	private void preparePopupTree(WindowTree tree, float x, float y, float scale) {
		if(tree.window instanceof WLCPopup) {
			WLCPopup popup = (WLCPopup) tree.window;
			
			x += popup.getParent().geometry.x() * scale;
			y += popup.getParent().geometry.y() * scale;
			
			x += popup.offsetX * scale;
			y += popup.offsetY * scale;
			
			x -= popup.geometry.x() * scale;
			y -= popup.geometry.y() * scale;
			
			windows.add(new WindowElement(popup, x, y, scale));
		}
		
		for(WindowTree child : tree.children) {
			preparePopupTree(child, x, y, scale);
		}
	}

	private void renderDebugOverlay(GuiGraphicsExtractor context) {
		Font font = Minecraft.getInstance().font;
		for(WindowElement element : windows) {
			WindowFramebuffer buf = element.window.framebuffer;
			if(buf == null || !buf.isValid()) continue;

			FitRect fit = element.framebufferFit();
			int x = (int) Math.round(fit.x() / guiScale);
			int y = (int) Math.round(fit.y() / guiScale);
			int w = (int) Math.round(fit.width() / guiScale);
			int h = (int) Math.round(fit.height() / guiScale);
			context.outline(x, y, w, h, Color.green.getRGB());

			String label = String.format("src=%dx%d fit=%dx%d scale=%.3f geom=%dx%d", buf.getWidth(), buf.getHeight(), (int) Math.round(fit.width()), (int) Math.round(fit.height()), fit.scale(), element.window.geometry.width(), element.window.geometry.height());
			if(element.window instanceof WLCX11Window x11 && x11.nativeGeometry != null) {
				label += String.format(" x11=%dx%d+%d+%d", x11.nativeGeometry.width(), x11.nativeGeometry.height(), x11.nativeGeometry.x(), x11.nativeGeometry.y());
			}
			context.text(font, label, x + 2, y + 2, Color.green.getRGB(), true);
		}
	}

	private void logRenderFit(WindowElement element, FitRect fit) {
		WindowFramebuffer buf = element.window.framebuffer;
		if(buf == null) return;

		if(element.window instanceof WLCX11Window x11 && x11.nativeGeometry != null) {
			WaylandCraft.LOGGER.info("WLC render fit window={} title={} appID={} source={}x{} framebuffer={}x{} x11={}x{}+{}+{} draw={}x{}+{}+{} scale={}", element.window.getHandle(), x11.title, x11.appID, fit.sourceWidth(), fit.sourceHeight(), buf.getWidth(), buf.getHeight(), x11.nativeGeometry.width(), x11.nativeGeometry.height(), x11.nativeGeometry.x(), x11.nativeGeometry.y(), fit.width(), fit.height(), fit.x(), fit.y(), fit.scale());
		}
		else {
			WaylandCraft.LOGGER.info("WLC render fit window={} source={}x{} framebuffer={}x{} geometry={}x{} draw={}x{}+{}+{} scale={}", element.window.getHandle(), fit.sourceWidth(), fit.sourceHeight(), buf.getWidth(), buf.getHeight(), element.window.geometry.width(), element.window.geometry.height(), fit.width(), fit.height(), fit.x(), fit.y(), fit.scale());
		}
	}

	private void logPointerMapping(String phase, WindowElement element, double screenX, double screenY, double rootX, double rootY, WLCSurface surface, double surfaceX, double surfaceY) {
		if(!WaylandCraft.DEBUG_WINDOWS) return;

		long now = System.nanoTime();
		if(now - debugLastPointerLogNanos < 250_000_000L) return;
		debugLastPointerLogNanos = now;

		FitRect fit = element.framebufferFit();
		WaylandCraft.LOGGER.info("WLC wm pointer {} window={} screen={}x{} root={}x{} surface={} rel={}x{} draw={}x{}+{}+{} scale={}", phase, element.window.getHandle(), screenX, screenY, rootX, rootY, surface.getDebugHandle(), surfaceX, surfaceY, fit.width(), fit.height(), fit.x(), fit.y(), fit.scale());
	}

	private void focusHoveredSurface(HoveredSurface hovered, String reason) {
		WLCToplevel root = rootToplevel(hovered.element.window);
		if(WaylandCraft.DEBUG_WINDOWS) {
			WaylandCraft.LOGGER.info("WLC wm pointer route reason={} window={} surface={} rel={}x{}", reason, describeWindow(hovered.element.window), hovered.surface.getDebugHandle(), hovered.rx, hovered.ry);
		}

		wlc.bridge.sendMotionRefocus(hovered.surface, hovered.rx, hovered.ry, reason);
		if(root != null) wlc.bridge.focusSurface(root);
	}

	private @Nullable HoveredSurface hoveredForImplicitGrab(double screenX, double screenY) {
		if(implicitGrab == null) return null;

		WindowElement element = elementForSurface(implicitGrab.surface);
		if(element == null) return null;

		float rootX = element.screenToRootX(screenX);
		float rootY = element.screenToRootY(screenY);
		float rx = rootX - implicitGrab.surface.xSubpos;
		float ry = rootY - implicitGrab.surface.ySubpos;
		return new HoveredSurface(element, implicitGrab.surface, rx, ry);
	}

	private @Nullable WindowElement elementForSurface(WLCSurface target) {
		for(WindowElement element : windows) {
			for(WLCSurface surface = element.window.getSurfaceTree(); surface != null; surface = surface.getNextChild()) {
				if(surface == target) return element;
			}
		}
		return null;
	}

	private @Nullable WLCToplevel rootToplevel(WLCAbstractWindow window) {
		WLCAbstractWindow root = window;
		while(root instanceof WLCPopup popup) {
			root = popup.getParent();
		}
		return root instanceof WLCToplevel toplevel ? toplevel : null;
	}

	private String describeWindow(WLCAbstractWindow window) {
		WLCToplevel root = rootToplevel(window);
		if(root != null) {
			return "window=" + root.getHandle() + " title=" + root.title + " appID=" + root.appID + " fullscreen=" + root.fullscreen;
		}
		return "window=" + window.getHandle() + " type=" + window.getClass().getSimpleName();
	}

	private void logImplicitGrabMissing(String reason, int button) {
		if(!WaylandCraft.DEBUG_WINDOWS || implicitGrab == null) return;
		WaylandCraft.LOGGER.info("WLC wm pointer route reason={} button={} missing-surface={} owner={}", reason, button, implicitGrab.surface.getDebugHandle(), describeWindow(implicitGrab.window));
	}
	
	public static class WindowElement {
		
		public WLCAbstractWindow window;
		public float x;
		public float y;
		public float scale;
		
		public WindowElement(WLCAbstractWindow window, float x, float y, float scale) {
			this.window = window;
			this.x = x;
			this.y = y;
			this.scale = scale;
		}

		public FitRect framebufferFit() {
			WindowFramebuffer buf = window.framebuffer;
			if(buf == null) return new FitRect(x, y, 0, 0, scale, 0, 0);
			return new FitRect(x - buf.getXOff() * scale, y - buf.getYOff() * scale, buf.getWidth() * scale, buf.getHeight() * scale, scale, buf.getWidth(), buf.getHeight());
		}

		public float screenToRootX(double screenX) {
			return (float) ((screenX - x) / scale);
		}

		public float screenToRootY(double screenY) {
			return (float) ((screenY - y) / scale);
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
			
			for(WLCPopup popup : bridge.getMappedPopups()) {
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
	
	private static record HoveredSurface(WindowElement element, WLCSurface surface, float rx, float ry) {}
	
	private static class ImplicitGrab {
		
		public final WLCAbstractWindow window;
		public final WLCSurface surface;
		public HashSet<Integer> pressedMouseButtons = new HashSet<Integer>();
		
		public ImplicitGrab(HoveredSurface hovered) {
			this.window = hovered.element.window;
			this.surface = hovered.surface;
		}
		
	}
	
}

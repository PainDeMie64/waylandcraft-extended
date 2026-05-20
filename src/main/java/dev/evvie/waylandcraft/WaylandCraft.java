package dev.evvie.waylandcraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;

import dev.evvie.waylandcraft.WindowDisplay.DisplayHitResult;
import dev.evvie.waylandcraft.WindowDisplay.MonitorControl;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow.SurfaceGeometry;
import dev.evvie.waylandcraft.bridge.WLCPopup;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge.ResizeRequest;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge.Size;
import dev.evvie.waylandcraft.desktop.DesktopManager;
import dev.evvie.waylandcraft.desktop.DesktopPanel.PanelHit;
import dev.evvie.waylandcraft.desktop.XDGDesktopManager;
import dev.evvie.waylandcraft.grabs.DNDGrab;
import dev.evvie.waylandcraft.grabs.MonitorClientResizeGrab;
import dev.evvie.waylandcraft.grabs.MonitorMoveGrab;
import dev.evvie.waylandcraft.grabs.MonitorResizeGrab;
import dev.evvie.waylandcraft.grabs.MonitorRotationGrab;
import dev.evvie.waylandcraft.grabs.MonitorRotationGrab.Axis;
import dev.evvie.waylandcraft.grabs.MoveGrab;
import dev.evvie.waylandcraft.grabs.PointerGrabMap;
import dev.evvie.waylandcraft.grabs.PointerGrabMap.ImplicitGrab;
import dev.evvie.waylandcraft.grabs.ResizeGrab;
import dev.evvie.waylandcraft.grabs.WindowGrab;
import dev.evvie.waylandcraft.gui.AppLauncherScreen;
import dev.evvie.waylandcraft.gui.WaylandHudRenderer;
import dev.evvie.waylandcraft.gui.WindowManagerScreen;
import dev.evvie.waylandcraft.item.WindowItem;
import dev.evvie.waylandcraft.item.WindowItemManager;
import dev.evvie.waylandcraft.render.WindowInHandRenderer;
import dev.evvie.waylandcraft.render.WindowInItemFrameRenderer;
import dev.evvie.waylandcraft.render.model.WindowItemModel;
import dev.evvie.waylandcraft.settings.WaylandCraftSettingsManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class WaylandCraft implements ModInitializer, ClientModInitializer {
	public static final String MOD_ID = "waylandcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final boolean DEBUG_WINDOWS = envFlagEnabled("WAYLANDCRAFT_DEBUG_WINDOWS");
	public static final boolean DEBUG_OVERLAY = envFlagEnabled("WAYLANDCRAFT_DEBUG_OVERLAY");
	public static final boolean DEBUG_TEXTURES = envFlagEnabled("WAYLANDCRAFT_DEBUG_TEXTURES");
	private static final KeyMapping.Category KEYBIND_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "keys"));
	
	public static WaylandCraft instance;
	
	public WaylandCraftBridge bridge = null;
	public String waylandSocket = "";
	
	public ArrayList<WindowDisplay> displays = new ArrayList<WindowDisplay>();
	
	public boolean overridePickBlock = false;
	public HitResult trueGameHitResult = null;
	
	public WLCToplevel pinnedToplevel = null;
	
	public WindowItemManager itemManager = new WindowItemManager(this);
	public XDGDesktopManager xdgManager;
	public DesktopManager desktopManager;
	public WaylandCraftSettingsManager settingsManager;
	
	public KeyMapping keyOpenScreen;
	public KeyMapping keyOpenAppLauncher;
	public KeyMapping keyCaptureKeyboard;
	
	public WindowInHandRenderer windowInHandRenderer = new WindowInHandRenderer();
	public WindowInItemFrameRenderer windowInItemFrameRenderer = new WindowInItemFrameRenderer();
	public WaylandHudRenderer hudRenderer = new WaylandHudRenderer(this);
	
	public PointerGrabMap pointerGrabs = new PointerGrabMap(this);
	
	// HitResult of currently hovered WindowDisplay
	// Only non-null, when no exclusive pointer grabs are currently active
	public DisplayHitResult hoveredDisplay = null;
	
	public KeyboardCaptureMode keyboardCaptureMode = KeyboardCaptureMode.NONE;
	
	public PointerCapture pointerCapture = null;
	public PanelHit hoveredPanel = null;
	public boolean snapMonitorPlacement = false;
	private boolean altChordActive = false;
	private boolean altChordClean = false;
	private MonitorRotationGrab rotationGrab = null;
	private @Nullable WindowDisplay editedDisplay = null;
	private @Nullable WindowDisplay activeChromeDisplay = null;
	
	public boolean playerUsingWindowItem = false;
	
	public @Nullable CursorShape cursorShape = null;
	private int debugOutputWidth = -1;
	private int debugOutputHeight = -1;
	private int debugOutputBoundsWidth = -1;
	private int debugOutputBoundsHeight = -1;

	private static boolean envFlagEnabled(String name) {
		String value = System.getenv(name);
		return value != null && !value.isBlank() && !value.equals("0") && !value.equalsIgnoreCase("false") && !value.equalsIgnoreCase("no");
	}
	
	@Override
	public void onInitialize() {
		WindowItem.register();
	}
	
	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing WaylandCraft");
		if(DEBUG_WINDOWS || DEBUG_OVERLAY || DEBUG_TEXTURES) {
			LOGGER.info("WLC debug flags windows={} overlay={} textures={}", DEBUG_WINDOWS, DEBUG_OVERLAY, DEBUG_TEXTURES);
		}
		
		instance = this;
		
		keyOpenScreen = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.windowManager", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, KEYBIND_CATEGORY));
		keyOpenAppLauncher = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.appLauncher", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KEYBIND_CATEGORY));
		keyCaptureKeyboard = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.captureKeyboard", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEYBIND_CATEGORY));
		
		LevelRenderEvents.COLLECT_SUBMITS.register(this::renderWorld);
		LevelRenderEvents.END_EXTRACTION.register(this::updateWorld);
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
		ServerTickEvents.START_LEVEL_TICK.register(itemManager::onServerTick);
		ClientPlayConnectionEvents.JOIN.register(this::onClientJoin);
		
		WindowItemModel.register();
		hudRenderer.register();
	}
	
	/* Update bridge and clients. May be called at any state of the game, even outside of a level
	 * Called before game render in Minecraft::runTick
	 */
	public void update() {
		if(bridge == null) {
			bridge = WaylandCraftBridge.start();
			waylandSocket = bridge.getSocket();
			xdgManager = new XDGDesktopManager(this);
			settingsManager = new WaylandCraftSettingsManager(this);
			desktopManager = new DesktopManager(this);
			
			LOGGER.info("Server started on " + waylandSocket);
		}
		bridge.update();
	}
	
	public void renderWorld(LevelRenderContext ctx) {
		if(bridge == null) return;
		
		displays.forEach((d) -> d.render(ctx));
		if(desktopManager != null) desktopManager.render(ctx);
	}
	
	public void updateWorld(LevelExtractionContext ctx) {
		for(WLCPopup popup : bridge.getMappedPopups()) {
			WLCAbstractWindow root = popup;
			while((root = ((WLCPopup) root).getParent()) instanceof WLCPopup);
			
			WLCToplevel toplevel = (WLCToplevel) root;
			boolean toplevelHasWindow = hasDisplayFor(toplevel);
			boolean popupHasWindow = hasDisplayFor(popup);
			if(toplevelHasWindow && !popupHasWindow) {
				getOrCreateDisplay(popup);
			}
			else if(!toplevelHasWindow && popupHasWindow) {
				displays.removeIf((w) -> w.window == popup);
			}
		}
		
		displays.removeIf((d) -> !d.isValid());
		displays.forEach((d) -> d.updateGeometry());
		if(desktopManager != null) desktopManager.update();
		
		for(WLCPopup popup : bridge.getMappedPopups()) {
			anchorToParent(popup);
		}
		
		updateDisplayRequests();
		
		itemManager.giveItemsIfMissing(bridge.getNewToplevels());
		
		boolean inWMScreen = Minecraft.getInstance().screen instanceof WindowManagerScreen;
		
		// Make sure the toplevels are focused in their respective order and being refocused when a toplevel disappears
		if(!inWMScreen) {
			WLCToplevel focus = bridge.getMostToLeastRecentFocus()
					.filter((t) -> hasDisplayFor(t))
					.findFirst()
					.orElse(null);
			
			bridge.focusSurface(focus);
		}
		
		Camera camera = ctx.camera();
		processPointerMotion(camera);
		
		if(Minecraft.getInstance().player == null || !Minecraft.getInstance().player.isUsingItem()) playerUsingWindowItem = false;
		if(playerUsingWindowItem) {
			ItemStack item = Minecraft.getInstance().player.getUseItem();
			if(item.is(WindowItem.WINDOW)) {
				WLCToplevel toplevel = WindowItem.getToplevel(item);
				
				if(toplevel != null) {
					WindowDisplay display = getOrCreateDisplay(toplevel);
					display.anchorToCamera(camera);
					if(desktopManager != null) desktopManager.placeOnCurrentWorkspace(toplevel, display);
					WaylandCraft.instance.bridge.focusSurface(toplevel);
				}
			}
			else playerUsingWindowItem = false;
		}
		
		updateOutputSize(inWMScreen);
	}
	
	public void enableKeyboardCapture(boolean hardCapture) {
		if(hardCapture) {
			if(keyboardCaptureMode == KeyboardCaptureMode.HARD_CAPTURE) return;
			if(!enableHardPointerCapture("hard-capture-toggle")) return;
			keyboardCaptureMode = KeyboardCaptureMode.HARD_CAPTURE;
			bridge.activateKeyboard();
			Minecraft.getInstance().mouseHandler.grabMouse();
			return;
		}

		if(keyboardCaptureMode != KeyboardCaptureMode.NONE) return;

		keyboardCaptureMode = KeyboardCaptureMode.CAPTURE;
		bridge.activateKeyboard();
	}

	private boolean enableHardPointerCapture(String reason) {
		WLCToplevel target = bridge.getMostRecentFocus();
		if(target == null && hoveredDisplay != null) {
			target = rootToplevel(hoveredDisplay.target.window);
		}
		if(target == null || target.getSurfaceTree() == null || !target.getSurfaceTree().isAlive()) {
			if(DEBUG_WINDOWS) LOGGER.info("WLC pointer hard capture skipped reason={} target=none", reason);
			return false;
		}

		WLCSurface surface = target.getSurfaceTree();
		double x = Math.max(0.0, surface.width() / 2.0);
		double y = Math.max(0.0, surface.height() / 2.0);

		bridge.focusSurface(target);
		bridge.sendMotionRefocus(surface, x, y, reason);
		bridge.maybeLockPointer(surface);
		pointerCapture = new PointerCapture(surface, x, y, true);
		hoveredDisplay = null;
		overridePickBlock = true;
		if(DEBUG_WINDOWS) {
			LOGGER.info("WLC pointer capture start reason={} mode=hard surface={} owner={} at={}x{}", reason, surface.getDebugHandle(), describeSurfaceOwner(surface), x, y);
		}
		return true;
	}
	
	public void disableKeyboardCapture() {
		disableKeyboardCapture("keyboard-capture-disabled");
	}

	public void disableKeyboardCapture(String reason) {
		if(keyboardCaptureMode == KeyboardCaptureMode.NONE) {
			disablePointerCapture(reason);
			return;
		}
		
		keyboardCaptureMode = KeyboardCaptureMode.NONE;
		bridge.deactivateKeyboard();
		disablePointerCapture(reason);
	}

	private void releaseInputCaptureForUi(String reason) {
		disableKeyboardCapture(reason);
		bridge.deactivateKeyboard();
		pointerGrabs.releaseAll();
		rotationGrab = null;
		editedDisplay = null;
		bridge.sendMotionOutside();
	}
	
	public void onClientTick(Minecraft minecraft) {
		if(minecraft.player == null) return;
		
		if(keyOpenScreen.consumeClick()) {
			releaseInputCaptureForUi("open-window-manager");
			minecraft.setScreen(new WindowManagerScreen(WaylandCraft.instance));
			return;
		}
		
		if(keyOpenAppLauncher.consumeClick()) {
			minecraft.setScreen(new AppLauncherScreen(WaylandCraft.instance));
			return;
		}

		if(desktopManager != null && keyCaptureKeyboard.consumeClick()) {
			desktopManager.placePanelInFrontOfPlayer();
			return;
		}
		
	}
	
	private void onClientJoin(ClientPacketListener listener, PacketSender sender, Minecraft minecraft) {
		minecraft.getChatListener().handleSystemMessage(Component.literal("Wayland compositor running on " + waylandSocket), false);
		itemManager.giveItemsIfMissing(bridge.getMappedToplevels());
	}
	
	private void updateDisplayRequests() {
		// Hide all windows that were minimized and unset minimize requested state
		if(desktopManager != null) {
			for(WindowDisplay display : displays) {
				if(display.window instanceof WLCToplevel toplevel && toplevel.requests.minimize) desktopManager.markMinimized(toplevel);
			}
		}
		if(displays.removeIf((w) -> w.window instanceof WLCToplevel && ((WLCToplevel) w.window).requests.minimize) && desktopManager != null) desktopManager.markDirty();
		Stream.of(bridge.getToplevels()).forEach((t) -> t.requests.minimize = false);
		
		// Handle any maximize or unmaximize requests
		for(WLCToplevel toplevel : bridge.getMappedToplevels()) {
			if(toplevel.requests.maximize && toplevel.requests.unmaximize) {
				// Both requests shouldn't happen at the same time
				toplevel.restoreGeometry = null;
			}
			else if(toplevel.requests.maximize) {
				// Maximize toplevel and store its old geometry
				toplevel.restoreGeometry = toplevel.geometry;
				bridge.maximizeToplevel(toplevel);
			}
			else if(toplevel.requests.unmaximize) {
				// Unmaximize toplevel and attempt to restore old geometry
				SurfaceGeometry newGeometry = toplevel.restoreGeometry;
				if(newGeometry == null) newGeometry = toplevel.geometry;
				
				// resizeToplevel also unsets the maximize flag
				bridge.resizeToplevel(toplevel, newGeometry.width(), newGeometry.height());
				toplevel.restoreGeometry = null;
			}
			
			toplevel.requests.maximize = toplevel.requests.unmaximize = false;
		}
		
		// Handle any fullscreen or unfullscreen requests
		for(WLCToplevel toplevel : bridge.getToplevels()) {
			if(toplevel.requests.fullscreen && toplevel.requests.unfullscreen) {
				// Both requests shouldn't happen at the same time
				toplevel.restoreGeometry = null;
			}
			else if(toplevel.requests.fullscreen) {
				// Fullscreen toplevel and store its old geometry
				toplevel.restoreGeometry = toplevel.geometry;
				bridge.fullscreenToplevel(toplevel);
			}
			else if(toplevel.requests.unfullscreen) {
				// Unfullscreen toplevel and attempt to restore old geometry
				SurfaceGeometry newGeometry = toplevel.restoreGeometry;
				if(newGeometry == null) newGeometry = toplevel.geometry;
				
				// resizeToplevel also unsets the fullscreen flag
				bridge.resizeToplevel(toplevel, newGeometry.width(), newGeometry.height());
				toplevel.restoreGeometry = null;
			}
			
			toplevel.requests.fullscreen = toplevel.requests.unfullscreen = false;
		}
		
		Integer moveRequest = bridge.checkMoveRequest();
		if(moveRequest != null) {
			ImplicitGrab implicit = pointerGrabs.dropImplicitMatching(moveRequest.intValue());
			if(implicit != null) {
				// The serial matched an active implicit grab
				pointerGrabs.startExclusive(new MoveGrab(implicit));
			}
		}
		
		ResizeRequest resizeRequest = bridge.checkResizeRequest();
		if(resizeRequest != null) {
			ImplicitGrab implicit = pointerGrabs.dropImplicitMatching(resizeRequest.serial());
			if(implicit != null) {
				// The serial matched an active implicit grab
				pointerGrabs.startExclusive(new ResizeGrab(implicit, resizeRequest.edges()));
			}
		}
		
		Integer dndRequest = bridge.checkDndRequest();
		if(dndRequest != null) {
			ImplicitGrab implicit = pointerGrabs.dropImplicitMatching(dndRequest);
			if(implicit != null) {
				LOGGER.info("DND STARTED");
				// The serial matched an active implicit grab
				pointerGrabs.startExclusive(new DNDGrab(implicit));
			}
			else {
				// Couldn't match implicit grab, have to cancel dnd
				LOGGER.info("drag and drop did not match implicit grab");
				bridge.dndCancel();
			}
		}
	}
	
	private void updateOutputSize(boolean inWMScreen) {
		int outputWidth = Minecraft.getInstance().getWindow().getWidth();
		int outputHeight = Minecraft.getInstance().getWindow().getHeight();
		
		Size size = bridge.getOutputSize();
		if(size.width() != outputWidth || size.height() != outputHeight) {
			bridge.resizeOutput(outputWidth, outputHeight);
		}
		if(DEBUG_WINDOWS && (debugOutputWidth != outputWidth || debugOutputHeight != outputHeight)) {
			LOGGER.info("WLC output size {}x{} inWMScreen={}", outputWidth, outputHeight, inWMScreen);
			debugOutputWidth = outputWidth;
			debugOutputHeight = outputHeight;
		}

		Size bounds = bridge.getOutputBounds();
		if(!inWMScreen && (bounds.width() != outputWidth || bounds.height() != outputHeight)) {
			bridge.setOutputBounds(outputWidth, outputHeight);
		}
		Size updatedBounds = bridge.getOutputBounds();
		if(DEBUG_WINDOWS && (debugOutputBoundsWidth != updatedBounds.width() || debugOutputBoundsHeight != updatedBounds.height())) {
			LOGGER.info("WLC output bounds {}x{} inWMScreen={}", updatedBounds.width(), updatedBounds.height(), inWMScreen);
			debugOutputBoundsWidth = updatedBounds.width();
			debugOutputBoundsHeight = updatedBounds.height();
		}
	}
	
	public @Nullable WindowDisplay getDisplay(WLCAbstractWindow window) {
		return displays.stream().filter((w) -> w.window == window).findAny().orElse(null);
	}
	
	public WindowDisplay getOrCreateDisplay(WLCAbstractWindow window) {
		WindowDisplay display = getDisplay(window);
		if(display != null) return display;
		
		display = new WindowDisplay(window);
		displays.add(display);
		if(desktopManager != null && window instanceof WLCToplevel) desktopManager.markDirty();
		
		return display;
	}
	
	public boolean hasDisplayFor(WLCAbstractWindow window) {
		return getDisplay(window) != null;
	}

	public boolean isDisplayHighlighted(WindowDisplay display) {
		if(display == null) return false;
		if(editedDisplay == display) return true;
		if(activeChromeDisplay == display) return true;
		if(rotationGrab != null && rotationGrab.window() == display) return true;
		if(hoveredDisplay != null && hoveredDisplay.target == display) return true;

		WLCToplevel focused = bridge == null ? null : bridge.getMostRecentFocus();
		WLCToplevel root = rootToplevel(display.window);
		return focused != null && focused == root;
	}

	public boolean isDisplayChromeActive(WindowDisplay display) {
		return display != null && (activeChromeDisplay == display || isDisplayHighlighted(display));
	}

	public boolean isMonitorControlHovered(WindowDisplay display, MonitorControl control) {
		return hoveredDisplay != null && hoveredDisplay.target == display && hoveredDisplay.control == control;
	}

	public void snapDisplayPlacement(WindowDisplay display) {
		if(!snapMonitorPlacement) return;
		display.pivot = new Vec3(Math.rint(display.pivot.x), Math.rint(display.pivot.y), Math.rint(display.pivot.z));
	}

	public void snapDisplayOrientation(WindowDisplay display) {
		if(!snapMonitorPlacement) return;

		Vec3 normal = display.normal().normalize();
		Vec3 down = display.down().normalize();
		double yaw = Math.atan2(normal.x, normal.z);
		double pitch = Math.asin(Math.clamp(normal.y, -1.0, 1.0));
		yaw = snapAngleRadians(yaw);
		pitch = snapAngleRadians(pitch);

		Vec3 snappedNormal = new Vec3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).normalize();
		Vec3 snappedRight = down.cross(snappedNormal);
		if(snappedRight.lengthSqr() < 0.000001) snappedRight = new Vec3(1, 0, 0);
		snappedRight = snappedRight.normalize();
		Vec3 snappedDown = snappedNormal.cross(snappedRight).normalize();
		display.rotate(snappedNormal, snappedDown);
	}

	public double snapAngleRadians(double angle) {
		if(!snapMonitorPlacement) return angle;
		double step = Math.toRadians(5);
		return Math.rint(angle / step) * step;
	}
	
	public void disablePointerCapture() {
		disablePointerCapture("pointer-capture-disabled");
	}

	public void disablePointerCapture(String reason) {
		if(DEBUG_WINDOWS && pointerCapture != null) {
			LOGGER.info("WLC pointer capture end reason={} surface={} owner={}", reason, pointerCapture.surface.getDebugHandle(), describeSurfaceOwner(pointerCapture.surface));
		}

		bridge.unlockPointer();
		bridge.sendMotionOutside();
		pointerCapture = null;
		hoveredDisplay = null;
		cursorShape = null;
		overridePickBlock = false;
	}
	
	private void processPointerMotion(Camera camera) {
		this.cursorShape = null;
		
		if(pointerCapture != null) {
			if(!pointerCapture.surface.isAlive()) {
				disablePointerCapture("surface-dead");
				return;
			}
			
			this.cursorShape = bridge.getCursorShape();
			
			boolean locked = bridge.maybeLockPointer(pointerCapture.surface);
			if(!pointerCapture.hard && !locked) {
				disablePointerCapture("lock-lost");
			}
			
			return;
		}
		
		// Reset hovered display and pick block override
		this.hoveredDisplay = null;
		this.hoveredPanel = null;
		this.overridePickBlock = false;
		
		if(Minecraft.getInstance().screen instanceof WindowManagerScreen) {
			return;
		}
		else if(Minecraft.getInstance().screen != null) {
			pointerGrabs.releaseAll();
			bridge.sendMotionOutside();
			return;
		}
		
		Vec3 pos = camera.position();
		Vec3 look = new Vec3(camera.forwardVector());
		Vec3 up = new Vec3(camera.upVector());
		
		DisplayHitResult finalHitResult = null;
		double finalDistance = Double.POSITIVE_INFINITY;
		for(WindowDisplay display : displays) {
			DisplayHitResult hit = display.intersect(pos, look);
			if(hit == null || hit.isMiss()) continue;
			
			double dist = hit.position.distanceToSqr(pos);
			if(finalHitResult == null || dist < finalDistance) {
				finalHitResult = hit;
				finalDistance = dist;
			}
		}

		PanelHit panelHit = null;
		double panelDistance = Double.POSITIVE_INFINITY;
		if(desktopManager != null && desktopManager.panel != null) {
			panelHit = desktopManager.panel.intersect(pos, look);
			if(panelHit != null) panelDistance = panelHit.position().distanceToSqr(pos);
		}
		
		// Check if game hit result closer
		// Must use trueGameHitResult because the game hit result is overridden by overridePickBlock
		HitResult gameHitResult = trueGameHitResult;
		double gameHitDistance = (gameHitResult == null || gameHitResult.getType() == HitResult.Type.MISS) ? Double.POSITIVE_INFINITY : gameHitResult.getLocation().distanceToSqr(pos);
		double interactiveDistance = Math.min(finalDistance, panelDistance);
		if(gameHitDistance < interactiveDistance) {
			finalHitResult = null;
			panelHit = null;
		}
		
		// Check for player reach
		if(finalHitResult != null && !finalHitResult.position.closerThan(pos, Minecraft.getInstance().player.blockInteractionRange())) finalHitResult = null;
		if(panelHit != null && !panelHit.position().closerThan(pos, Minecraft.getInstance().player.blockInteractionRange())) panelHit = null;
		if(panelHit != null && panelDistance < finalDistance) finalHitResult = null;
		
		if(!pointerGrabs.isExclusiveGrabActive()) hoveredDisplay = finalHitResult;
		if(!pointerGrabs.isGrabActive()) hoveredPanel = panelHit;
		if(finalHitResult != null) {
			activeChromeDisplay = finalHitResult.target;
		}
		else {
			activeChromeDisplay = null;
		}
		
		// Check for pointer grab and short-circuit if any
		if(pointerGrabs.isGrabActive()) {
			this.overridePickBlock = true;
			this.cursorShape = bridge.getCursorShape();
			
			pointerGrabs.moveWorld(pos, look, up);
			if(finalHitResult != null && finalHitResult.surface != null && finalHitResult.surfaceLocalRelative != null) {
				pointerGrabs.hover(finalHitResult.target.window, finalHitResult.surface, finalHitResult.surfaceLocalRelative.x, finalHitResult.surfaceLocalRelative.y);
			}
			else {
				pointerGrabs.hoverNone();
			}
			
			return;
		}
		
		/* All of the following code will only be executed when there aren't any active pointer grabs */
		
		if(hoveredPanel != null) {
			this.overridePickBlock = true;
			this.cursorShape = CursorShape.POINTER;
			bridge.sendMotionOutside();
			return;
		}

		if(hoveredDisplay != null && !canStartInteracting()) hoveredDisplay = null;
		
		if(hoveredDisplay != null) {
			this.overridePickBlock = true;
		}
		
		if(hoveredDisplay != null && hoveredDisplay.dist >= 0) {
			if(hoveredDisplay.control != MonitorControl.NONE) {
				this.cursorShape = hoveredDisplay.control.isButton() ? CursorShape.POINTER : null;
				bridge.sendMotionOutside();
				return;
			}

			WLCSurface surface = hoveredDisplay.surface;
			Vec3 rel = hoveredDisplay.surfaceLocalRelative;
			if(surface == null || rel == null) return;
			
			this.cursorShape = bridge.getCursorShape();
			bridge.sendMotionRefocus(surface, rel.x, rel.y);
			
			if(keyboardCaptureMode != KeyboardCaptureMode.NONE && bridge.maybeLockPointer(surface)) {
				pointerCapture = new PointerCapture(surface, rel.x, rel.y, false);
				if(DEBUG_WINDOWS) {
					LOGGER.info("WLC pointer capture start reason=keyboard-capture-lock surface={} owner={} at={}x{}", surface.getDebugHandle(), describeSurfaceOwner(surface), rel.x, rel.y);
				}
			}
		}
		else {
			bridge.sendMotionOutside();
		}
	}
	
	/* Handle mouse button input
	 * Returns true when the mouse button action has been consumed
	 */
	public boolean onButtonPress(long windowHandle, int button, int action, int modifiers) {
		if(pointerCapture != null) {
			if(DEBUG_WINDOWS && action == 1) {
				LOGGER.info("WLC pointer capture button route button={} surface={} owner={} hovered={}", button, pointerCapture.surface.getDebugHandle(), describeSurfaceOwner(pointerCapture.surface), hoveredDisplay == null ? "none" : describeWindow(hoveredDisplay.target.window));
			}

			if(action == 1 && !pointerCapture.pressedButtons.contains(button)) {
				bridge.sendButton(0x110 + button, 1);
				pointerCapture.pressedButtons.add(button);
			}
			else if(action == 0 && pointerCapture.pressedButtons.contains(button)) {
				bridge.sendButton(0x110 + button, 0);
				pointerCapture.pressedButtons.remove(button);
			}
			else if(action == 0) {
				// Forward release to minecraft if it wasn't part of this pointer capture
				return false;
			}
			return true;
		}
		
		if(action == 0 && pointerGrabs.isGrabActive(button)) {
			pointerGrabs.release(button);
			editedDisplay = null;
			return true;
		}
		
		if(pointerGrabs.isExclusiveGrabActive()) return true;
		
		// Handle implicit pointer grab button presses
		if(action == 1) {
			if(hoveredPanel != null && desktopManager != null) {
				desktopManager.handlePanelButton(hoveredPanel, button);
				return true;
			}

			if(hoveredDisplay != null && hoveredDisplay.control.isButton()) {
				handleMonitorControl(hoveredDisplay, button);
				return true;
			}

			// Start new implicit grab when conditions are met
			if(!pointerGrabs.isImplicitActive() && hoveredDisplay != null && hoveredDisplay.dist >= 0) {
				focusHoveredDisplay("world-button-press", button);
				pointerGrabs.startImplicit(hoveredDisplay);
				WLCAbstractWindow window = hoveredDisplay.target.window;
				bridge.focusSurface(rootToplevel(window));
			}
			
			// If an implicit pointer grab is now active, capture the button press
			if(pointerGrabs.isImplicitActive()) {
				focusHoveredDisplay("world-implicit-button", button);
				pointerGrabs.sendImplicitButton(button);
				return true;
			}
			
			// If clicking on a window at all, the button press should be captured, even if it wasn't passed on to the application
			if(hoveredDisplay != null) return true;
		}
		
		return false;
	}

	private void focusHoveredDisplay(String reason, int button) {
		if(hoveredDisplay == null || hoveredDisplay.dist < 0) return;

		WLCSurface surface = hoveredDisplay.surface;
		Vec3 rel = hoveredDisplay.surfaceLocalRelative;
		WLCAbstractWindow window = hoveredDisplay.target.window;
		if(surface == null || rel == null) return;

		if(DEBUG_WINDOWS) {
			LOGGER.info("WLC world pointer route reason={} button={} window={} surface={} rel={}x{}", reason, button, describeWindow(window), surface.getDebugHandle(), rel.x, rel.y);
		}

		bridge.sendMotionRefocus(surface, rel.x, rel.y, reason);
		bridge.focusSurface(rootToplevel(window));
	}

	private void handleMonitorControl(DisplayHitResult hit, int button) {
		if(!(hit.target.window instanceof WLCToplevel toplevel)) return;
		editedDisplay = hit.target;

		if(DEBUG_WINDOWS) {
			LOGGER.info("WLC monitor control control={} window={}", hit.control, describeWindow(toplevel));
		}

		switch(hit.control) {
		case MOVE:
			pointerGrabs.startExclusive(new MonitorMoveGrab(hit.target, button, hit.surfaceLocalOrigin));
			break;
		case MINIMIZE:
			if(desktopManager != null) desktopManager.markMinimized(toplevel);
			displays.remove(hit.target);
			editedDisplay = null;
			break;
		case RESIZE_MONITOR_TOP_LEFT:
		case RESIZE_MONITOR_TOP_RIGHT:
		case RESIZE_MONITOR_BOTTOM_LEFT:
		case RESIZE_MONITOR_BOTTOM_RIGHT:
			pointerGrabs.startExclusive(new MonitorResizeGrab(hit.target, button, hit.surfaceLocalOrigin, hit.control));
			break;
		case RESIZE_APP_TOP_LEFT:
		case RESIZE_APP_TOP_RIGHT:
		case RESIZE_APP_BOTTOM_LEFT:
		case RESIZE_APP_BOTTOM_RIGHT:
			if(!toplevel.fullscreen) pointerGrabs.startExclusive(new MonitorClientResizeGrab(hit.target, button, hit.surfaceLocalOrigin, hit.control));
			else editedDisplay = null;
			break;
		case CLOSE:
			bridge.closeToplevel(toplevel);
			editedDisplay = null;
			break;
		default:
			editedDisplay = null;
			break;
		}
	}
	
	private boolean canStartInteracting() {
		LocalPlayer player = Minecraft.getInstance().player;
		if(player == null) return false;
		if(player.isUsingItem()) return false;
		return true;
	}
	
	/* Handle mouse being turned in game
	 * Returns true when the mouse move has been consumed
	 */
	public boolean onMouseTurn(double dx, double dy) {
		if(pointerCapture == null) return false;
		if(pointerCapture.hard) return true;
		
		bridge.sendRelativeMotion(dx, dy);
		
		// Workaround for xwayland-satellite issues, usually shouldn't be done
		// as it is technically against protocol and so might cause issues but
		// otherwise relative motion seems to not work.
//		bridge.sendMotion(pointerCapture.x += dx, pointerCapture.y += dy);
		
		return true;
	}

	/* Handle raw cursor motion before Minecraft accumulates it for camera turning. */
	public boolean onRawMouseMove(long windowHandle, double dx, double dy) {
		if(bridge == null) return false;
		if(keyboardCaptureMode != KeyboardCaptureMode.HARD_CAPTURE) return false;
		if(pointerCapture == null) return false;
		if(!pointerCapture.surface.isAlive()) {
			disablePointerCapture("surface-dead");
			return true;
		}
		if(windowHandle != Minecraft.getInstance().getWindow().handle()) return false;

		double maxX = Math.max(1.0, pointerCapture.surface.width());
		double maxY = Math.max(1.0, pointerCapture.surface.height());
		pointerCapture.x = Math.clamp(pointerCapture.x + dx, 0.0, maxX);
		pointerCapture.y = Math.clamp(pointerCapture.y + dy, 0.0, maxY);
		bridge.sendMotion(pointerCapture.x, pointerCapture.y);
		bridge.sendRelativeMotion(dx, dy);
		return true;
	}
	
	/* Handle mouse scroll input
	 * Returns true when the mouse scroll action has been consumed
	 */
	public boolean onScroll(long windowHandle, double scrollX, double scrollY) {
		if(pointerGrabs.isExclusiveGrabActive()) return true;
		
		if(hoveredDisplay != null) {
			if(hoveredDisplay.dist < 0) return true;
			if(hoveredDisplay.control != MonitorControl.NONE) return true;
			
			// Multiplication by -10 is the inverse transformation from what GLFW does on wayland
			focusHoveredDisplay("world-scroll", -1);
			bridge.sendScroll(0, -scrollY * 10);
			bridge.sendScroll(1, -scrollX * 10);
			
			return true;
		}
		
		return false;
	}
	
	/* Handle keyboard input
	 * Returns true when the key press action has been consumed
	 * This code just completely naively assumes that the scancode received by GLFW
	 * is also the correct matching Wayland scancode for the default XKBConfig.
	 * For X11 and Wayland hosts, this is a huge hack but should mostly work for now
	 */
	public boolean onKeyPress(long windowHandle, int key, int scancode, int action, int modifiers) {
		boolean press = action == GLFW.GLFW_PRESS;
		boolean release = action == GLFW.GLFW_RELEASE;
		boolean altKey = isAltKey(key);
		boolean altDown = altKey || (modifiers & GLFW.GLFW_MOD_ALT) != 0;

		if(press && altKey) {
			altChordActive = true;
			altChordClean = true;
			return true;
		}
		if(press && altChordActive && !altKey) {
			altChordClean = false;
		}

		if(altDown && key == GLFW.GLFW_KEY_G) {
			if(press) {
				if(keyboardCaptureMode == KeyboardCaptureMode.NONE) enableKeyboardCapture(false);
				else disableKeyboardCapture("alt-g-toggle");
			}
			return true;
		}

		if(altDown && key == GLFW.GLFW_KEY_R) {
			if(press) toggleRotationMode();
			return true;
		}

		if(altDown && key == GLFW.GLFW_KEY_P) {
			if(press && desktopManager != null) desktopManager.placePanelInFrontOfPlayer();
			return true;
		}

		if(altDown && key == GLFW.GLFW_KEY_Q) {
			if(!press) return true;

			if(keyboardCaptureMode != KeyboardCaptureMode.HARD_CAPTURE) {
				enableKeyboardCapture(true);
			}
			else {
				disableKeyboardCapture("alt-q-toggle");
			}
			return true;
		}

		if(release && altKey) {
			if(altChordActive && altChordClean) {
				snapMonitorPlacement = !snapMonitorPlacement;
				if(DEBUG_WINDOWS) LOGGER.info("WLC monitor snapping {}", snapMonitorPlacement ? "enabled" : "disabled");
			}
			altChordActive = false;
			altChordClean = false;
			return true;
		}

		if(rotationGrab != null) {
			if(press) {
				if(key == GLFW.GLFW_KEY_X) rotationGrab.setAxis(rotationGrab.axis() == Axis.X ? Axis.NONE : Axis.X);
				else if(key == GLFW.GLFW_KEY_Y) rotationGrab.setAxis(rotationGrab.axis() == Axis.Y ? Axis.NONE : Axis.Y);
				else if(key == GLFW.GLFW_KEY_Z) rotationGrab.setAxis(rotationGrab.axis() == Axis.Z ? Axis.NONE : Axis.Z);
			}
			return true;
		}

		if(keyboardCaptureMode == KeyboardCaptureMode.NONE) return false;
		
		if(action == GLFW.GLFW_PRESS) {
			bridge.pressKey(scancode);
		}
		else if(action == GLFW.GLFW_RELEASE) {
			bridge.releaseKey(scancode);
		}
		
		return true;
	}

	private boolean isAltKey(int key) {
		return key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT;
	}

	private void toggleRotationMode() {
		if(rotationGrab != null) {
			pointerGrabs.releaseAll();
			editedDisplay = null;
			rotationGrab = null;
			return;
		}

		WindowDisplay target = null;
		if(hoveredDisplay != null) target = hoveredDisplay.target;
		if(target == null && bridge != null) target = getDisplay(bridge.getMostRecentFocus());
		if(target == null || !target.isValid()) return;

		disableKeyboardCapture("monitor-rotation");
		rotationGrab = new MonitorRotationGrab(target);
		editedDisplay = target;
		pointerGrabs.startExclusive(rotationGrab);
		if(DEBUG_WINDOWS) LOGGER.info("WLC monitor rotation start window={}", describeWindow(target.window));
	}

	private String describeSurfaceOwner(@Nullable WLCSurface surface) {
		if(surface == null || bridge == null) return "none";

		for(WLCToplevel toplevel : bridge.getToplevels()) {
			if(surfaceInTree(toplevel.getSurfaceTree(), surface)) return describeWindow(toplevel);
		}

		for(WLCPopup popup : bridge.getPopups()) {
			if(surfaceInTree(popup.getSurfaceTree(), surface)) return describeWindow(popup);
		}

		return "unknown";
	}

	private boolean surfaceInTree(@Nullable WLCSurface root, WLCSurface target) {
		for(WLCSurface surface = root; surface != null; surface = surface.getNextChild()) {
			if(surface == target) return true;
		}
		return false;
	}

	private String describeWindow(WLCAbstractWindow window) {
		if(window instanceof WLCToplevel toplevel) {
			return "window=" + toplevel.getHandle() + " title=" + toplevel.title + " appID=" + toplevel.appID + " fullscreen=" + toplevel.fullscreen;
		}

		return "window=" + window.getHandle() + " type=" + window.getClass().getSimpleName();
	}

	private @Nullable WLCToplevel rootToplevel(WLCAbstractWindow window) {
		WLCAbstractWindow root = window;
		while(root instanceof WLCPopup popup) {
			root = popup.getParent();
		}
		return root instanceof WLCToplevel toplevel ? toplevel : null;
	}
	
	public static int correctScancode(int scancode) {
		if(GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
			scancode += 8;
		}
		return scancode;
	}
	
	private void anchorToParent(WLCPopup popup) {
		WindowDisplay window = displays.stream().filter((w) -> w.window == popup).findAny().orElse(null);
		WindowDisplay parent = displays.stream().filter((w) -> w.window == popup.getParent()).findAny().orElse(null);
		
		if(window == null || parent == null) return;
		
		// If the parent is also a popup, first make it anchor itself
		if(parent.window instanceof WLCPopup) {
			anchorToParent((WLCPopup) parent.window);
		}
		
		window.rotate(parent.normal(), parent.down());
		
		int x = popup.offsetX - popup.geometry.x() + parent.window.geometry.x();
		int y = popup.offsetY - popup.geometry.y() + parent.window.geometry.y();
		
		window.moveOrigin(parent.localToWorld(x, y, 0.01));
	}
	
	public static enum KeyboardCaptureMode {
		
		NONE, CAPTURE, HARD_CAPTURE;
		
	}
	
	public static class PointerCapture {
		
		public final WLCSurface surface;
		
		// Pointer capture entry surface-local coordinates
		public double x;
		public double y;
		
		public final boolean hard;
		public HashSet<Integer> pressedButtons = new HashSet<Integer>();
		
		public PointerCapture(WLCSurface surface, double x, double y, boolean hard) {
			this.surface = surface;
			this.x = x;
			this.y = y;
			this.hard = hard;
		}
		
	}
	
}

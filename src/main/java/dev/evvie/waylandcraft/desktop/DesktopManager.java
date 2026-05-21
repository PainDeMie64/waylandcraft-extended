package dev.evvie.waylandcraft.desktop;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WindowDisplay;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.bridge.WLCX11Window;
import dev.evvie.waylandcraft.desktop.DesktopLayout.MonitorState;
import dev.evvie.waylandcraft.desktop.DesktopLayout.Vec3State;
import dev.evvie.waylandcraft.grabs.DesktopPanelMoveGrab;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class DesktopManager {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long SAVE_DELAY_NANOS = 1_000_000_000L;

	private final WaylandCraft wlc;
	private DesktopLayout layout = new DesktopLayout();
	private File layoutFile = null;
	private String loadedLayoutKey = "";
	private boolean dirty = false;
	private long lastSaveNanos = 0L;
	private final ArrayList<Long> seenWindows = new ArrayList<Long>();
	public DesktopPanel panel = new DesktopPanel();

	public DesktopManager(WaylandCraft wlc) {
		this.wlc = wlc;
	}

	public int currentWorkspace() {
		return layout.currentWorkspace;
	}

	public void switchWorkspace(int workspace) {
		if(workspace < 1) workspace = 1;
		if(layout.currentWorkspace == workspace) return;
		layout.currentWorkspace = workspace;
		layout.workspace(workspace);
		notify("workspace", "Workspace " + workspace, "Switched workspace");
		markDirty();
	}

	public void update() {
		if(wlc.settingsManager == null || Minecraft.getInstance().level == null) return;
		ensureLoaded();
		ensurePanelPlaced();
		syncWindows();
		restoreDisplays();
		applyWorkspaceVisibility();
		saveIfNeeded();
	}

	public void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx) {
		if(Minecraft.getInstance().level == null) return;
		panel.render(ctx, wlc, this);
	}

	public void placePanelInFrontOfPlayer() {
		if(Minecraft.getInstance().player == null) return;
		panel.anchorToEntity(Minecraft.getInstance().player);
		layout.panel = panel.snapshot();
		notify("panel", "Desktop panel", "Panel placed");
		markDirty();
	}

	public void handlePanelButton(DesktopPanel.PanelHit hit, int mouseButton) {
		DesktopPanel.ButtonRect button = hit.button();
		switch(button.kind) {
		case LAUNCHER:
			Minecraft.getInstance().setScreen(new dev.evvie.waylandcraft.gui.AppLauncherScreen(wlc));
			break;
		case MOVE:
			wlc.pointerGrabs.startExclusive(new DesktopPanelMoveGrab(panel, mouseButton, hit.local()));
			break;
		case WORKSPACE:
			switchWorkspace((int) button.id);
			break;
		case WINDOW:
			WLCToplevel toplevel = toplevelByHandle(button.id);
			if(toplevel == null) return;
			if(wlc.hasDisplayFor(toplevel)) {
				wlc.bridge.focusSurface(toplevel);
				assignWorkspace(toplevel, currentWorkspace());
			}
			else {
				placeOrRestoreDisplay(toplevel);
				assignWorkspace(toplevel, currentWorkspace());
				notify("window", "Placed monitor", windowTitle(toplevel));
			}
			break;
		default:
			break;
		}
		markDirty();
	}

	public void placeOnCurrentWorkspace(WLCToplevel toplevel, WindowDisplay display) {
		assignWorkspace(toplevel, currentWorkspace());
		MonitorState state = layout.monitors.get(stableId(toplevel));
		if(state == null) return;
		state.lifecycle = "placed";
		state.presentationWidth = display.presentationWidth();
		state.presentationHeight = display.presentationHeight();
		state.pivot = fromVec(display.pivot);
		state.normal = fromVec(display.normal());
		state.down = fromVec(display.down());
		markDirty();
	}

	private void placeOrRestoreDisplay(WLCToplevel toplevel) {
		MonitorState state = layout.monitors.get(stableId(toplevel));
		WindowDisplay display = wlc.getOrCreateDisplay(toplevel);
		if(state != null && "placed".equals(state.lifecycle)) {
			applyMonitorState(display, state);
			return;
		}
		if(Minecraft.getInstance().gameRenderer != null) {
			display.anchorToCamera(Minecraft.getInstance().gameRenderer.getMainCamera());
		}
	}

	public List<DesktopNotification> activeNotifications() {
		long now = System.currentTimeMillis();
		ArrayList<DesktopNotification> result = new ArrayList<DesktopNotification>();
		for(DesktopNotification notification : layout.notifications) {
			if(!notification.dismissed && now - notification.createdAt < 8_000L) result.add(notification);
		}
		return result;
	}

	public void markDirty() {
		dirty = true;
	}

	public void markMinimized(WLCToplevel toplevel) {
		MonitorState state = layout.monitors.get(stableId(toplevel));
		if(state == null) return;
		state.lifecycle = "minimized";
		markDirty();
	}

	private void ensureLoaded() {
		String key = layoutKey();
		if(key.equals(loadedLayoutKey)) return;
		loadedLayoutKey = key;
		layoutFile = layoutFileForKey(key);
		layout = readLayout(layoutFile);
		panel.apply(layout.panel);
		seenWindows.clear();
		dirty = false;
	}

	private void ensurePanelPlaced() {
		if(layout.panel.pivot.x != 0.0 || layout.panel.pivot.y != 0.0 || layout.panel.pivot.z != 0.0) return;
		placePanelInFrontOfPlayer();
	}

	private void syncWindows() {
		for(WLCToplevel toplevel : wlc.bridge.getMappedToplevels()) {
			if(!seenWindows.contains(toplevel.getHandle())) {
				seenWindows.add(toplevel.getHandle());
				if(!wlc.hasDisplayFor(toplevel)) {
					notify("window", "New window", windowTitle(toplevel));
				}
				if(WaylandCraft.DEBUG_WINDOWS) {
					WaylandCraft.LOGGER.info("WLC desktop window-seen window={} title={} appID={} stableId={} workspace={} hasDisplay={}",
							toplevel.getHandle(), toplevel.title, toplevel.appID, stableId(toplevel), currentWorkspace(), wlc.hasDisplayFor(toplevel));
				}
			}

			String id = stableId(toplevel);
			MonitorState state = layout.monitors.get(id);
			if(state == null) {
				state = new MonitorState();
				state.stableId = id;
				state.workspace = currentWorkspace();
				state.lifecycle = wlc.hasDisplayFor(toplevel) ? "placed" : "unplaced";
				layout.monitors.put(id, state);
				if(WaylandCraft.DEBUG_WINDOWS) {
					WaylandCraft.LOGGER.info("WLC desktop state-created stableId={} window={} title={} appID={} workspace={} lifecycle={}",
							id, toplevel.getHandle(), toplevel.title, toplevel.appID, state.workspace, state.lifecycle);
				}
			}
			state.appId = safe(toplevel.appID);
			state.title = safe(toplevel.title);

			WindowDisplay display = wlc.getDisplay(toplevel);
			if(display != null) {
				state.lifecycle = "placed";
				state.presentationWidth = display.presentationWidth();
				state.presentationHeight = display.presentationHeight();
				state.pivot = fromVec(display.pivot);
				state.normal = fromVec(display.normal());
				state.down = fromVec(display.down());
			}
		}

		Iterator<Long> iterator = seenWindows.iterator();
		while(iterator.hasNext()) {
			long handle = iterator.next();
			if(toplevelByHandle(handle) == null) {
				iterator.remove();
				notify("window", "Window closed", Long.toString(handle));
			}
		}
		layout.panel = panel.snapshot();
	}

	private void restoreDisplays() {
		for(WLCToplevel toplevel : wlc.bridge.getMappedToplevels()) {
			MonitorState state = layout.monitors.get(stableId(toplevel));
			if(state == null || !"placed".equals(state.lifecycle)) continue;
			WindowDisplay display = wlc.getDisplay(toplevel);
			if(display == null) {
				display = wlc.getOrCreateDisplay(toplevel);
				applyMonitorState(display, state);
				if(WaylandCraft.DEBUG_WINDOWS) {
					WaylandCraft.LOGGER.info("WLC desktop restore-display stableId={} window={} title={} appID={} workspace={} lifecycle={}",
							state.stableId, toplevel.getHandle(), toplevel.title, toplevel.appID, state.workspace, state.lifecycle);
				}
			}
		}
	}

	private void applyMonitorState(WindowDisplay display, MonitorState state) {
		display.pivot = toVec(state.pivot);
		display.rotate(toVec(state.normal), toVec(state.down));
		if(state.presentationWidth > 0 && state.presentationHeight > 0) display.setPresentationSize(state.presentationWidth, state.presentationHeight);
	}

	private void applyWorkspaceVisibility() {
		wlc.displays.removeIf(display -> {
			if(!(display.window instanceof WLCToplevel toplevel)) return false;
			MonitorState state = layout.monitors.get(stableId(toplevel));
			if(state == null) return false;
			if(state.showOnAllWorkspaces) return false;
			if(state.workspace == currentWorkspace()) return false;
			state.lifecycle = "placed";
			if(WaylandCraft.DEBUG_WINDOWS) {
				WaylandCraft.LOGGER.info("WLC desktop hide-display stableId={} window={} title={} appID={} windowWorkspace={} currentWorkspace={}",
						state.stableId, toplevel.getHandle(), toplevel.title, toplevel.appID, state.workspace, currentWorkspace());
			}
			return true;
		});
	}

	private void assignWorkspace(WLCToplevel toplevel, int workspace) {
		MonitorState state = layout.monitors.computeIfAbsent(stableId(toplevel), (id) -> {
			MonitorState created = new MonitorState();
			created.stableId = id;
			return created;
		});
		state.workspace = workspace;
		state.lifecycle = wlc.hasDisplayFor(toplevel) ? "placed" : "unplaced";
		if(WaylandCraft.DEBUG_WINDOWS) {
			WaylandCraft.LOGGER.info("WLC desktop assign-workspace stableId={} window={} title={} appID={} workspace={} lifecycle={}",
					state.stableId, toplevel.getHandle(), toplevel.title, toplevel.appID, workspace, state.lifecycle);
		}
		for(DesktopWorkspace existing : layout.workspaces) {
			existing.windowIds.remove(state.stableId);
		}
		if(!layout.workspace(workspace).windowIds.contains(state.stableId)) layout.workspace(workspace).windowIds.add(state.stableId);
	}

	private void saveIfNeeded() {
		if(!dirty) return;
		if(System.nanoTime() - lastSaveNanos < SAVE_DELAY_NANOS) return;
		if(layoutFile == null) return;
		writeLayout(layoutFile, layout);
		dirty = false;
		lastSaveNanos = System.nanoTime();
	}

	private void notify(String kind, String title, String message) {
		layout.notifications.add(new DesktopNotification(kind, title, message));
		while(layout.notifications.size() > 40) layout.notifications.remove(0);
		markDirty();
	}

	private DesktopLayout readLayout(File file) {
		if(file.exists() && file.isFile()) {
			try(FileReader reader = new FileReader(file)) {
				DesktopLayout read = GSON.fromJson(reader, DesktopLayout.class);
				if(read != null) return read;
			} catch(IOException e) {
				WaylandCraft.LOGGER.warn("Failed to read desktop layout {}", file, e);
			}
		}
		return new DesktopLayout();
	}

	private void writeLayout(File file, DesktopLayout layout) {
		try {
			file.getParentFile().mkdirs();
			try(FileWriter writer = new FileWriter(file)) {
				GSON.toJson(layout, writer);
			}
		} catch(IOException e) {
			WaylandCraft.LOGGER.warn("Failed to write desktop layout {}", file, e);
		}
	}

	private File layoutFileForKey(String key) {
		File layouts = new File(wlc.settingsManager.getSettingsDir(), "layouts");
		return new File(layouts, key + ".json");
	}

	private String layoutKey() {
		Minecraft mc = Minecraft.getInstance();
		String world = mc.getCurrentServer() == null ? "singleplayer" : mc.getCurrentServer().ip;
		String dimension = mc.level == null ? "minecraft:overworld" : mc.level.dimension().toString();
		return encode(world) + "/" + encode(dimension);
	}

	private String encode(String value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private @Nullable WLCToplevel toplevelByHandle(long handle) {
		for(WLCToplevel toplevel : wlc.bridge.getMappedToplevels()) {
			if(toplevel.getHandle() == handle) return toplevel;
		}
		return null;
	}

	private String stableId(WLCToplevel toplevel) {
		if(toplevel instanceof WLCX11Window x11 && x11.x11WindowID != 0) return "x11:" + Long.toHexString(x11.x11WindowID);
		if(toplevel.appID != null && !toplevel.appID.isBlank()) return "app:" + toplevel.appID + ":" + safe(toplevel.title);
		return "title:" + safe(toplevel.title) + ":" + toplevel.getHandle();
	}

	private String windowTitle(WLCToplevel toplevel) {
		if(toplevel.title != null && !toplevel.title.isBlank()) return toplevel.title;
		if(toplevel.appID != null && !toplevel.appID.isBlank()) return toplevel.appID;
		return "window " + toplevel.getHandle();
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private Vec3State fromVec(Vec3 vec) {
		return new Vec3State(vec.x, vec.y, vec.z);
	}

	private Vec3 toVec(Vec3State state) {
		return new Vec3(state.x, state.y, state.z);
	}

}

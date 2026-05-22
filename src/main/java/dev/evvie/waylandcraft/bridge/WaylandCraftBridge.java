package dev.evvie.waylandcraft.bridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeEGL;

import dev.evvie.waylandcraft.CursorShape;
import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow.SurfaceGeometry;
import dev.evvie.waylandcraft.debug.InputTrace;
import dev.evvie.waylandcraft.debug.TextureDebug;
import dev.evvie.waylandcraft.desktop.RawDesktopEntry;
import dev.evvie.waylandcraft.render.BufferTexture;
import dev.evvie.waylandcraft.render.BufferTexture.DmabufTexture;
import dev.evvie.waylandcraft.render.WindowFramebuffer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public class WaylandCraftBridge {

	private long instance;
	private ArrayList<WLCToplevel> toplevels = new ArrayList<WLCToplevel>();
	private ArrayList<WLCX11Window> x11Windows = new ArrayList<WLCX11Window>();
	private ArrayList<WLCPopup> popups = new ArrayList<WLCPopup>();
	private ArrayList<WLCSurface> surfaces = new ArrayList<WLCSurface>();
	private ArrayList<DmabufTexture> dmabufs = new ArrayList<DmabufTexture>();
	private ArrayList<DmabufTexture> retiredDmabufs = new ArrayList<DmabufTexture>();
	private ArrayList<WindowFramebuffer> framebuffers = new ArrayList<WindowFramebuffer>();
	private boolean launchedAppsCleaned = false;

	public IconSurface dndIcon = null;
	public IconSurface cursorIcon = null;
	public int cursorHotspotX = 0;
	public int cursorHotspotY = 0;

	private LinkedList<WLCToplevel> focusOrder = new LinkedList<WLCToplevel>();

	private ArrayList<WLCToplevel> newToplevels = new ArrayList<WLCToplevel>();

	private @Nullable Integer lastMoveRequestSerial = null;
	private @Nullable ResizeRequest lastResizeRequest = null;
	private long debugLastPointerFocusSurface = 0;
	private String debugLastPointerFocusReason = "none";

	static {
		boolean loaded = false;
		InputStream inputStream = WaylandCraftBridge.class.getResourceAsStream("/libwaylandcraft.so");
		if(inputStream != null) {
			try {
				byte[] data = inputStream.readAllBytes();
				inputStream.close();

				File temp = File.createTempFile("waylandcraft-", "-libwaylandcraft.so");
				temp.deleteOnExit();

				FileOutputStream outputStream = new FileOutputStream(temp);
				outputStream.write(data);
				outputStream.close();

				System.load(temp.getAbsolutePath());
				loaded = true;

				WaylandCraft.LOGGER.info("Loaded native library from jar");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if(!loaded) {
			WaylandCraft.LOGGER.info("Native library could not be loaded from jar. Attempting to load from system");
			System.loadLibrary("waylandcraft");
		}
	}

	private WaylandCraftBridge(long handle) {
		this.instance = handle;
	}

	public static WaylandCraftBridge start() {
		long eglDisplay = GLFWNativeEGL.glfwGetEGLDisplay();

		if(eglDisplay == 0) {
			throw new RuntimeException("Failed to get EGL display!");
		}

		long handle = init(GLFW.Functions.GetProcAddress, eglDisplay);
		if(WaylandCraft.INPUT_TRACE) setNativeInputTracePath(InputTrace.nativePath().toString());
		setNativeDebugInput(WaylandCraft.DEBUG_WINDOWS || WaylandCraft.DEBUG_INPUT);
		WaylandCraftBridge bridge = new WaylandCraftBridge(handle);
		Runtime.getRuntime().addShutdownHook(new Thread(
				bridge::cleanupLaunchedApps,
				"WaylandCraft app cleanup"));
		return bridge;
	}

	public synchronized void cleanupLaunchedApps() {
		if(launchedAppsCleaned || instance == 0) return;
		launchedAppsCleaned = true;
		cleanupLaunchedApps(instance);
	}

	protected WLCToplevel getOrCreateToplevel(long handle) {
		for(WLCToplevel toplevel : toplevels) {
			if(toplevel.getHandle() == handle) return toplevel;
		}
		WLCToplevel toplevel = new WLCToplevel(handle);

		long surfaceHandle = toplevelSurface(this.instance, handle);
		WLCSurface surface = getOrCreateSurface(surfaceHandle);
		toplevel.surface = surface;

		toplevels.add(toplevel);
		return toplevel;
	}

	protected WLCX11Window getOrCreateX11Window(long handle) {
		for(WLCX11Window window : x11Windows) {
			if(window.getHandle() == handle) return window;
		}
		WLCX11Window window = new WLCX11Window(handle);

		long surfaceHandle = x11WindowSurface(this.instance, handle);
		window.x11WindowID = x11WindowID(handle);
		window.x11MappedWindowID = x11WindowMappedID(handle);
		if(surfaceHandle != 0) {
			WLCSurface surface = getOrCreateSurface(surfaceHandle);
			window.surface = surface;
			window.debugSurfaceHandle = surface.getDebugHandle();
		}

		x11Windows.add(window);
		toplevels.add(window);
		WaylandCraft.LOGGER.info("WLC X11 window created handle={} xid=0x{} mappedXid=0x{} surface={}",
				handle, Long.toHexString(window.x11WindowID), Long.toHexString(window.x11MappedWindowID), surfaceHandle);
		return window;
	}

	public WLCToplevel[] getNewToplevels() {
		WLCToplevel[] toplevels = newToplevels.toArray(WLCToplevel[]::new);
		newToplevels.clear();

		return toplevels;
	}

	protected WLCPopup getOrCreatePopup(long handle) {
		for(WLCPopup popup : popups) {
			if(popup.getHandle() == handle) return popup;
		}
		WLCPopup popup = new WLCPopup(handle);

		long surfaceHandle = popupSurface(this.instance, handle);
		WLCSurface surface = getOrCreateSurface(surfaceHandle);
		popup.surface = surface;

		popup.parentHandle = popupParent(this.instance, handle);

		popups.add(popup);
		return popup;
	}

	protected WLCSurface getOrCreateSurface(long handle) {
		for(WLCSurface surface : surfaces) {
			if(surface.getHandle() == handle) return surface;
		}
		WLCSurface surface = new WLCSurface(handle);
		surfaces.add(surface);
		return surface;
	}

	protected DmabufTexture getDmabuf(long handle) {
		for(DmabufTexture dmabuf : dmabufs) {
			if(dmabuf.handle == handle) return dmabuf;
		}
		return null;
	}

	protected void addDmabuf(DmabufTexture dmabuf) {
		dmabufs.add(dmabuf);
	}

	public int countBufferReferences(BufferTexture buffer) {
		int refs = 0;
		for(WLCSurface surface : this.surfaces) {
			if(surface.getBuffer() == buffer) refs++;
		}
		return refs;
	}

	public String surfaceHandlesForBuffer(BufferTexture buffer) {
		return this.surfaces.stream()
				.filter((surface) -> surface.getBuffer() == buffer)
				.map((surface) -> Long.toString(surface.getDebugHandle()))
				.collect(Collectors.joining(","));
	}

	private void deleteNonExistingToplevels(long[] remainingHandles) {
		ArrayList<WLCToplevel> toplevels_new = new ArrayList<WLCToplevel>();
		for(WLCToplevel toplevel : this.toplevels) {
			if(toplevel instanceof WLCX11Window) {
				toplevels_new.add(toplevel);
				continue;
			}
			if(ArrayUtils.contains(remainingHandles, toplevel.getHandle())) {
				toplevels_new.add(toplevel);
			}
			else {
				freeToplevel(this.instance, toplevel.takeHandle());
			}
		}
		this.toplevels = toplevels_new;
	}

	private void deleteNonExistingX11Windows(long[] remainingHandles) {
		ArrayList<WLCX11Window> windows_new = new ArrayList<WLCX11Window>();
		for(WLCX11Window window : this.x11Windows) {
			if(ArrayUtils.contains(remainingHandles, window.getHandle())) {
				windows_new.add(window);
			}
			else {
				this.toplevels.remove(window);
				WaylandCraft.LOGGER.info("WLC X11 window removed handle={} title={} appID={}", window.getHandle(), window.title, window.appID);
				window.takeHandle();
			}
		}
		this.x11Windows = windows_new;
	}

	private void deleteNonExistingPopups(long[] remainingHandles) {
		ArrayList<WLCPopup> popups_new = new ArrayList<WLCPopup>();
		for(WLCPopup popup : this.popups) {
			if(ArrayUtils.contains(remainingHandles, popup.getHandle())) {
				popups_new.add(popup);
			}
			else {
				freePopup(this.instance, popup.takeHandle());
			}
		}
		this.popups = popups_new;
	}

	private void deleteNonExistingDmabufs(long[] remainingHandles) {
		ArrayList<DmabufTexture> dmabufs_new = new ArrayList<DmabufTexture>();
		for(DmabufTexture dmabuf : this.dmabufs) {
			if(ArrayUtils.contains(remainingHandles, dmabuf.handle)) {
				TextureDebug.dmabufDecision(dmabuf, true, countBufferReferences(dmabuf), surfaceHandlesForBuffer(dmabuf));
				dmabufs_new.add(dmabuf);
			}
			else {
				int refs = countBufferReferences(dmabuf);
				TextureDebug.dmabufDecision(dmabuf, false, refs, surfaceHandlesForBuffer(dmabuf));
				if(refs > 0) {
					retiredDmabufs.add(dmabuf);
					continue;
				}
				dmabuf.free();
			}
		}
		this.dmabufs = dmabufs_new;
	}

	private void cleanupRetiredDmabufs() {
		ArrayList<DmabufTexture> keep = new ArrayList<DmabufTexture>();
		for(DmabufTexture dmabuf : this.retiredDmabufs) {
			int refs = countBufferReferences(dmabuf);
			TextureDebug.dmabufDecision(dmabuf, false, refs, surfaceHandlesForBuffer(dmabuf));
			if(refs > 0) {
				keep.add(dmabuf);
				continue;
			}
			dmabuf.free();
		}
		this.retiredDmabufs = keep;
	}

	private void deleteUnvisitedSurfaces() {
		ArrayList<WLCSurface> surfaces_new = new ArrayList<WLCSurface>();
		for(WLCSurface surface : this.surfaces) {
			if(surface.visited) {
				surfaces_new.add(surface);
			}
			else {
				freeSurface(this.instance, surface.takeHandle());
			}
		}
		this.surfaces = surfaces_new;
	}

	private void findPopupParent(WLCPopup popup) {
		// Popups cannot change their parent, so if one is found, it's the one
		if(popup.parent != null) return;

		for(WLCToplevel toplevel : toplevels) {
			if(toplevel.getHandle() == popup.parentHandle) {
				popup.parent = toplevel;
				return;
			}
		}

		for(WLCPopup popup2 : popups) {
			if(popup2.getHandle() == popup.parentHandle) {
				popup.parent = popup2;
				return;
			}
		}
	}

	public void update() {
		TextureDebug.nextFrame();
		ProfilerFiller profiler = Profiler.get();
		profiler.push("wayland");

		profiler.push("update");
		// Update wayland clients
		update(this.instance);
		profiler.pop();

		// Find all available toplevels and delete ones that no longer exist
		long[] toplevelHandles = toplevels(instance);
		deleteNonExistingToplevels(toplevelHandles);

		// Find all available X11 windows and delete ones that no longer exist
		long[] x11WindowHandles = x11Windows(instance);
		deleteNonExistingX11Windows(x11WindowHandles);

		// Find all available popups and delete ones that no longer exist
		long[] popupHandles = popups(instance);
		deleteNonExistingPopups(popupHandles);

		long[] minimizeRequests = minimizeReq(instance);
		long[] maximizeRequests = maximizeReq(instance);
		long[] unmaximizeRequests = unmaximizeReq(instance);
		long[] fullscreenRequests = fullscreenReq(instance);
		long[] unfullscreenRequests = unfullscreenReq(instance);
		long[] fullscreened = fullscreened(instance);

		int[] moveRequest = moveRequest(instance);
		if(moveRequest != null) {
			lastMoveRequestSerial = moveRequest[0];
		}

		int[] resizeRequest = resizeRequest(instance);
		if(resizeRequest != null) {
			lastResizeRequest = new ResizeRequest(resizeRequest[0], resizeRequest[1]);
		}

		// Reset surface visited state
		for(WLCSurface surface : surfaces) {
			surface.visited = false;
		}

		profiler.push("surfaces");
		// Create new toplevels when necessary
		// Update surface tree geometry and properties of all toplevels
		for(long handle : toplevelHandles) {
			WLCToplevel toplevel = getOrCreateToplevel(handle);
			WLCSurface root = toplevel.getSurfaceTree();
			toplevel.lastChild = updateSurfaceTree(root);

			updateGeometry(toplevel);
			toplevel.title = toplevelTitle(toplevel.getHandle());
			toplevel.appID = toplevelAppID(toplevel.getHandle());

			if(ArrayUtils.contains(minimizeRequests, handle)) toplevel.requests.minimize = true;
			if(ArrayUtils.contains(maximizeRequests, handle)) toplevel.requests.maximize= true;
			if(ArrayUtils.contains(unmaximizeRequests, handle)) toplevel.requests.unmaximize = true;
			if(ArrayUtils.contains(fullscreenRequests, handle)) toplevel.requests.fullscreen = true;
			if(ArrayUtils.contains(unfullscreenRequests, handle)) toplevel.requests.unfullscreen = true;

			toplevel.fullscreen = ArrayUtils.contains(fullscreened, handle);
		}

		for(long handle : x11WindowHandles) {
			WLCX11Window window = getOrCreateX11Window(handle);
			long surfaceHandle = x11WindowSurface(this.instance, handle);
			if(surfaceHandle == 0) continue;

			WLCSurface surface = getOrCreateSurface(surfaceHandle);
			window.surface = surface;
			if(window.debugSurfaceHandle != surface.getDebugHandle()) {
				WaylandCraft.LOGGER.info("WLC X11 surface changed window={} oldSurface={} newSurface={} title={} appID={}", handle, window.debugSurfaceHandle, surface.getDebugHandle(), window.title, window.appID);
				window.debugSurfaceHandle = surface.getDebugHandle();
			}

			WLCSurface root = window.getSurfaceTree();
			window.lastChild = updateSurfaceTree(root);

			updateGeometry(window);
			window.title = x11WindowTitle(window.getHandle());
			window.appID = x11WindowAppID(window.getHandle());
			long x11WindowID = x11WindowID(window.getHandle());
			long x11MappedWindowID = x11WindowMappedID(window.getHandle());
			if(window.x11WindowID != x11WindowID || window.x11MappedWindowID != x11MappedWindowID) {
				WaylandCraft.LOGGER.info("WLC X11 ids changed handle={} oldXid=0x{} newXid=0x{} oldMappedXid=0x{} newMappedXid=0x{} title={} appID={}",
						handle,
						Long.toHexString(window.x11WindowID),
						Long.toHexString(x11WindowID),
						Long.toHexString(window.x11MappedWindowID),
						Long.toHexString(x11MappedWindowID),
						window.title,
						window.appID);
				window.x11WindowID = x11WindowID;
				window.x11MappedWindowID = x11MappedWindowID;
				window.resetWindowIcon();
			}
			long nowMs = System.currentTimeMillis();
			if(window.shouldFetchWindowIcon(nowMs)) {
				window.updateWindowIcon(x11WindowIcon(instance, window.getHandle()), nowMs);
			}

			if(ArrayUtils.contains(minimizeRequests, handle)) window.requests.minimize = true;
			window.fullscreen = ArrayUtils.contains(fullscreened, handle);
			if(WaylandCraft.DEBUG_WINDOWS && (!window.debugFullscreenSet || window.debugFullscreen != window.fullscreen)) {
				WaylandCraft.LOGGER.info("WLC X11 fullscreen state window={} fullscreen={} title={} appID={} native={} surface={}x{}",
						handle,
						window.fullscreen,
						window.title,
						window.appID,
						window.nativeGeometry == null ? "null" : window.nativeGeometry.width() + "x" + window.nativeGeometry.height() + "+" + window.nativeGeometry.x() + "+" + window.nativeGeometry.y(),
						window.surface == null ? 0 : window.surface.width(),
						window.surface == null ? 0 : window.surface.height());
				window.debugFullscreen = window.fullscreen;
				window.debugFullscreenSet = true;
			}
		}

		// Create new popups when necessary
		// Update surface tree geometry, parent relationships and offsets of all popups
		for(long handle : popupHandles) {
			WLCPopup popup = getOrCreatePopup(handle);
			findPopupParent(popup);

			int[] offset = popupOffset(handle);
			popup.offsetX = offset[0];
			popup.offsetY = offset[1];

			WLCSurface root = popup.getSurfaceTree();
			popup.lastChild = updateSurfaceTree(root);
			updateGeometry(popup);
		}

		long dndIconHandle = dndIcon(instance);
		if(dndIconHandle != 0) {
			WLCSurface dndIconSurface = getOrCreateSurface(dndIconHandle);
			if(dndIcon != null && dndIcon.surface != dndIconSurface) dndIcon = null;
			if(dndIcon == null) dndIcon = new IconSurface(dndIconSurface);

			updateSurfaceData(instance, dndIcon.surface);
			dndIcon.surface.visited = true;
		}
		else {
			dndIcon = null;
		}

		long cursorSurfaceHandle = cursorSurface(instance);
		if(cursorSurfaceHandle != 0) {
			WLCSurface cursorSurface = getOrCreateSurface(cursorSurfaceHandle);
			if(cursorIcon != null && cursorIcon.surface != cursorSurface) cursorIcon = null;
			if(cursorIcon == null) cursorIcon = new IconSurface(cursorSurface);

			cursorHotspotX = cursorHotspotX(instance);
			cursorHotspotY = cursorHotspotY(instance);
			updateSurfaceData(instance, cursorIcon.surface);
			cursorIcon.surface.visited = true;
		}
		else {
			cursorIcon = null;
			cursorHotspotX = 0;
			cursorHotspotY = 0;
		}

		// All surface trees have now been walked. Now delete all unvisited surfaces
		deleteUnvisitedSurfaces();
		profiler.pop();

		// Resolve surface parent handles to actual surfaces
		for(WLCSurface surface : surfaces) {
			if(surface.parentHandle != 0) {
				surface.parent = getOrCreateSurface(surface.parentHandle);
			}
			else {
				surface.parent = null;
			}
		}

		List<WLCAbstractWindow> allWindows = Stream.of(toplevels, popups)
				.flatMap((l) -> l.stream())
				.filter((w) -> w.getSurfaceTree() != null)
				.collect(Collectors.toList());

		// Update all surface buffers
		for(WLCAbstractWindow window : allWindows) {
			WLCSurface root = window.getSurfaceTree();
			for(WLCSurface surface = root; surface != null; surface = surface.getNextChild()) {
				updateSurfaceData(instance, surface);
				calculateSubpos(surface);
			}
			updateGeometry(window);
		}

		for(WLCToplevel toplevel : toplevels) {
			boolean mapped = toplevel.isMapped();
			if(mapped && !toplevel.wasMapped) {
				newToplevels.add(toplevel);
			}
			toplevel.wasMapped = mapped;
		}

		profiler.push("framebuffer");
		updateFramebuffers();
		profiler.pop();

		deleteNonExistingDmabufs(dmabufs(instance));
		cleanupRetiredDmabufs();

		updateFocusOrder();

		// Do client frame callbacks
		for(WLCSurface surface : surfaces) {
			sendFrame(surface.getHandle());
		}

		profiler.pop();
	}

	private void updateFramebuffers() {
		List<WLCAbstractWindow> allWindows = Stream.of(toplevels, popups)
				.flatMap((l) -> l.stream())
				.filter((w) -> w.getSurfaceTree() != null)
				.collect(Collectors.toList());

		// Render windows
		for(WLCAbstractWindow window : allWindows) {
			if(window.framebuffer != null && window.framebuffer.surfaceTree != window.getSurfaceTree()) {
				window.framebuffer.destroy();
				framebuffers.remove(window.framebuffer);
				window.framebuffer = null;
			}

			if(window.framebuffer == null) {
				window.framebuffer = new WindowFramebuffer(window.getSurfaceTree());
				framebuffers.add(window.framebuffer);
			}
			window.framebuffer.render();
		}

		// Render dnd icon
		if(dndIcon != null) {
			if(dndIcon.framebuffer == null) {
				dndIcon.framebuffer = new WindowFramebuffer(dndIcon.surface);
				framebuffers.add(dndIcon.framebuffer);
			}
			dndIcon.framebuffer.render();
		}

		if(cursorIcon != null) {
			if(cursorIcon.framebuffer == null) {
				cursorIcon.framebuffer = new WindowFramebuffer(cursorIcon.surface);
				framebuffers.add(cursorIcon.framebuffer);
			}
			cursorIcon.framebuffer.render();
		}

		// Cleanup unused framebuffers
		ArrayList<WindowFramebuffer> usedFramebuffers = new ArrayList<WindowFramebuffer>();
		for(WLCAbstractWindow window : allWindows) {
			if(window.framebuffer != null && window.framebuffer.surfaceTree.isAlive()) {
				usedFramebuffers.add(window.framebuffer);
			}
		}
		if(dndIcon != null && dndIcon.framebuffer != null && dndIcon.framebuffer.surfaceTree.isAlive()) {
			usedFramebuffers.add(dndIcon.framebuffer);
		}
		if(cursorIcon != null && cursorIcon.framebuffer != null && cursorIcon.framebuffer.surfaceTree.isAlive()) {
			usedFramebuffers.add(cursorIcon.framebuffer);
		}

		for(WindowFramebuffer framebuffer : framebuffers) {
			if(!usedFramebuffers.contains(framebuffer)) {
				framebuffer.destroy();
			}
		}
		framebuffers.retainAll(usedFramebuffers);

		WindowFramebuffer.endFrame();
	}

	private void updateGeometry(WLCAbstractWindow window) {
		int[] data;
		if(window instanceof WLCX11Window) {
			data = x11WindowGeometry(window.getHandle());
		}
		else {
			data = surfaceXDGGeometry(window.surface.getHandle());
		}
		SurfaceGeometry geometry;

		if(data == null) {
			geometry = new SurfaceGeometry(0, 0, window.surface.width(), window.surface.height());
		}
		else {
			if(window instanceof WLCX11Window && window.surface != null && window.surface.width() > 0 && window.surface.height() > 0) {
				WLCX11Window x11 = (WLCX11Window) window;
				x11.nativeGeometry = new SurfaceGeometry(data[0], data[1], data[2], data[3]);
				if(x11.debugNativeX != data[0] || x11.debugNativeY != data[1] || x11.debugNativeWidth != data[2] || x11.debugNativeHeight != data[3] || x11.debugSurfaceWidth != window.surface.width() || x11.debugSurfaceHeight != window.surface.height()) {
					WaylandCraft.LOGGER.info("WLC X11 geometry window={} native={}x{}+{}+{} surface={}x{} title={} appID={}", window.getHandle(), data[2], data[3], data[0], data[1], window.surface.width(), window.surface.height(), x11.title, x11.appID);
					x11.debugNativeX = data[0];
					x11.debugNativeY = data[1];
					x11.debugNativeWidth = data[2];
					x11.debugNativeHeight = data[3];
					x11.debugSurfaceWidth = window.surface.width();
					x11.debugSurfaceHeight = window.surface.height();
				}
				data[0] = 0;
				data[1] = 0;
			}
			geometry = new SurfaceGeometry(data[0], data[1], data[2], data[3]);
		}

		window.geometry = geometry;
	}

	private void calculateSubpos(WLCSurface surface) {
		if(surface.parent != null) {
			calculateSubpos(surface.parent);
			surface.xSubpos = surface.parent.xSubpos + surface.xoff;
			surface.ySubpos = surface.parent.ySubpos + surface.yoff;
		}
		else {
			surface.xSubpos = 0;
			surface.ySubpos = 0;
		}
	}

	public WLCToplevel[] getToplevels() {
		return toplevels.toArray(new WLCToplevel[toplevels.size()]);
	}

	public WLCToplevel[] getMappedToplevels() {
		return toplevels.stream().filter((t) -> t.isMapped()).toArray(WLCToplevel[]::new);
	}

	public WLCToplevel getToplevel(long handle) {
		return toplevels.stream().filter((w) -> w.getHandle() == handle).findAny().orElse(null);
	}

	public WLCPopup[] getPopups() {
		return popups.toArray(new WLCPopup[popups.size()]);
	}

	public WLCPopup[] getMappedPopups() {
		return popups.stream().filter((t) -> t.isMapped()).toArray(WLCPopup[]::new);
	}

	public String getSocket() {
		return socket(this.instance);
	}

	public boolean inputRegionContains(WLCSurface surface, double x, double y) {
		return checkInputRegion(surface.getHandle(), x, y);
	}

	public void sendMotion(double x, double y) {
		withNativeTrace("bridge.pointerMotion", "\"x\":" + x + ",\"y\":" + y, () -> pointerMotion(instance, x, y));
	}

	public void sendMotionRefocus(WLCSurface surface, double x, double y) {
		sendMotionRefocus(surface, x, y, "refocus");
	}

	public void sendMotionRefocus(WLCSurface surface, double x, double y, String reason) {
		debugLastPointerFocusSurface = surface.getDebugHandle();
		debugLastPointerFocusReason = reason;
		if(WaylandCraft.DEBUG_WINDOWS) {
			WaylandCraft.LOGGER.info("WLC pointer native refocus reason={} surface={} x={} y={}", reason, debugLastPointerFocusSurface, x, y);
		}
		withNativeTrace("bridge.pointerMotionFocus", "\"reason\":" + InputTrace.s(reason) + ",\"surface\":" + surface.getHandle() + ",\"surface_debug\":" + surface.getDebugHandle() + ",\"x\":" + x + ",\"y\":" + y, () -> pointerMotionFocus(instance, surface.getHandle(), x, y));
	}

	public void sendRelativeMotion(double dx, double dy) {
		withNativeTrace("bridge.pointerRelMotion", "\"dx\":" + dx + ",\"dy\":" + dy, () -> pointerRelMotion(instance, dx, dy));
	}

	public void sendMotionOutside() {
		debugLastPointerFocusSurface = 0;
		debugLastPointerFocusReason = "outside";
		if(WaylandCraft.DEBUG_WINDOWS) {
			WaylandCraft.LOGGER.info("WLC pointer native leave");
		}
		withNativeTrace("bridge.pointerLeave", "", () -> pointerLeave(instance));
	}

	public boolean maybeLockPointer(WLCSurface surface) {
		return withNativeTraceBool("bridge.maybePointerLock", "\"surface\":" + surface.getHandle() + ",\"surface_debug\":" + surface.getDebugHandle(), () -> maybePointerLock(instance, surface.getHandle()));
	}

	public void unlockPointer() {
		withNativeTrace("bridge.pointerUnlock", "", () -> pointerUnlock(instance));
	}

	public int sendButton(int button, int state) {
		if(WaylandCraft.DEBUG_WINDOWS) {
			WaylandCraft.LOGGER.info("WLC pointer native button button={} state={} focusSurface={} focusReason={}", button, state, debugLastPointerFocusSurface, debugLastPointerFocusReason);
		}
		return withNativeTraceInt("bridge.pointerButton", "\"button\":" + button + ",\"state\":" + state + ",\"focus_surface_debug\":" + debugLastPointerFocusSurface + ",\"focus_reason\":" + InputTrace.s(debugLastPointerFocusReason), () -> pointerButton(instance, button, state));
	}

	public void sendScroll(int axis, double value) {
		withNativeTrace("bridge.pointerAxis", "\"axis\":" + axis + ",\"value\":" + value, () -> pointerAxis(instance, axis, value));
	}

	public CursorShape getCursorShape() {
		return CursorShape.fromId(cursorShape(instance));
	}

	public void focusSurface(@Nullable WLCToplevel toplevel) {
		if(toplevel instanceof WLCX11Window) {
			if(WaylandCraft.DEBUG_INPUT) WaylandCraft.LOGGER.info("WLC input java focus x11 target={}", debugDescribe(toplevel));
			withNativeTrace("bridge.x11WindowFocus", "\"target\":" + InputTrace.s(debugDescribe(toplevel)), () -> x11WindowFocus(instance, toplevel.getHandle()));

			focusOrder.remove(toplevel);
			focusOrder.addLast(toplevel);
			return;
		}

		long handle = 0;
		if(toplevel != null) {
			handle = toplevel.getHandle();
		}
		if(WaylandCraft.DEBUG_INPUT) WaylandCraft.LOGGER.info("WLC input java focus wayland target={}", debugDescribe(toplevel));

		final long focusHandle = handle;
		withNativeTrace("bridge.keyboardFocus", "\"target\":" + InputTrace.s(debugDescribe(toplevel)) + ",\"handle\":" + focusHandle, () -> keyboardFocus(instance, focusHandle));

		// Make toplevel most recently focused
		if(toplevel != null) {
			focusOrder.remove(toplevel);
			focusOrder.addLast(toplevel);
		}
	}

	public void activateKeyboard() {
		if(WaylandCraft.DEBUG_INPUT) WaylandCraft.LOGGER.info("WLC input java keyboard-activate focus={}", debugDescribe(getMostRecentFocus()));
		withNativeTrace("bridge.keyboardActivate", "\"focus\":" + InputTrace.s(debugDescribe(getMostRecentFocus())), () -> keyboardActivate(instance));
	}

	public void deactivateKeyboard() {
		if(WaylandCraft.DEBUG_INPUT) WaylandCraft.LOGGER.info("WLC input java keyboard-deactivate focus={}", debugDescribe(getMostRecentFocus()));
		withNativeTrace("bridge.keyboardDeactivate", "\"focus\":" + InputTrace.s(debugDescribe(getMostRecentFocus())), () -> keyboardDeactivate(instance));
	}

	private void updateFocusOrder() {
		focusOrder.removeIf((t) -> !toplevels.contains(t));
		for(WLCToplevel toplevel : toplevels) {
			if(!focusOrder.contains(toplevel)) focusOrder.addLast(toplevel);
		}
	}

	// Find the most recently focused toplevel that exists
	public WLCToplevel getMostRecentFocus() {
		updateFocusOrder();
		return focusOrder.peekLast();
	}

	// Find the most recently focused toplevel that exists
	public Stream<WLCToplevel> getMostToLeastRecentFocus() {
		updateFocusOrder();
		return focusOrder.reversed().stream();
	}

	public void pressKey(int scancode) {
		if(WaylandCraft.DEBUG_INPUT) WaylandCraft.LOGGER.info("WLC input java bridge-key state=press scancode={} focus={}", scancode, debugDescribe(getMostRecentFocus()));
		withNativeTrace("bridge.keyboardInput", "\"state\":\"press\",\"scancode\":" + scancode + ",\"focus\":" + InputTrace.s(debugDescribe(getMostRecentFocus())), () -> keyboardInput(instance, scancode, 1));
	}

	public void releaseKey(int scancode) {
		if(WaylandCraft.DEBUG_INPUT) WaylandCraft.LOGGER.info("WLC input java bridge-key state=release scancode={} focus={}", scancode, debugDescribe(getMostRecentFocus()));
		withNativeTrace("bridge.keyboardInput", "\"state\":\"release\",\"scancode\":" + scancode + ",\"focus\":" + InputTrace.s(debugDescribe(getMostRecentFocus())), () -> keyboardInput(instance, scancode, 0));
	}

	public void internalKeyUpdate(int scancode, boolean pressed) {
		if(WaylandCraft.DEBUG_INPUT) WaylandCraft.LOGGER.info("WLC input java glfw-key state={} scancode={}", pressed ? "press" : "release", scancode);
		withNativeTrace("bridge.keyboardUpdate", "\"pressed\":" + pressed + ",\"scancode\":" + scancode, () -> keyboardUpdate(instance, scancode, pressed));
	}

	private void withNativeTrace(String eventType, String fields, Runnable action) {
		if(!WaylandCraft.INPUT_TRACE) {
			action.run();
			return;
		}
		InputTrace.event("java-bridge", eventType, fieldsWithFocus(fields));
		setNativeInputTraceCurrent(InputTrace.current());
		try {
			action.run();
		} finally {
			clearNativeInputTraceCurrent();
		}
	}

	private int withNativeTraceInt(String eventType, String fields, IntAction action) {
		if(!WaylandCraft.INPUT_TRACE) return action.run();
		InputTrace.event("java-bridge", eventType, fieldsWithFocus(fields));
		setNativeInputTraceCurrent(InputTrace.current());
		try {
			int result = action.run();
			InputTrace.event("java-bridge", eventType + ".return", fieldsWithFocus(fields + ",\"result\":" + result));
			return result;
		} finally {
			clearNativeInputTraceCurrent();
		}
	}

	private boolean withNativeTraceBool(String eventType, String fields, BoolAction action) {
		if(!WaylandCraft.INPUT_TRACE) return action.run();
		InputTrace.event("java-bridge", eventType, fieldsWithFocus(fields));
		setNativeInputTraceCurrent(InputTrace.current());
		try {
			boolean result = action.run();
			InputTrace.event("java-bridge", eventType + ".return", fieldsWithFocus(fields + ",\"result\":" + result));
			return result;
		} finally {
			clearNativeInputTraceCurrent();
		}
	}

	private String fieldsWithFocus(String fields) {
		String prefix = "\"focus\":" + InputTrace.s(debugDescribe(getMostRecentFocus()));
		if(fields == null || fields.isBlank()) return prefix;
		return prefix + "," + fields;
	}

	@FunctionalInterface
	private interface IntAction {
		int run();
	}

	@FunctionalInterface
	private interface BoolAction {
		boolean run();
	}

	private String debugDescribe(@Nullable WLCToplevel toplevel) {
		if(toplevel == null) return "none";
		return "window=" + toplevel.getHandle() + " title=" + toplevel.title + " appID=" + toplevel.appID + " fullscreen=" + toplevel.fullscreen + " surface=" + (toplevel.getSurfaceTree() == null ? 0 : toplevel.getSurfaceTree().getDebugHandle());
	}

	public void resizeToplevelInteractive(WLCToplevel toplevel, int width, int height) {
		if(toplevel instanceof WLCX11Window) {
			x11WindowResize(toplevel.getHandle(), width, height);
			return;
		}
		toplevelResize(toplevel.getHandle(), width, height, true);
	}

	public void resizeToplevel(WLCToplevel toplevel, int width, int height) {
		if(toplevel instanceof WLCX11Window) {
			x11WindowResize(toplevel.getHandle(), width, height);
			return;
		}
		toplevelResize(toplevel.getHandle(), width, height, false);
	}

	public void resizeToplevelOverride(WLCToplevel toplevel, int width, int height) {
		if(toplevel instanceof WLCX11Window) {
			x11WindowResize(toplevel.getHandle(), width, height);
			return;
		}
		toplevelResizeOvr(toplevel.getHandle(), width, height);
	}

	public void maximizeToplevel(WLCToplevel toplevel) {
		if(toplevel instanceof WLCX11Window) {
			x11WindowMaximize(instance, toplevel.getHandle());
			return;
		}
		toplevelMaximize(instance, toplevel.getHandle());
	}

	public void fullscreenToplevel(WLCToplevel toplevel) {
		if(toplevel instanceof WLCX11Window) {
			x11WindowFullscreen(instance, toplevel.getHandle());
			return;
		}
		toplevelFullscreen(instance, toplevel.getHandle());
	}

	public void closeToplevel(WLCToplevel toplevel) {
		if(toplevel instanceof WLCX11Window) {
			x11WindowClose(toplevel.getHandle());
			return;
		}
		toplevelClose(toplevel.getHandle());
	}

	public Integer checkMoveRequest() {
		if(lastMoveRequestSerial == null) return null;
		int serial = lastMoveRequestSerial.intValue();
		lastMoveRequestSerial = null;
		return serial;
	}

	public ResizeRequest checkResizeRequest() {
		if(lastResizeRequest == null) return null;
		ResizeRequest req = lastResizeRequest;
		lastResizeRequest = null;
		return req;
	}

	public void resizeOutput(int width, int height) {
		outputResize(instance, width, height);
	}

	public void setOutputBounds(int width, int height) {
		outputSetBounds(instance, width, height);
	}

	public Size getOutputSize() {
		int[] size = outputSize(instance);
		return new Size(size[0], size[1]);
	}

	public Size getOutputBounds() {
		int[] size = outputBounds(instance);
		return new Size(size[0], size[1]);
	}

	public RawDesktopEntry loadDesktopEntry(File path) {
		return loadDesktopEntry(instance, path.getAbsolutePath());
	}

	public RawDesktopEntry[] loadSystemDesktopEntries() {
		return loadDesktopEntries(instance);
	}

	public boolean renderSVG(File file, int width, int height, long ptr) {
		return renderSVG(file.getAbsolutePath(), width, height, ptr);
	}

	public boolean execApp(String appId) {
		return execApp(instance, appId);
	}

	public void setKeymapDefault() {
		setKeymapDefault(instance);
	}

	public String exportKeymap() {
		return exportKeymap(instance);
	}

	public boolean setKeymapFromStr(String keymap) {
		return setKeymapFromStr(instance, keymap);
	}

	public Integer checkDndRequest() {
		int[] serial = checkDndRequest(instance);
		if(serial == null) return null;
		return serial[0];
	}

	public void dndCancel() {
		dndCancel(instance);
	}

	public void dndDrop() {
		dndDrop(instance);
	}

	public void sendDndMotion(WLCSurface surface, double x, double y) {
		long handle = surface == null ? 0 : surface.getHandle();
		dndMotion(instance, handle, x, y);
	}

	public static record Size(int width, int height) {}

	public static record ResizeRequest(int serial, int edges) {}

	private static native long init(long glfwGetProcAddress, long eglDisplay);
	private static native void setNativeDebugInput(boolean enabled);
	private static native void setNativeInputTracePath(String path);
	private static native void setNativeInputTraceCurrent(long traceId);
	private static native void clearNativeInputTraceCurrent();
	private static native void cleanupLaunchedApps(long instance);
	private static native void update(long instance);
	private static native String socket(long instance);
	private static native void sendFrame(long handle);

	private static native void updateSurfaceData(long instance, WLCSurface surface);

	private static native long[] toplevels(long instance);
	private static native long toplevelSurface(long instance, long handle);
	private static native String toplevelTitle(long handle);
	private static native String toplevelAppID(long handle);

	private static native long[] x11Windows(long instance);
	private static native long x11WindowSurface(long instance, long handle);
	private static native String x11WindowTitle(long handle);
	private static native String x11WindowAppID(long handle);
	private static native long x11WindowID(long handle);
	private static native long x11WindowMappedID(long handle);
	private static native int[] x11WindowIcon(long instance, long handle);
	private static native int[] x11WindowGeometry(long handle);
	private static native void x11WindowResize(long handle, int width, int height);
	private static native void x11WindowMaximize(long instance, long handle);
	private static native void x11WindowFullscreen(long instance, long handle);
	private static native void x11WindowClose(long handle);
	private static native void x11WindowFocus(long instance, long handle);
	private static native String x11Display(long instance);
	private static native void toplevelClose(long handle);
	// Resize toplevel
	private static native void toplevelResize(long handle, int width, int height, boolean interactive);
	// Resize toplevel override, keep maximized and fullscreen state, stop interactive resize
	private static native void toplevelResizeOvr(long handle, int width, int height);

	// Collect all toplevels that have sent a minimize request and clear the list
	private static native long[] minimizeReq(long instance);
	// Collect all toplevels that have sent a maximize request and clear the list
	private static native long[] maximizeReq(long instance);
	// Collect all toplevels that have sent an unmaximize request and clear the list
	private static native long[] unmaximizeReq(long instance);
	// Collect all toplevels that have sent a fullscreen request and clear the list
	private static native long[] fullscreenReq(long instance);
	// Collect all toplevels that have sent an unfullscreen request and clear the list
	private static native long[] unfullscreenReq(long instance);

	// Collect up to one serial of a sent interactive move request
	private static native int[] moveRequest(long instance);
	// Collect up to one serial of a sent interactive resize request
	private static native int[] resizeRequest(long instance);

	// All toplevels that are currently in fullscreen
	private static native long[] fullscreened(long instance);

	private static native void toplevelMaximize(long instance, long handle);
	private static native void toplevelFullscreen(long instance, long handle);

	private static native long[] popups(long instance);
	private static native long popupSurface(long instance, long handle);
	// Query the parent of a popup
	// Returned handle is a handle either to a toplevel or another popup
	private static native long popupParent(long instance, long handle);
	// Query popup local offset coordinates
	// Returns two-element list containing x,y
	private static native int[] popupOffset(long handle);

	// Query the xdg_surface window geometry of a toplevel or popup.
	// handle should be the handle to the root WLCSurface
	// Returns four-element array containing x,y,width,height which could be null
	private static native int[] surfaceXDGGeometry(long handle);

	private static native long[] dmabufs(long instance);

	// Updates the surface tree given by the root surface
	// This changes the doubly linked list of the WLCSurfaces.
	// The returned surface is the last (most deeply nested) child
	private native WLCSurface updateSurfaceTree(WLCSurface root);

	// Check if point in surface input region
	private static native boolean checkInputRegion(long surfaceHandle, double x, double y);

	// Create pointer motion event
	private static native void pointerMotion(long instance, double x, double y);

	// Create pointer motion event
	private static native void pointerMotionFocus(long instance, long handle, double x, double y);

	// Send relative pointer motion to surface with pointer focus
	private static native void pointerRelMotion(long instance, double dx, double dy);

	private static native boolean maybePointerLock(long instance, long handle);

	private static native void pointerUnlock(long instance);

	// Remove pointer focus from all surfaces
	private static native void pointerLeave(long instance);

	// Create pointer button event. `button` has to be the linux button code, state is 1 for pressed, 0 for released
	private static native int pointerButton(long instance, int button, int state);

	// Create pointer axis event. `axis` is the scroll axis (0 for vertical, 1 for horizontal)
	private static native void pointerAxis(long instance, int axis, double value);

	// Get active cursor image
	private static native int cursorShape(long instance);

	private static native long cursorSurface(long instance);
	private static native int cursorHotspotX(long instance);
	private static native int cursorHotspotY(long instance);

	// Set keyboard focus to a wayland surface. The handle may be 0 to unfocus any surfaces
	private static native void keyboardFocus(long instance, long surfaceHandle);

	private static native void keyboardActivate(long instance);
	private static native void keyboardDeactivate(long instance);

	// Keyboard input. scancode is the raw keycode. action: 0 is released, 1 is pressed.
	private static native void keyboardInput(long instance, int scancode, int action);

	// Update internal key state
	private static native void keyboardUpdate(long instance, int scancode, boolean pressed);

	private static native int[] outputSize(long instance);
	private static native int[] outputBounds(long instance);

	// Update virtual output dimensions
	private static native void outputResize(long instance, int width, int height);

	// Update virtual output maximum window bounds
	private static native void outputSetBounds(long instance, int width, int height);

	private static native void freeSurface(long instance, long handle);
	private static native void freeToplevel(long instance, long handle);
	private static native void freePopup(long instance, long handle);

	private static native RawDesktopEntry loadDesktopEntry(long instance, String path);
	private static native RawDesktopEntry[] loadDesktopEntries(long instance);

	private static native boolean renderSVG(String path, int width, int height, long ptr);

	private static native boolean execApp(long instance, String appId);

	private static native void setKeymapDefault(long instance);
	private static native String exportKeymap(long instance);
	private static native boolean setKeymapFromStr(long instance, String keymap);

	private static native int[] checkDndRequest(long instance);
	private static native boolean checkDndActive(long instance);
	private static native void dndCancel(long instance);
	private static native void dndDrop(long instance);
	private static native void dndMotion(long instance, long surface, double x, double y);
	private static native long dndIcon(long instance);

}

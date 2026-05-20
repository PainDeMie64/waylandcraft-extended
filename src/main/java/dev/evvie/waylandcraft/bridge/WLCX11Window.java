package dev.evvie.waylandcraft.bridge;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.desktop.WindowIcon;
import net.minecraft.resources.Identifier;

public class WLCX11Window extends WLCToplevel {

	private static final long MISSING_ICON_RETRY_MS = 1000L;
	private static final long FOUND_ICON_REFRESH_MS = 5000L;

	public SurfaceGeometry nativeGeometry = null;
	public long x11WindowID = 0;
	public long x11MappedWindowID = 0;
	private WindowIcon windowIcon = null;
	private int windowIconHash = 0;
	private long nextWindowIconFetchMs = 0;
	protected long debugSurfaceHandle = Long.MIN_VALUE;
	protected int debugNativeX = Integer.MIN_VALUE;
	protected int debugNativeY = Integer.MIN_VALUE;
	protected int debugNativeWidth = Integer.MIN_VALUE;
	protected int debugNativeHeight = Integer.MIN_VALUE;
	protected int debugSurfaceWidth = Integer.MIN_VALUE;
	protected int debugSurfaceHeight = Integer.MIN_VALUE;
	protected boolean debugFullscreen = false;
	protected boolean debugFullscreenSet = false;

	public WLCX11Window(long handle) {
		super(handle);
	}

	@Override
	public boolean isMapped() {
		return isAlive() && x11MappedWindowID != 0 && getSurfaceTree() != null && getSurfaceTree().getBuffer() != null;
	}

	@Override
	public @Nullable Identifier getIcon() {
		if(windowIcon == null) return null;
		return windowIcon.getTextureLocation();
	}

	public boolean shouldFetchWindowIcon(long nowMs) {
		return nowMs >= nextWindowIconFetchMs;
	}

	public void updateWindowIcon(@Nullable int[] iconData, long nowMs) {
		if(iconData == null || iconData.length < 3) {
			nextWindowIconFetchMs = nowMs + MISSING_ICON_RETRY_MS;
			return;
		}

		int width = iconData[0];
		int height = iconData[1];
		long pixelCount = (long) width * (long) height;
		if(width <= 0 || height <= 0 || pixelCount > Integer.MAX_VALUE || iconData.length != pixelCount + 2) {
			nextWindowIconFetchMs = nowMs + MISSING_ICON_RETRY_MS;
			return;
		}

		int hash = Arrays.hashCode(iconData);
		if(windowIcon != null && windowIconHash == hash) {
			nextWindowIconFetchMs = nowMs + FOUND_ICON_REFRESH_MS;
			return;
		}

		int[] pixels = Arrays.copyOfRange(iconData, 2, iconData.length);
		String key = Long.toHexString(x11WindowID) + "_" + Integer.toUnsignedString(hash, 16);
		windowIcon = new WindowIcon(key, width, height, pixels);
		windowIconHash = hash;
		nextWindowIconFetchMs = nowMs + FOUND_ICON_REFRESH_MS;
		if(WaylandCraft.DEBUG_WINDOWS) {
			WaylandCraft.LOGGER.info("WLC X11 window icon updated xid=0x{} size={}x{} title={} appID={}", Long.toHexString(x11WindowID), width, height, title, appID);
		}
	}

	public void resetWindowIcon() {
		windowIcon = null;
		windowIconHash = 0;
		nextWindowIconFetchMs = 0;
	}

}

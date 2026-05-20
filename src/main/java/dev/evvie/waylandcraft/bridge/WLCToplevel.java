package dev.evvie.waylandcraft.bridge;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.desktop.DesktopEntry;
import net.minecraft.resources.Identifier;

public class WLCToplevel extends WLCAbstractWindow {
	
	@Nullable
	public String title;
	
	@Nullable
	public String appID;
	
	public ToplevelRequests requests = new ToplevelRequests();
	public boolean fullscreen = false;
	
	@Nullable
	public SurfaceGeometry restoreGeometry = null;
	
	public WLCToplevel(long handle) {
		super(handle);
	}

	public @Nullable Identifier getIcon() {
		DesktopEntry entry = WaylandCraft.instance.xdgManager.exactEntryForAppId(appID);
		if(entry == null) return null;
		return entry.getIcon();
	}
	
	public static class ToplevelRequests {
		
		public boolean minimize = false;
		public boolean maximize = false;
		public boolean unmaximize = false;
		public boolean fullscreen = false;
		public boolean unfullscreen = false;
		
	}
	
}

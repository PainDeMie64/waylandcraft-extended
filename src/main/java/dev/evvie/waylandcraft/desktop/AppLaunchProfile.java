package dev.evvie.waylandcraft.desktop;

import java.util.HashMap;
import java.util.Map;

public class AppLaunchProfile {

	public String appId = "";
	public String displayNameOverride = "";
	public boolean pinnedByDefault = false;
	public boolean autoStart = false;
	public int preferredMonitorWidth = 0;
	public int preferredMonitorHeight = 0;
	public int preferredWorkspace = 1;
	public Map<String, String> environment = new HashMap<String, String>();

}

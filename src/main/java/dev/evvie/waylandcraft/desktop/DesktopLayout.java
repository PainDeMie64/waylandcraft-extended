package dev.evvie.waylandcraft.desktop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DesktopLayout {

	public int currentWorkspace = 1;
	public DesktopPanel.PanelState panel = DesktopPanel.PanelState.defaultState();
	public ArrayList<DesktopWorkspace> workspaces = new ArrayList<DesktopWorkspace>();
	public ArrayList<PinnedApp> pinnedApps = new ArrayList<PinnedApp>();
	public ArrayList<DesktopNotification> notifications = new ArrayList<DesktopNotification>();
	public Map<String, MonitorState> monitors = new HashMap<String, MonitorState>();

	public DesktopLayout() {
		for(int i = 1; i <= 4; i++) {
			workspaces.add(new DesktopWorkspace(i));
		}
	}

	public DesktopWorkspace workspace(int id) {
		for(DesktopWorkspace workspace : workspaces) {
			if(workspace.id == id) return workspace;
		}
		DesktopWorkspace workspace = new DesktopWorkspace(id);
		workspaces.add(workspace);
		return workspace;
	}

	public static class MonitorState {
		public String stableId = "";
		public String appId = "";
		public String title = "";
		public String lifecycle = "placed";
		public int workspace = 1;
		public boolean showOnAllWorkspaces = false;
		public int presentationWidth = 0;
		public int presentationHeight = 0;
		public Vec3State pivot = new Vec3State();
		public Vec3State normal = new Vec3State(0, 0, 1);
		public Vec3State down = new Vec3State(0, -1, 0);
	}

	public static class Vec3State {
		public double x = 0.0;
		public double y = 0.0;
		public double z = 0.0;

		public Vec3State() {
		}

		public Vec3State(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

}

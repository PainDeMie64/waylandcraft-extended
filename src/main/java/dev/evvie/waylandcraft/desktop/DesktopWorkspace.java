package dev.evvie.waylandcraft.desktop;

import java.util.ArrayList;

public class DesktopWorkspace {

	public int id = 1;
	public String name = "1";
	public ArrayList<String> windowIds = new ArrayList<String>();

	public DesktopWorkspace() {
	}

	public DesktopWorkspace(int id) {
		this.id = id;
		this.name = Integer.toString(id);
	}

}

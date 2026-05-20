package dev.evvie.waylandcraft.desktop;

public class DesktopNotification {

	public String kind = "info";
	public String title = "";
	public String message = "";
	public long createdAt = System.currentTimeMillis();
	public boolean dismissed = false;

	public DesktopNotification() {
	}

	public DesktopNotification(String kind, String title, String message) {
		this.kind = kind;
		this.title = title;
		this.message = message;
	}

}

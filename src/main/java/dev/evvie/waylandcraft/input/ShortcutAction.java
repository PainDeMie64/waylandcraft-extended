package dev.evvie.waylandcraft.input;

import org.lwjgl.glfw.GLFW;

public enum ShortcutAction {
	WINDOW_MANAGER("windowManager", "Window Manager", GLFW.GLFW_KEY_B, 0),
	APP_LAUNCHER("appLauncher", "App Launcher", GLFW.GLFW_KEY_V, 0),
	DESKTOP_PANEL("desktopPanel", "Place Desktop Panel", GLFW.GLFW_KEY_P, GLFW.GLFW_MOD_ALT),
	KEYBOARD_CAPTURE("keyboardCapture", "Toggle Keyboard Capture", GLFW.GLFW_KEY_G, GLFW.GLFW_MOD_ALT),
	HARD_CAPTURE("hardCapture", "Toggle Mouse and Keyboard Capture", GLFW.GLFW_KEY_Q, GLFW.GLFW_MOD_ALT),
	MONITOR_ROTATION("monitorRotation", "Toggle Monitor Rotation", GLFW.GLFW_KEY_R, GLFW.GLFW_MOD_ALT),
	MONITOR_SNAPPING("monitorSnapping", "Toggle Monitor Snapping", GLFW.GLFW_KEY_LEFT_ALT, 0),
	ROTATION_AXIS_X("rotationAxisX", "Rotation Axis X", GLFW.GLFW_KEY_X, 0),
	ROTATION_AXIS_Y("rotationAxisY", "Rotation Axis Y", GLFW.GLFW_KEY_Y, 0),
	ROTATION_AXIS_Z("rotationAxisZ", "Rotation Axis Z", GLFW.GLFW_KEY_Z, 0);

	public final String id;
	public final String title;
	public final int defaultKey;
	public final int defaultModifiers;

	ShortcutAction(String id, String title, int defaultKey, int defaultModifiers) {
		this.id = id;
		this.title = title;
		this.defaultKey = defaultKey;
		this.defaultModifiers = defaultModifiers;
	}
}

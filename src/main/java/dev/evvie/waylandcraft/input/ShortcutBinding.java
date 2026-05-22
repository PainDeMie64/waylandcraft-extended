package dev.evvie.waylandcraft.input;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.input.KeyEvent;

public record ShortcutBinding(int key, int modifiers) {
	public static final ShortcutBinding UNBOUND = new ShortcutBinding(GLFW.GLFW_KEY_UNKNOWN, 0);

	public boolean isUnbound() {
		return key == GLFW.GLFW_KEY_UNKNOWN;
	}

	public boolean matches(KeyEvent event) {
		if(isUnbound() || event.key() != key) return false;
		if(isModifierKey(key)) return true;
		return normalizedModifiers(event.modifiers()) == modifiers;
	}

	public boolean isModifierOnly() {
		return isModifierKey(key) && modifiers == 0;
	}

	public String label() {
		if(isUnbound()) return "Unbound";
		String keyName = keyName(key);
		if(modifiers == 0) return keyName;
		return modifiersLabel(modifiers) + "+" + keyName;
	}

	public String serialize() {
		return key + "," + modifiers;
	}

	public static ShortcutBinding parse(String value, ShortcutBinding fallback) {
		if(value == null || value.isBlank()) return fallback;
		String[] parts = value.split(",", 2);
		if(parts.length != 2) return fallback;
		try {
			return new ShortcutBinding(Integer.parseInt(parts[0]), normalizedModifiers(Integer.parseInt(parts[1])));
		} catch(NumberFormatException ignored) {
			return fallback;
		}
	}

	public static int modifierForKey(int key) {
		return switch(key) {
			case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> GLFW.GLFW_MOD_SHIFT;
			case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> GLFW.GLFW_MOD_CONTROL;
			case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> GLFW.GLFW_MOD_ALT;
			case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> GLFW.GLFW_MOD_SUPER;
			default -> 0;
		};
	}

	public static boolean isModifierKey(int key) {
		return modifierForKey(key) != 0;
	}

	public static int normalizedModifiers(int modifiers) {
		return modifiers & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER);
	}

	public static String modifiersLabel(int modifiers) {
		StringBuilder label = new StringBuilder();
		appendModifier(label, modifiers, GLFW.GLFW_MOD_CONTROL, "Ctrl");
		appendModifier(label, modifiers, GLFW.GLFW_MOD_ALT, "Alt");
		appendModifier(label, modifiers, GLFW.GLFW_MOD_SHIFT, "Shift");
		appendModifier(label, modifiers, GLFW.GLFW_MOD_SUPER, "Super");
		return label.toString();
	}

	private static void appendModifier(StringBuilder label, int modifiers, int flag, String name) {
		if((modifiers & flag) == 0) return;
		if(!label.isEmpty()) label.append("+");
		label.append(name);
	}

	private static String keyName(int key) {
		return switch(key) {
			case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "Alt";
			case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
			case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "Shift";
			case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> "Super";
			default -> InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
		};
	}
}

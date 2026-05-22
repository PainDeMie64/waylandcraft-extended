package dev.evvie.waylandcraft.input;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Properties;

import org.lwjgl.glfw.GLFW;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.client.input.KeyEvent;

public class ShortcutManager {
	private final EnumMap<ShortcutAction, ShortcutBinding> bindings = new EnumMap<>(ShortcutAction.class);
	private final EnumSet<ShortcutAction> consumedReleases = EnumSet.noneOf(ShortcutAction.class);
	private File file;
	private ShortcutAction modifierOnlyAction = null;
	private boolean modifierOnlyContaminated = false;

	public ShortcutManager(File gameDirectory) {
		resetToDefaults();
		File dir = new File(gameDirectory, "waylandcraft");
		if(!dir.exists() && !dir.mkdirs()) {
			WaylandCraft.LOGGER.warn("Failed to create WaylandCraft settings directory {}", dir);
		}
		file = new File(dir, "shortcuts.properties");
		load();
	}

	public ShortcutBinding binding(ShortcutAction action) {
		return bindings.getOrDefault(action, defaultBinding(action));
	}

	public String label(ShortcutAction action) {
		return binding(action).label();
	}

	public void set(ShortcutAction action, ShortcutBinding binding) {
		bindings.put(action, binding);
		save();
	}

	public void reset(ShortcutAction action) {
		bindings.put(action, defaultBinding(action));
		save();
	}

	public void resetToDefaults() {
		for(ShortcutAction action : ShortcutAction.values()) {
			bindings.put(action, defaultBinding(action));
		}
	}

	public void resetAllAndSave() {
		resetToDefaults();
		save();
	}

	public ShortcutResult handle(KeyEvent event, int action) {
		if(action == GLFW.GLFW_PRESS && modifierOnlyAction != null && !ShortcutBinding.isModifierKey(event.key())) {
			modifierOnlyContaminated = true;
		}

		for(ShortcutAction shortcut : ShortcutAction.values()) {
			if(shortcut == ShortcutAction.ROTATION_AXIS_X || shortcut == ShortcutAction.ROTATION_AXIS_Y || shortcut == ShortcutAction.ROTATION_AXIS_Z) continue;
			ShortcutBinding binding = binding(shortcut);
			if(binding.isUnbound()) continue;

			if(action == GLFW.GLFW_RELEASE && consumedReleases.remove(shortcut) && event.key() == binding.key()) {
				return ShortcutResult.consumeOnly();
			}

			if(!binding.matches(event)) continue;

			if(binding.isModifierOnly()) {
				return handleModifierOnly(shortcut, event, action);
			}

			if(action == GLFW.GLFW_PRESS) {
				consumedReleases.add(shortcut);
				return ShortcutResult.trigger(shortcut);
			}
			if(action == GLFW.GLFW_REPEAT && consumedReleases.contains(shortcut)) {
				return ShortcutResult.consumeOnly();
			}
		}

		return ShortcutResult.pass();
	}

	private ShortcutResult handleModifierOnly(ShortcutAction shortcut, KeyEvent event, int action) {
		if(action == GLFW.GLFW_PRESS) {
			modifierOnlyAction = shortcut;
			modifierOnlyContaminated = false;
			return ShortcutResult.consumeOnly();
		}
		if(action == GLFW.GLFW_REPEAT) return ShortcutResult.consumeOnly();
		if(action == GLFW.GLFW_RELEASE && modifierOnlyAction == shortcut) {
			boolean trigger = !modifierOnlyContaminated;
			modifierOnlyAction = null;
			modifierOnlyContaminated = false;
			return trigger ? ShortcutResult.trigger(shortcut) : ShortcutResult.consumeOnly();
		}
		return ShortcutResult.pass();
	}

	private ShortcutBinding defaultBinding(ShortcutAction action) {
		return new ShortcutBinding(action.defaultKey, action.defaultModifiers);
	}

	private void load() {
		if(file == null || !file.isFile()) return;
		Properties properties = new Properties();
		try(FileInputStream stream = new FileInputStream(file)) {
			properties.load(stream);
			for(ShortcutAction action : ShortcutAction.values()) {
				ShortcutBinding fallback = defaultBinding(action);
				bindings.put(action, ShortcutBinding.parse(properties.getProperty(action.id), fallback));
			}
		} catch(IOException err) {
			WaylandCraft.LOGGER.warn("Failed to load WaylandCraft shortcuts", err);
		}
	}

	private void save() {
		if(file == null) return;
		Properties properties = new Properties();
		for(ShortcutAction action : ShortcutAction.values()) {
			properties.setProperty(action.id, binding(action).serialize());
		}
		try(FileOutputStream stream = new FileOutputStream(file)) {
			properties.store(stream, "WaylandCraft Extended shortcuts");
		} catch(IOException err) {
			WaylandCraft.LOGGER.warn("Failed to save WaylandCraft shortcuts", err);
		}
	}

	public record ShortcutResult(boolean consume, ShortcutAction action) {
		public static ShortcutResult pass() {
			return new ShortcutResult(false, null);
		}

		public static ShortcutResult consumeOnly() {
			return new ShortcutResult(true, null);
		}

		public static ShortcutResult trigger(ShortcutAction action) {
			return new ShortcutResult(true, action);
		}
	}
}

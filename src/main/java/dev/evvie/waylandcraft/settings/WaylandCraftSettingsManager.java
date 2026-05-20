package dev.evvie.waylandcraft.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.client.Minecraft;

public class WaylandCraftSettingsManager {
	
	private final WaylandCraft wlc;
	
	private File settingsDir;
	private File keymapFile;
	
	public WaylandCraftSettingsManager(WaylandCraft wlc) {
		this.wlc = wlc;
		
		try {
			init();
		} catch(IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to read settings storage!");
		}
	}
	
	private void init() throws IOException {
		settingsDir = new File(Minecraft.getInstance().gameDirectory, "waylandcraft");
		if(!settingsDir.exists()) {
			settingsDir.mkdir();
		}
		else if(!settingsDir.isDirectory()) {
			throw new IOException("Waylandcraft settings directory exists but is not a directory");
		}
		
		keymapFile = new File(settingsDir, "keymap.txt");
		
		String keymap = tryReadKeymapFromFile();
		if(keymap == null) {
			keymap = tryReadKeymapFromSystem();
		}
		
		if(keymap != null) {
			if(!wlc.bridge.setKeymapFromStr(keymap)) {
				WaylandCraft.LOGGER.error("Failed to load keymap!");
			}
		}
	}
	
	private String tryReadKeymapFromSystem() {
		// Try running xkbcli to get keymap
		String keymap = null;
		try {
			Process process = new ProcessBuilder("xkbcli", "dump-keymap").start();
			byte[] data = process.getInputStream().readAllBytes();
			keymap = new String(data);
			
			int exitCode = process.waitFor();
			if(exitCode != 0) {
				keymap = null;
				WaylandCraft.LOGGER.error("xkbcli exited with error " + exitCode);
			}
		} catch (IOException | InterruptedException e) {
			WaylandCraft.LOGGER.error("xkbcli invoke failed!", e);
		}
		if(keymap == null) {
			WaylandCraft.LOGGER.error("Failed to dump keymap using xkbcli");
		}
		return keymap;
	}
	
	private String tryReadKeymapFromFile() {
		if(!(keymapFile.exists() && keymapFile.isFile())) return null;
		
		try {
			FileInputStream stream = new FileInputStream(keymapFile);
			byte[] data = stream.readAllBytes();
			String keymap = new String(data);
			stream.close();
			return keymap;
		} catch(IOException e) {
			WaylandCraft.LOGGER.info("Error reading keymap file!", e);
			return null;
		}
	}

	public File getSettingsDir() {
		return settingsDir;
	}
	
}

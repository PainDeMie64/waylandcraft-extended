package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.desktop.DesktopPanel;
import net.minecraft.world.phys.Vec3;

public class DesktopPanelMoveGrab extends PointerGrab {

	private final DesktopPanel panel;
	private final Vec3 grabbedLocal;

	public DesktopPanelMoveGrab(DesktopPanel panel, int button, Vec3 grabbedLocal) {
		super(button);
		this.panel = panel;
		this.grabbedLocal = grabbedLocal;
	}

	@Override
	public void init() throws GrabDroppedException {
	}

	@Override
	public void release(boolean force) throws GrabDroppedException {
		if(wlc.desktopManager != null) wlc.desktopManager.markDirty();
	}

	@Override
	public void moveWorld(Vec3 pos, Vec3 view, Vec3 up) throws GrabDroppedException {
		panel.moveWithGrab(pos, view, up, grabbedLocal);
		if(wlc.desktopManager != null) wlc.desktopManager.markDirty();
	}

}

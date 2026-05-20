package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.WindowDisplay;
import net.minecraft.world.phys.Vec3;

public class MonitorMoveGrab extends PointerGrab {

	private final WindowDisplay window;
	private final Vec3 grabbedLocal;

	public MonitorMoveGrab(WindowDisplay window, int button, Vec3 grabbedLocal) {
		super(button);
		this.window = window;
		this.grabbedLocal = grabbedLocal;
	}

	private void checkValid() throws GrabDroppedException {
		if(!window.isValid()) this.drop();
	}

	@Override
	public void init() throws GrabDroppedException {
		checkValid();
	}

	@Override
	public void release(boolean force) throws GrabDroppedException {
		checkValid();
	}

	@Override
	public void moveWorld(Vec3 pos, Vec3 view, Vec3 up) throws GrabDroppedException {
		checkValid();

		window.rotate(view.reverse(), up.reverse());
		Vec3 target = pos.add(view.scale(2));
		double centerX = window.presentationWidth() / 2.0;
		double centerY = window.presentationHeight() / 2.0;
		window.pivot = target
				.add(window.localX().scale(centerX - grabbedLocal.x))
				.add(window.localY().scale(centerY - grabbedLocal.y));
		wlc.snapDisplayPlacement(window);
		wlc.snapDisplayOrientation(window);
		if(wlc.desktopManager != null) wlc.desktopManager.markDirty();
	}

}

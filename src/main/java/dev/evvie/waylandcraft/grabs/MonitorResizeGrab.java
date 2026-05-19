package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.CursorShape;
import dev.evvie.waylandcraft.WindowDisplay;
import dev.evvie.waylandcraft.WindowDisplay.PlaneHit;
import net.minecraft.world.phys.Vec3;

public class MonitorResizeGrab extends PointerGrab {

	private final WindowDisplay window;
	private final Vec3 startLocal;
	private final int startWidth;
	private final int startHeight;
	private int width;
	private int height;

	public MonitorResizeGrab(WindowDisplay window, int button, Vec3 startLocal) {
		super(button);
		this.window = window;
		this.startLocal = startLocal;
		this.startWidth = window.presentationWidth();
		this.startHeight = window.presentationHeight();
		this.width = startWidth;
		this.height = startHeight;
	}

	@Override
	public void init() throws GrabDroppedException {
	}

	@Override
	public void release(boolean force) throws GrabDroppedException {
	}

	@Override
	public void moveWorld(Vec3 pos, Vec3 view, Vec3 up) throws GrabDroppedException {
		if(!window.isValid()) this.drop();

		wlc.cursorShape = CursorShape.SE_RESIZE;
		PlaneHit hit = window.intersectPlane(pos, view);
		if(hit == null) return;

		Vec3 local = window.worldToLocal(hit.position());
		int newWidth = Math.clamp(startWidth + (int) Math.round(local.x - startLocal.x), 1, 10000);
		int newHeight = Math.clamp(startHeight + (int) Math.round(local.y - startLocal.y), 1, 10000);
		if(newWidth == width && newHeight == height) return;

		width = newWidth;
		height = newHeight;
		window.setPresentationSize(width, height);
	}

}

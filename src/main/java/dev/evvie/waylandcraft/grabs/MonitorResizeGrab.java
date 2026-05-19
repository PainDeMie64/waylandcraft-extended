package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.CursorShape;
import dev.evvie.waylandcraft.WindowDisplay;
import dev.evvie.waylandcraft.WindowDisplay.PlaneHit;
import net.minecraft.world.phys.Vec3;

public class MonitorResizeGrab extends PointerGrab {

	private final WindowDisplay window;
	private final Vec3 cornerOffset;
	private final Vec3 startOrigin;
	private final int startWidth;
	private final int startHeight;
	private int width;
	private int height;

	public MonitorResizeGrab(WindowDisplay window, int button, Vec3 startLocal) {
		super(button);
		this.window = window;
		this.startWidth = window.presentationWidth();
		this.startHeight = window.presentationHeight();
		this.cornerOffset = new Vec3(startWidth - startLocal.x, startHeight - startLocal.y, 0);
		this.startOrigin = window.origin();
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
		window.moveOrigin(startOrigin);
		PlaneHit hit = window.intersectPlane(pos, view);
		if(hit == null) return;

		Vec3 local = window.worldToLocal(hit.position()).add(cornerOffset);
		int newWidth = Math.clamp((int) Math.round(local.x), 1, 10000);
		int newHeight = Math.clamp((int) Math.round(local.y), 1, 10000);
		if(newWidth == width && newHeight == height) return;

		width = newWidth;
		height = newHeight;
		window.setPresentationSize(width, height);
		window.moveOrigin(startOrigin);
	}

}

package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.CursorShape;
import dev.evvie.waylandcraft.WindowDisplay;
import dev.evvie.waylandcraft.WindowDisplay.MonitorControl;
import dev.evvie.waylandcraft.WindowDisplay.PlaneHit;
import net.minecraft.world.phys.Vec3;

public class MonitorResizeGrab extends PointerGrab {

	private final WindowDisplay window;
	private final Vec3 cornerOffset;
	private final Vec3 startOrigin;
	private final MonitorControl corner;
	private final int startWidth;
	private final int startHeight;
	private int width;
	private int height;

	public MonitorResizeGrab(WindowDisplay window, int button, Vec3 startLocal, MonitorControl corner) {
		super(button);
		this.window = window;
		this.corner = corner;
		this.startWidth = window.presentationWidth();
		this.startHeight = window.presentationHeight();
		this.cornerOffset = new Vec3(corner.isLeftCorner() ? startLocal.x : startWidth - startLocal.x, corner.isTopCorner() ? startLocal.y : startHeight - startLocal.y, 0);
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
		PlaneHit hit = window.intersectPlane(pos, view);
		if(hit == null) return;

		Vec3 local = window.worldToLocalFromOrigin(hit.position(), startOrigin);
		int newWidth = Math.clamp((int) Math.round(corner.isLeftCorner() ? startWidth - local.x + cornerOffset.x : local.x + cornerOffset.x), 1, 10000);
		int newHeight = Math.clamp((int) Math.round(corner.isTopCorner() ? startHeight - local.y + cornerOffset.y : local.y + cornerOffset.y), 1, 10000);
		if(newWidth == width && newHeight == height) return;

		width = newWidth;
		height = newHeight;
		Vec3 newOrigin = startOrigin;
		if(corner.isLeftCorner()) newOrigin = newOrigin.add(window.localX().scale(startWidth - width));
		if(corner.isTopCorner()) newOrigin = newOrigin.add(window.localY().scale(startHeight - height));
		window.setPresentationSize(width, height);
		window.moveOrigin(newOrigin);
	}

}

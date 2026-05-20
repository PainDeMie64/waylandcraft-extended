package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.CursorShape;
import dev.evvie.waylandcraft.WindowDisplay;
import dev.evvie.waylandcraft.WindowDisplay.MonitorControl;
import dev.evvie.waylandcraft.WindowDisplay.PlaneHit;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge.Size;
import net.minecraft.world.phys.Vec3;

public class MonitorClientResizeGrab extends PointerGrab {

	private final WindowDisplay window;
	private final WLCToplevel toplevel;
	private final Vec3 cornerOffset;
	private final Vec3 startOrigin;
	private final MonitorControl corner;
	private final int startWidth;
	private final int startHeight;
	private final int startPresentationWidth;
	private final int startPresentationHeight;
	private int width;
	private int height;

	public MonitorClientResizeGrab(WindowDisplay window, int button, Vec3 startLocal, MonitorControl corner) {
		super(button);
		this.window = window;
		this.toplevel = (WLCToplevel) window.window;
		this.corner = corner;
		this.startWidth = window.window.geometry.width();
		this.startHeight = window.window.geometry.height();
		this.startPresentationWidth = window.presentationWidth();
		this.startPresentationHeight = window.presentationHeight();
		this.cornerOffset = new Vec3(corner.isLeftCorner() ? startLocal.x : startPresentationWidth - startLocal.x, corner.isTopCorner() ? startLocal.y : startPresentationHeight - startLocal.y, 0);
		this.startOrigin = window.origin();
		this.width = startWidth;
		this.height = startHeight;
	}

	@Override
	public void init() throws GrabDroppedException {
		if(toplevel.fullscreen) this.drop();
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
		Size bounds = wlc.bridge.getOutputBounds();
		int maxWidth = Math.max(1, bounds.width());
		int maxHeight = Math.max(1, bounds.height());
		double presentationWidth = Math.max(1.0, corner.isLeftCorner() ? startPresentationWidth - local.x + cornerOffset.x : local.x + cornerOffset.x);
		double presentationHeight = Math.max(1.0, corner.isTopCorner() ? startPresentationHeight - local.y + cornerOffset.y : local.y + cornerOffset.y);
		int newWidth = Math.clamp((int) Math.round(startWidth * presentationWidth / Math.max(1, startPresentationWidth)), 1, maxWidth);
		int newHeight = Math.clamp((int) Math.round(startHeight * presentationHeight / Math.max(1, startPresentationHeight)), 1, maxHeight);
		if(newWidth == width && newHeight == height) return;

		width = newWidth;
		height = newHeight;
		wlc.bridge.resizeToplevelInteractive(toplevel, width, height);
		Vec3 newOrigin = startOrigin;
		if(corner.isLeftCorner()) newOrigin = newOrigin.add(window.localX().scale(startPresentationWidth - presentationWidth));
		if(corner.isTopCorner()) newOrigin = newOrigin.add(window.localY().scale(startPresentationHeight - presentationHeight));
		window.setPresentationSize((int) Math.round(presentationWidth), (int) Math.round(presentationHeight));
		window.moveOrigin(newOrigin);
		if(wlc.desktopManager != null) wlc.desktopManager.markDirty();
	}

}

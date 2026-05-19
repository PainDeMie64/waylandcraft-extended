package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.CursorShape;
import dev.evvie.waylandcraft.WindowDisplay;
import dev.evvie.waylandcraft.WindowDisplay.PlaneHit;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge.Size;
import net.minecraft.world.phys.Vec3;

public class MonitorClientResizeGrab extends PointerGrab {

	private final WindowDisplay window;
	private final WLCToplevel toplevel;
	private final Vec3 cornerOffset;
	private final Vec3 startOrigin;
	private final int startWidth;
	private final int startHeight;
	private final int startPresentationWidth;
	private final int startPresentationHeight;
	private int width;
	private int height;

	public MonitorClientResizeGrab(WindowDisplay window, int button, Vec3 startLocal) {
		super(button);
		this.window = window;
		this.toplevel = (WLCToplevel) window.window;
		this.startWidth = window.window.geometry.width();
		this.startHeight = window.window.geometry.height();
		this.startPresentationWidth = window.presentationWidth();
		this.startPresentationHeight = window.presentationHeight();
		this.cornerOffset = new Vec3(startPresentationWidth - startLocal.x, startPresentationHeight - startLocal.y, 0);
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
		window.moveOrigin(startOrigin);
		PlaneHit hit = window.intersectPlane(pos, view);
		if(hit == null) return;

		Vec3 local = window.worldToLocal(hit.position()).add(cornerOffset);
		Size bounds = wlc.bridge.getOutputBounds();
		int maxWidth = Math.max(1, bounds.width());
		int maxHeight = Math.max(1, bounds.height());
		double presentationWidth = Math.max(1.0, local.x);
		double presentationHeight = Math.max(1.0, local.y);
		int newWidth = Math.clamp((int) Math.round(startWidth * presentationWidth / Math.max(1, startPresentationWidth)), 1, maxWidth);
		int newHeight = Math.clamp((int) Math.round(startHeight * presentationHeight / Math.max(1, startPresentationHeight)), 1, maxHeight);
		if(newWidth == width && newHeight == height) return;

		width = newWidth;
		height = newHeight;
		wlc.bridge.resizeToplevelInteractive(toplevel, width, height);
		window.moveOrigin(startOrigin);
	}

}

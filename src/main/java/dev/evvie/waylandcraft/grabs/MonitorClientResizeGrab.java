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
	private final Vec3 startLocal;
	private final int startWidth;
	private final int startHeight;
	private int width;
	private int height;

	public MonitorClientResizeGrab(WindowDisplay window, int button, Vec3 startLocal) {
		super(button);
		this.window = window;
		this.toplevel = (WLCToplevel) window.window;
		this.startLocal = startLocal;
		this.startWidth = window.window.geometry.width();
		this.startHeight = window.window.geometry.height();
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

		Vec3 local = window.worldToLocal(hit.position());
		Size bounds = wlc.bridge.getOutputBounds();
		int maxWidth = Math.max(1, bounds.width());
		int maxHeight = Math.max(1, bounds.height());
		int newWidth = Math.clamp(startWidth + (int) Math.round(local.x - startLocal.x), 1, maxWidth);
		int newHeight = Math.clamp(startHeight + (int) Math.round(local.y - startLocal.y), 1, maxHeight);
		if(newWidth == width && newHeight == height) return;

		width = newWidth;
		height = newHeight;
		wlc.bridge.resizeToplevelInteractive(toplevel, width, height);
	}

}

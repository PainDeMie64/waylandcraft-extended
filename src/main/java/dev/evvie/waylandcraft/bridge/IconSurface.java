package dev.evvie.waylandcraft.bridge;

import dev.evvie.waylandcraft.render.WindowFramebuffer;

public class IconSurface {
	
	public final WLCSurface surface;
	public WindowFramebuffer framebuffer = null;
	
	public IconSurface(WLCSurface surface) {
		this.surface = surface;
	}
	
	public void render() {
		if(framebuffer != null) framebuffer.free();
		framebuffer = WindowFramebuffer.renderSurfaceTree(surface);
	}
	
}

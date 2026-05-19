package dev.evvie.waylandcraft.bridge;

public class WLCX11Window extends WLCToplevel {

	public WLCX11Window(long handle) {
		super(handle);
	}

	@Override
	public boolean isMapped() {
		return isAlive() && getSurfaceTree() != null && getSurfaceTree().getBuffer() != null;
	}

}

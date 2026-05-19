package dev.evvie.waylandcraft.bridge;

public class WLCX11Window extends WLCToplevel {

	public SurfaceGeometry nativeGeometry = null;
	protected long debugSurfaceHandle = Long.MIN_VALUE;
	protected int debugNativeX = Integer.MIN_VALUE;
	protected int debugNativeY = Integer.MIN_VALUE;
	protected int debugNativeWidth = Integer.MIN_VALUE;
	protected int debugNativeHeight = Integer.MIN_VALUE;
	protected int debugSurfaceWidth = Integer.MIN_VALUE;
	protected int debugSurfaceHeight = Integer.MIN_VALUE;

	public WLCX11Window(long handle) {
		super(handle);
	}

	@Override
	public boolean isMapped() {
		return isAlive() && getSurfaceTree() != null && getSurfaceTree().getBuffer() != null;
	}

}

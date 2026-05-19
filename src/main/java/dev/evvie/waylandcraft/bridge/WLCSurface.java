package dev.evvie.waylandcraft.bridge;

import org.jetbrains.annotations.Nullable;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.render.BufferTexture;
import dev.evvie.waylandcraft.render.BufferTexture.DmabufTexture;
import dev.evvie.waylandcraft.render.BufferTexture.ShmBufferTexture;
import dev.evvie.waylandcraft.render.BufferTexture.SinglePixelBufferTexture;

public class WLCSurface {
	
	// Set to zero when this surface no longer exists
	private long handle;
	
	// Used by native code to tag used surfaces
	protected boolean visited;
	
	@Nullable
	private BufferTexture buffer = null;
	
	// Either a child of this surface or one of its siblings
	@Nullable
	protected WLCSurface nextChild = null;
	
	@Nullable
	protected WLCSurface prevChild = null;
	
	protected long parentHandle = 0;
	
	@Nullable
	protected WLCSurface parent = null;
	
	// Surface size. By default the size of the attached buffer.
	private int width = 0;
	private int height = 0;
	
	@Nullable
	private ViewportSource sourceView = null;
	private boolean debugViewportSet = false;
	private double debugViewportX = Double.NaN;
	private double debugViewportY = Double.NaN;
	private double debugViewportWidth = Double.NaN;
	private double debugViewportHeight = Double.NaN;
	private int debugWidth = Integer.MIN_VALUE;
	private int debugHeight = Integer.MIN_VALUE;
	
	// X and Y offsets relative to parent coords
	protected int xoff = 0;
	protected int yoff = 0;
	
	// Total calculated offsets
	public int xSubpos = 0;
	public int ySubpos = 0;
	
	protected WLCSurface(long handle) {
		this.handle = handle;
	}
	
	protected long getHandle() {
		return this.handle;
	}
	
	public long getDebugHandle() {
		return this.handle;
	}

	protected long takeHandle() {
		long old = this.handle;
		this.handle = 0;
		return old;
	}
	
	public boolean isAlive() {
		return handle != 0;
	}

	protected boolean referencesDmabuf(long handle) {
		return this.buffer instanceof DmabufTexture && ((DmabufTexture) this.buffer).handle == handle;
	}
	
	// Attach a shared memory buffer
	// The surface width and height are reset to the given buffer dimensions.
	protected void attachShmBuffer(long ptr, int width, int height, int format, int stride) {
		if(this.buffer != null) {
			this.buffer.release();
		}
		this.buffer = new ShmBufferTexture(ptr, width, height, format, stride);
		this.width = width;
		this.height = height;
	}
	
	// Attach a single pixel buffer
	// The surface width and height are reset to 1.
	protected void attachSinglePixelBuffer(byte r, byte g, byte b, byte a) {
		if(this.buffer != null) {
			this.buffer.release();
		}
		this.buffer = new SinglePixelBufferTexture(r, g, b, a);
		this.width = 1;
		this.height = 1;
	}
	
	// Attach an already known dmabuf
	// The surface width and height are reset to the given buffer dimensions.
	// Returns false if no DmabufTexture by that handle was found.
	protected boolean attachDmabuf(long handle) {
		if(this.buffer != null) {
			this.buffer.release();
		}
		
		this.buffer = WaylandCraft.instance.bridge.getDmabuf(handle);
		if(this.buffer != null) {
			this.width = buffer.width;
			this.height = buffer.height;
		}
		return this.buffer != null;
	}
	
	// Create and attach a new DmabufTexture
	// MUST only be used when attachDmabuf returns false for this handle!
	protected void attachNewDmabuf(long handle, long eglImage, int width, int height) {
		DmabufTexture dmabuf = new DmabufTexture(handle, eglImage, width, height);
		WaylandCraft.instance.bridge.addDmabuf(dmabuf);
		
		if(!attachDmabuf(handle)) {
			throw new RuntimeException("Failed to attach newly created dmabuf");
		}
	}
	
	protected void removeBuffer() {
		this.buffer = null;
		this.width = this.height = 0;
	}
	
	// Set viewport source dimensions
	// Crops the surface to the specified rectangle.
	protected void setViewportSrc(double x, double y, double width, double height) {
		this.sourceView = new ViewportSource(x, y, width, height);
		this.width = (int) width;
		this.height = (int) height;
	}

	protected void clearViewportSrc() {
		this.sourceView = null;
		if(this.buffer != null) {
			this.width = this.buffer.width;
			this.height = this.buffer.height;
		}
	}
	
	// Set viewport destination dimensions
	// Overrides this surfaces width & height values.
	protected void setViewportDst(int width, int height) {
		this.width = width;
		this.height = height;
	}

	protected void logStateIfChanged() {
		boolean viewportSet = this.sourceView != null;
		double viewportX = viewportSet ? this.sourceView.x : Double.NaN;
		double viewportY = viewportSet ? this.sourceView.y : Double.NaN;
		double viewportWidth = viewportSet ? this.sourceView.width : Double.NaN;
		double viewportHeight = viewportSet ? this.sourceView.height : Double.NaN;
		if(this.debugWidth == this.width && this.debugHeight == this.height && this.debugViewportSet == viewportSet && Double.compare(this.debugViewportX, viewportX) == 0 && Double.compare(this.debugViewportY, viewportY) == 0 && Double.compare(this.debugViewportWidth, viewportWidth) == 0 && Double.compare(this.debugViewportHeight, viewportHeight) == 0) {
			return;
		}

		this.debugWidth = this.width;
		this.debugHeight = this.height;
		this.debugViewportSet = viewportSet;
		this.debugViewportX = viewportX;
		this.debugViewportY = viewportY;
		this.debugViewportWidth = viewportWidth;
		this.debugViewportHeight = viewportHeight;
		if(viewportSet) {
			WaylandCraft.LOGGER.info("WLC surface state handle={} size={}x{} buffer={}x{} viewport={}x{}+{}+{}", this.handle, this.width, this.height, this.buffer == null ? 0 : this.buffer.width, this.buffer == null ? 0 : this.buffer.height, viewportWidth, viewportHeight, viewportX, viewportY);
		}
		else {
			WaylandCraft.LOGGER.info("WLC surface state handle={} size={}x{} buffer={}x{} viewport=none", this.handle, this.width, this.height, this.buffer == null ? 0 : this.buffer.width, this.buffer == null ? 0 : this.buffer.height);
		}
	}
	
	public int width() {
		return width;
	}
	
	public int height() {
		return height;
	}
	
	public ViewportSource getViewportSource() {
		return sourceView;
	}
	
	@Nullable
	public BufferTexture getBuffer() {
		return this.buffer;
	}
	
	@Nullable
	public WLCSurface getParent() {
		return this.parent;
	}
	
	@Nullable
	public WLCSurface getNextChild() {
		return this.nextChild;
	}
	
	@Nullable
	public WLCSurface getPrevChild() {
		return this.prevChild;
	}
	
	// Surface-local dimensions of the source rectangle in a buffer
	public static final class ViewportSource {
		
		public final double x;
		public final double y;
		public final double width;
		public final double height;
		
		public ViewportSource(double x, double y, double width, double height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
		
	}
	
}

package dev.evvie.waylandcraft;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.bridge.WLCAbstractWindow.SurfaceGeometry;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.bridge.WLCX11Window;
import dev.evvie.waylandcraft.bridge.WaylandCraftBridge.Size;
import dev.evvie.waylandcraft.render.RenderUtils;
import dev.evvie.waylandcraft.render.RenderUtils.FitRect;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class WindowDisplay {
	
	private static final float PIXEL_SCALE = 1.0f / 500;
	private static final double OVERLAY_PADDING = 14;
	private static final double OUTLINE_THICKNESS = 4;
	private static final double CONTROL_SIZE = 72;
	private static final double CONTROL_GAP = 12;
	private static final double CORNER_HANDLE = 54;
	private static final double CORNER_INSET = 10;
	private static final double CORNER_OUTSET = 22;
	private static final double CHROME_DEPTH = 0.004;
	private static final double CONTROL_BG_DEPTH = 0.006;
	private static final double CONTROL_FG_DEPTH = 0.010;
	private static final int OUTLINE_COLOR = 0xffffffff;
	private static final int CONTROL_BG = 0xff15181d;
	private static final int CONTROL_ICON = 0xffffffff;
	private static final int CONTROL_DISABLED = 0xff7c838c;
	private static final int CONTROL_MONITOR_RESIZE = 0xff4bc17d;
	private static final int CONTROL_APP_RESIZE = 0xff5da9ff;
	private static final int CONTROL_CLOSE = 0xffff5d5d;
	
	public final WLCAbstractWindow window;
	
	// World position of window
	public Vec3 pivot = new Vec3(0, 0, 0);
	
	// Window facing direction normal
	private Vec3 normal = new Vec3(0, 0, 1);
	
	// Window orientation downwards vector, has to be orthogonal to `normal` and normalized
	private Vec3 down = new Vec3(0, -1, 0);
	
	private int width;
	private int height;
	private boolean customPresentationSize = false;
	private int debugFramebufferWidth = Integer.MIN_VALUE;
	private int debugFramebufferHeight = Integer.MIN_VALUE;
	private double debugDrawX = Double.NaN;
	private double debugDrawY = Double.NaN;
	private double debugDrawWidth = Double.NaN;
	private double debugDrawHeight = Double.NaN;
	private double debugDrawScale = Double.NaN;
	private long debugLastPointerLogNanos = 0L;
	
	public WindowDisplay(WLCAbstractWindow window) {
		this.window = window;
		this.updateGeometry();
	}
	
	public boolean isValid() {
		return window.isAlive() && window.framebuffer != null && window.framebuffer.isValid();
	}
	
	public void rotate(Vec3 normal, Vec3 down) {
		this.normal = normal;
		this.down = down;
	}
	
	public Vec3 normal() {
		return normal;
	}
	
	public Vec3 down() {
		return down;
	}
	
	public Vec3 right() {
		return normal.cross(down);
	}
	
	public Vec3 localX() {
		return right().scale(PIXEL_SCALE);
	}
	
	public Vec3 localY() {
		return down.scale(PIXEL_SCALE);
	}
	
	// World coordinates of the origin of the root surface surface-local coordinate space
	public Vec3 origin() {
		return pivot.add(localX().scale(-width/2)).add(localY().scale(-height/2));
	}
	
	public Vec3 localToWorld(double x, double y, double z) {
		Vec3 origin = origin();
		Vec3 localX = localX();
		Vec3 localY = localY();
		return origin.add(localX.scale(x)).add(localY.scale(y)).add(normal.scale(z));
	}
	
	public void moveOrigin(Vec3 pos) {
		pivot = pos.add(localX().scale(width/2)).add(localY().scale(height/2));
	}

	public boolean containsDisplayLocal(double x, double y) {
		return x >= 0 && y >= 0 && x <= width && y <= height;
	}
	
	public void updateGeometry() {
		if(!customPresentationSize || width <= 0 || height <= 0) {
			width = window.geometry.width();
			height = window.geometry.height();
		}
	}

	public int presentationWidth() {
		return width;
	}

	public int presentationHeight() {
		return height;
	}

	public void setPresentationSize(int width, int height) {
		this.width = Math.max(width, 1);
		this.height = Math.max(height, 1);
		this.customPresentationSize = true;
	}

	public void clearPresentationSize() {
		this.customPresentationSize = false;
		updateGeometry();
	}
	
	public void render(LevelRenderContext ctx) {
		if(window.framebuffer == null) return;
		updateGeometry();
		
		FitRect fit = presentationFit();
		if(fit.scale() <= 0.0) return;
		logRenderFitIfChanged(fit);

		Vec3 localX = localX();
		Vec3 localY = localY();

		Vec3 cameraPos = ctx.levelState().cameraRenderState.pos;
		Vec3 originRel = origin().subtract(cameraPos);

		Vec3 tl = localX.scale(fit.x()).add(localY.scale(fit.y()));
		Vec3 bl = localX.scale(fit.x()).add(localY.scale(fit.bottom()));
		Vec3 br = localX.scale(fit.right()).add(localY.scale(fit.bottom()));
		Vec3 tr = localX.scale(fit.right()).add(localY.scale(fit.y()));
		
		PoseStack poseStack = ctx.poseStack();
		poseStack.pushPose();
		poseStack.translate(originRel.x, originRel.y, originRel.z);
		RenderUtils.renderFramebuffer(window.framebuffer, poseStack, ctx.submitNodeCollector(), true, tl, bl, br, tr, "world-display window=" + window.getHandle());
		renderMonitorChrome(ctx, poseStack, localX, localY);
		poseStack.popPose();
	}

	private void renderMonitorChrome(LevelRenderContext ctx, PoseStack poseStack, Vec3 localX, Vec3 localY) {
		boolean highlighted = WaylandCraft.instance != null && WaylandCraft.instance.isDisplayHighlighted(this);
		double x = -OVERLAY_PADDING;
		double y = -OVERLAY_PADDING;
		double w = width + OVERLAY_PADDING * 2;
		double h = height + OVERLAY_PADDING * 2;

		if(!highlighted) return;

		Vec3 chromeDepth = normal.scale(CHROME_DEPTH);
		Vec3 controlBgDepth = normal.scale(CONTROL_BG_DEPTH);
		Vec3 controlFgDepth = normal.scale(CONTROL_FG_DEPTH);

		RenderUtils.renderSolidOutline(poseStack, ctx.submitNodeCollector(), localX, localY, x, y, w, h, chromeDepth, OUTLINE_THICKNESS, OUTLINE_COLOR);
		if(window instanceof WLCToplevel) {
			for(MonitorControl control : MonitorControl.values()) {
				if(!control.isButton()) continue;
				ControlRect rect = controlRect(control);
				boolean enabled = isControlEnabled(control);
				boolean hovered = WaylandCraft.instance != null && WaylandCraft.instance.isMonitorControlHovered(this, control);
				if(control.isCornerResize()) {
					renderCornerHandle(poseStack, ctx, localX, localY, controlFgDepth, rect, control, enabled && hovered);
				}
				else {
					int iconColor = enabled ? CONTROL_ICON : CONTROL_DISABLED;
					RenderUtils.renderSolidRect(poseStack, ctx.submitNodeCollector(), localX, localY, rect.x(), rect.y(), rect.width(), rect.height(), controlBgDepth, CONTROL_BG);
					RenderUtils.renderSolidOutline(poseStack, ctx.submitNodeCollector(), localX, localY, rect.x(), rect.y(), rect.width(), rect.height(), controlFgDepth, OUTLINE_THICKNESS, OUTLINE_COLOR);
					int accent = control == MonitorControl.CLOSE ? CONTROL_CLOSE : OUTLINE_COLOR;
					renderControlIcon(poseStack, ctx, localX, localY, controlFgDepth, rect, control, iconColor, accent);
				}
			}
		}
	}

	private void renderCornerHandle(PoseStack poseStack, LevelRenderContext ctx, Vec3 localX, Vec3 localY, Vec3 depth, ControlRect rect, MonitorControl control, boolean hovered) {
		int color = hovered ? (control.isMonitorResize() ? CONTROL_MONITOR_RESIZE : CONTROL_APP_RESIZE) : CONTROL_DISABLED;
		double x1 = control.isLeftCorner() ? rect.x() : rect.x() + rect.width();
		double y1 = control.isTopCorner() ? rect.y() : rect.y() + rect.height();
		double x2 = control.isLeftCorner() ? rect.x() + rect.width() : rect.x();
		double y2 = control.isTopCorner() ? rect.y() + rect.height() : rect.y();
		RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x1, y1, x2, y1, depth, OUTLINE_THICKNESS + 1, color);
		RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x1, y1, x1, y2, depth, OUTLINE_THICKNESS + 1, color);
	}

	private void renderControlIcon(PoseStack poseStack, LevelRenderContext ctx, Vec3 localX, Vec3 localY, Vec3 depth, ControlRect rect, MonitorControl control, int iconColor, int accentColor) {
		double x = rect.x();
		double y = rect.y();
		double s = rect.width();
		double t = 5;
		switch(control) {
		case MOVE:
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.50, y + s * 0.22, x + s * 0.50, y + s * 0.78, depth, t, iconColor);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.22, y + s * 0.50, x + s * 0.78, y + s * 0.50, depth, t, iconColor);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.50, y + s * 0.22, x + s * 0.38, y + s * 0.34, depth, t, iconColor);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.50, y + s * 0.22, x + s * 0.62, y + s * 0.34, depth, t, iconColor);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.50, y + s * 0.78, x + s * 0.38, y + s * 0.66, depth, t, iconColor);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.50, y + s * 0.78, x + s * 0.62, y + s * 0.66, depth, t, iconColor);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.22, y + s * 0.50, x + s * 0.34, y + s * 0.38, depth, t, iconColor);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.22, y + s * 0.50, x + s * 0.34, y + s * 0.62, depth, t, iconColor);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.78, y + s * 0.50, x + s * 0.66, y + s * 0.38, depth, t, iconColor);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.78, y + s * 0.50, x + s * 0.66, y + s * 0.62, depth, t, iconColor);
			break;
		case CLOSE:
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.28, y + s * 0.28, x + s * 0.72, y + s * 0.72, depth, t + 1, accentColor);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.72, y + s * 0.28, x + s * 0.28, y + s * 0.72, depth, t + 1, accentColor);
			break;
		default:
			break;
		}
	}

	private boolean isControlEnabled(MonitorControl control) {
		return !control.isAppResize() || isClientResizeAllowed();
	}

	private boolean isClientResizeAllowed() {
		if(!(window instanceof WLCToplevel toplevel)) return false;
		if(toplevel.fullscreen) return false;
		if(window instanceof WLCX11Window x11 && x11.nativeGeometry != null && WaylandCraft.instance != null && WaylandCraft.instance.bridge != null) {
			Size bounds = WaylandCraft.instance.bridge.getOutputBounds();
			SurfaceGeometry geometry = x11.nativeGeometry;
			if(geometry.x() == 0 && geometry.y() == 0 && geometry.width() >= bounds.width() && geometry.height() >= bounds.height()) {
				return false;
			}
		}
		return true;
	}

	private FitRect presentationFit() {
		if(window.framebuffer == null) {
			return RenderUtils.aspectFit(width, height, 0, 0, width, height);
		}
		return RenderUtils.aspectFit(window.framebuffer, 0, 0, width, height);
	}

	private @Nullable Vec3 displayLocalToRootLocal(Vec3 displayLocal) {
		if(window.framebuffer == null) return displayLocal;

		FitRect fit = presentationFit();
		if(!fit.contains(displayLocal.x, displayLocal.y)) {
			return null;
		}

		double rootX = fit.sourceX(displayLocal.x) - window.framebuffer.getXOff();
		double rootY = fit.sourceY(displayLocal.y) - window.framebuffer.getYOff();
		return new Vec3(rootX, rootY, displayLocal.z);
	}
	
	/* Transform absolute world coordinates to surface-local pixel coordinates relative to toplevel (0, 0)
	 * 
	 * The resulting vector is the (x, y) pixel location and the z value is the block distance normal to the plane.
	 */
	public Vec3 worldToLocal(Vec3 in) {
		return worldToLocalFromOrigin(in, origin());
	}

	public Vec3 worldToLocalFromOrigin(Vec3 in, Vec3 origin) {
		Vec3 localX = localX();
		Vec3 localY = localY();
		
		// World coordinates relative to the origin of this window
		Vec3 world = in.subtract(origin);
		
		Matrix3d matrix = new Matrix3d(
			localX.x, localX.y, localX.z, // Column 0
			localY.x, localY.y, localY.z, // Column 1
			normal.x, normal.y, normal.z  // Column 2
		);
		matrix.invert();
		
		Vector3d result = matrix.transform(new Vector3d(world.x, world.y, world.z));
		return new Vec3(result.x, result.y, result.z);
	}
	
	/* Perform ray-window plane intersection
	 * `dir` must be normalized.
	 */
	public DisplayHitResult intersect(Vec3 pos, Vec3 dir) {
		PlaneHit planeHit = intersectPlane(pos, dir);
		if(planeHit == null) return null;

		double t = planeHit.dist();
		Vec3 hitPos = planeHit.position();
		Vec3 displayLocalCoords = worldToLocal(hitPos);
		MonitorControl control = controlAt(displayLocalCoords);
		if(control != MonitorControl.NONE) {
			return new DisplayHitResult(this, null, control, hitPos, displayLocalCoords, null, null, t);
		}

		Vec3 inputLocalCoords = displayLocalToRootLocal(displayLocalCoords);
		if(inputLocalCoords == null) {
			return new DisplayHitResult(this, null, MonitorControl.NONE, hitPos, displayLocalCoords, null, null, t);
		}
		
		WLCSurface hitSurface = null;
		Vec3 localCoordsRelative = null;
		
		for(WLCSurface surface = window.getSurfaceTreeLast(); surface != null; surface = surface.getPrevChild()) {
			Vec3 rel = inputLocalCoords.subtract(surface.xSubpos, surface.ySubpos, 0);
			
			int width = surface.width();
			int height = surface.height();
			
			if(rel.x < 0 || rel.y < 0 || rel.x > width || rel.y > height) {
				continue;
			}
			
			if(WaylandCraft.instance.bridge.inputRegionContains(surface, rel.x, rel.y)) {
				hitSurface = surface;
				localCoordsRelative = rel;
				break;
			}
		}
		
		// Flip z-coordinate when on the window backside
		double dist = t;
		if(dir.dot(normal) > 0) dist *= -1;
		
		DisplayHitResult result = new DisplayHitResult(this, hitSurface, MonitorControl.NONE, hitPos, displayLocalCoords, inputLocalCoords, localCoordsRelative, dist);
		logPointerMapping(result);
		return result;
	}

	public @Nullable PlaneHit intersectPlane(Vec3 pos, Vec3 dir) {
		double p1 = pivot.subtract(pos).dot(normal);
		double p2 = dir.dot(normal);
		if(p2 == 0) return null;

		double t = p1 / p2;
		if(t < 0) return null;

		return new PlaneHit(pos.add(dir.scale(t)), t);
	}

	private MonitorControl controlAt(Vec3 displayLocalCoords) {
		if(!(window instanceof WLCToplevel)) return MonitorControl.NONE;
		if(WaylandCraft.instance == null || !WaylandCraft.instance.isDisplayChromeActive(this)) return MonitorControl.NONE;

		for(MonitorControl control : MonitorControl.values()) {
			if(!control.isButton()) continue;
			if(!isControlEnabled(control)) continue;
			ControlRect rect = controlRect(control);
			if(rect.contains(displayLocalCoords.x, displayLocalCoords.y)) return control;
		}
		if(!containsDisplayLocal(displayLocalCoords.x, displayLocalCoords.y) && chromeRect().contains(displayLocalCoords.x, displayLocalCoords.y)) return MonitorControl.CHROME;
		return MonitorControl.NONE;
	}

	private ControlRect controlRect(MonitorControl control) {
		double totalWidth = CONTROL_SIZE * 2 + CONTROL_GAP;
		double startX = width / 2.0 - totalWidth / 2.0;
		double y = -CONTROL_SIZE - CONTROL_GAP;
		return switch(control) {
		case MOVE -> new ControlRect(startX, y, CONTROL_SIZE, CONTROL_SIZE);
		case CLOSE -> new ControlRect(startX + CONTROL_SIZE + CONTROL_GAP, y, CONTROL_SIZE, CONTROL_SIZE);
		case RESIZE_APP_TOP_LEFT -> new ControlRect(CORNER_INSET, CORNER_INSET, CORNER_HANDLE, CORNER_HANDLE);
		case RESIZE_APP_TOP_RIGHT -> new ControlRect(width - CORNER_INSET - CORNER_HANDLE, CORNER_INSET, CORNER_HANDLE, CORNER_HANDLE);
		case RESIZE_APP_BOTTOM_LEFT -> new ControlRect(CORNER_INSET, height - CORNER_INSET - CORNER_HANDLE, CORNER_HANDLE, CORNER_HANDLE);
		case RESIZE_APP_BOTTOM_RIGHT -> new ControlRect(width - CORNER_INSET - CORNER_HANDLE, height - CORNER_INSET - CORNER_HANDLE, CORNER_HANDLE, CORNER_HANDLE);
		case RESIZE_MONITOR_TOP_LEFT -> new ControlRect(-CORNER_OUTSET, -CORNER_OUTSET, CORNER_HANDLE, CORNER_HANDLE);
		case RESIZE_MONITOR_TOP_RIGHT -> new ControlRect(width + CORNER_OUTSET - CORNER_HANDLE, -CORNER_OUTSET, CORNER_HANDLE, CORNER_HANDLE);
		case RESIZE_MONITOR_BOTTOM_LEFT -> new ControlRect(-CORNER_OUTSET, height + CORNER_OUTSET - CORNER_HANDLE, CORNER_HANDLE, CORNER_HANDLE);
		case RESIZE_MONITOR_BOTTOM_RIGHT -> new ControlRect(width + CORNER_OUTSET - CORNER_HANDLE, height + CORNER_OUTSET - CORNER_HANDLE, CORNER_HANDLE, CORNER_HANDLE);
		default -> new ControlRect(0, 0, 0, 0);
		};
	}

	private ControlRect chromeRect() {
		double totalWidth = CONTROL_SIZE * 2 + CONTROL_GAP;
		double x = Math.min(-OVERLAY_PADDING, width / 2.0 - totalWidth / 2.0);
		double y = -CONTROL_SIZE - CONTROL_GAP;
		double right = Math.max(width + OVERLAY_PADDING, width / 2.0 + totalWidth / 2.0);
		double bottom = height + OVERLAY_PADDING;
		return new ControlRect(x, y, right - x, bottom - y);
	}

	private void logRenderFitIfChanged(FitRect fit) {
		if(!WaylandCraft.DEBUG_WINDOWS || window.framebuffer == null) return;

		int framebufferWidth = window.framebuffer.getWidth();
		int framebufferHeight = window.framebuffer.getHeight();
		if(debugFramebufferWidth == framebufferWidth && debugFramebufferHeight == framebufferHeight && Double.compare(debugDrawX, fit.x()) == 0 && Double.compare(debugDrawY, fit.y()) == 0 && Double.compare(debugDrawWidth, fit.width()) == 0 && Double.compare(debugDrawHeight, fit.height()) == 0 && Double.compare(debugDrawScale, fit.scale()) == 0) {
			return;
		}

		debugFramebufferWidth = framebufferWidth;
		debugFramebufferHeight = framebufferHeight;
		debugDrawX = fit.x();
		debugDrawY = fit.y();
		debugDrawWidth = fit.width();
		debugDrawHeight = fit.height();
		debugDrawScale = fit.scale();

		if(window instanceof WLCX11Window x11 && x11.nativeGeometry != null) {
			WaylandCraft.LOGGER.info("WLC world render fit window={} title={} appID={} source={}x{} framebuffer={}x{} x11={}x{}+{}+{} display={}x{} draw={}x{}+{}+{} scale={}", window.getHandle(), x11.title, x11.appID, fit.sourceWidth(), fit.sourceHeight(), framebufferWidth, framebufferHeight, x11.nativeGeometry.width(), x11.nativeGeometry.height(), x11.nativeGeometry.x(), x11.nativeGeometry.y(), width, height, fit.width(), fit.height(), fit.x(), fit.y(), fit.scale());
		}
		else {
			WaylandCraft.LOGGER.info("WLC world render fit window={} source={}x{} framebuffer={}x{} geometry={}x{} display={}x{} draw={}x{}+{}+{} scale={}", window.getHandle(), fit.sourceWidth(), fit.sourceHeight(), framebufferWidth, framebufferHeight, window.geometry.width(), window.geometry.height(), width, height, fit.width(), fit.height(), fit.x(), fit.y(), fit.scale());
		}
	}

	private void logPointerMapping(DisplayHitResult result) {
		if(!WaylandCraft.DEBUG_WINDOWS || result.surface == null || result.inputLocalOrigin == null || result.surfaceLocalRelative == null) return;

		long now = System.nanoTime();
		if(now - debugLastPointerLogNanos < 250_000_000L) return;
		debugLastPointerLogNanos = now;

		FitRect fit = presentationFit();
		WaylandCraft.LOGGER.info("WLC world pointer window={} display={}x{} inputRoot={}x{} surface={} rel={}x{} draw={}x{}+{}+{} scale={}", window.getHandle(), result.surfaceLocalOrigin.x, result.surfaceLocalOrigin.y, result.inputLocalOrigin.x, result.inputLocalOrigin.y, result.surface.getDebugHandle(), result.surfaceLocalRelative.x, result.surfaceLocalRelative.y, fit.width(), fit.height(), fit.x(), fit.y(), fit.scale());
	}
	
	public void anchorToPosView(Vec3 pos, Vec3 look, Vec3 up) {
		this.pivot = pos.add(look.scale(2));
		this.rotate(look.reverse(), up.reverse());
	}
	
	public void anchorToCamera(Camera camera) {
		anchorToPosView(camera.position(), new Vec3(camera.forwardVector()), new Vec3(camera.upVector()));
	}
	
	public void anchorToEntity(Entity entity) {
		anchorToPosView(WaylandCraftUtils.getPosition(entity), WaylandCraftUtils.getLookVector(entity), WaylandCraftUtils.getUpVector(entity));
	}
	
	public static class DisplayHitResult {
		
		// WindowDisplay that was raycasted
		public final WindowDisplay target;
		
		// Surface that was hit, if any
		public final @Nullable WLCSurface surface;

		// Monitor control that was hit, if any.
		public final MonitorControl control;
		
		// World position
		public final Vec3 position;
		
		// Surface-local coordinates relative to WindowDisplay origin
		public final Vec3 surfaceLocalOrigin;

		// Root surface coordinates after inverse presentation scaling.
		public final @Nullable Vec3 inputLocalOrigin;
		
		// Surface-local coordinates relative to hit surface. Always guaranteed to not be null, if `surface` is non-null.
		public final @Nullable Vec3 surfaceLocalRelative;
		
		// Calculated distance
		public final double dist;
		
		public DisplayHitResult(WindowDisplay target, WLCSurface surface, MonitorControl control, Vec3 position, Vec3 surfaceLocalOrigin, Vec3 inputLocalOrigin, Vec3 surfaceLocalRelative, double dist) {
			this.target = target;
			this.surface = surface;
			this.control = control;
			this.position = position;
			this.surfaceLocalOrigin = surfaceLocalOrigin;
			this.inputLocalOrigin = inputLocalOrigin;
			this.surfaceLocalRelative = surfaceLocalRelative;
			this.dist = dist;
		}
		
		public boolean isMiss() {
			return surface == null && control == MonitorControl.NONE;
		}
		
		@Override
		public String toString() {
			return "{target=" + target + ", surface=" + surface + ", position=" + position + ", local=" + surfaceLocalOrigin + ", inputLocal=" + inputLocalOrigin + ", relative=" + surfaceLocalRelative + ", dist=" + dist + "}";
		}
		
	}

	public static record PlaneHit(Vec3 position, double dist) {}

	private static record ControlRect(double x, double y, double width, double height) {

		public boolean contains(double px, double py) {
			return px >= x && py >= y && px <= x + width && py <= y + height;
		}

	}

	public static enum MonitorControl {
		NONE,
		CHROME,
		MOVE,
		CLOSE,
		RESIZE_MONITOR_TOP_LEFT,
		RESIZE_MONITOR_TOP_RIGHT,
		RESIZE_MONITOR_BOTTOM_LEFT,
		RESIZE_MONITOR_BOTTOM_RIGHT,
		RESIZE_APP_TOP_LEFT,
		RESIZE_APP_TOP_RIGHT,
		RESIZE_APP_BOTTOM_LEFT,
		RESIZE_APP_BOTTOM_RIGHT;

		public boolean isButton() {
			return this == MOVE || this == CLOSE || isCornerResize();
		}

		public boolean isCornerResize() {
			return isMonitorResize() || isAppResize();
		}

		public boolean isMonitorResize() {
			return this == RESIZE_MONITOR_TOP_LEFT || this == RESIZE_MONITOR_TOP_RIGHT || this == RESIZE_MONITOR_BOTTOM_LEFT || this == RESIZE_MONITOR_BOTTOM_RIGHT;
		}

		public boolean isAppResize() {
			return this == RESIZE_APP_TOP_LEFT || this == RESIZE_APP_TOP_RIGHT || this == RESIZE_APP_BOTTOM_LEFT || this == RESIZE_APP_BOTTOM_RIGHT;
		}

		public boolean isLeftCorner() {
			return this == RESIZE_MONITOR_TOP_LEFT || this == RESIZE_MONITOR_BOTTOM_LEFT || this == RESIZE_APP_TOP_LEFT || this == RESIZE_APP_BOTTOM_LEFT;
		}

		public boolean isTopCorner() {
			return this == RESIZE_MONITOR_TOP_LEFT || this == RESIZE_MONITOR_TOP_RIGHT || this == RESIZE_APP_TOP_LEFT || this == RESIZE_APP_TOP_RIGHT;
		}
	}
	
}

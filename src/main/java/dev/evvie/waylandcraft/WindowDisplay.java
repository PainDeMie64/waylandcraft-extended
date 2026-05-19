package dev.evvie.waylandcraft;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.bridge.WLCAbstractWindow;
import dev.evvie.waylandcraft.bridge.WLCSurface;
import dev.evvie.waylandcraft.bridge.WLCX11Window;
import dev.evvie.waylandcraft.render.RenderUtils;
import dev.evvie.waylandcraft.render.RenderUtils.FitRect;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class WindowDisplay {
	
	private static final float PIXEL_SCALE = 1.0f / 500;
	
	public final WLCAbstractWindow window;
	
	// World position of window
	public Vec3 pivot = new Vec3(0, 0, 0);
	
	// Window facing direction normal
	private Vec3 normal = new Vec3(0, 0, 1);
	
	// Window orientation downwards vector, has to be orthogonal to `normal` and normalized
	private Vec3 down = new Vec3(0, -1, 0);
	
	private int width;
	private int height;
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
	
	public void updateGeometry() {
		width = window.geometry.width();
		height = window.geometry.height();
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
		RenderUtils.renderFramebuffer(window.framebuffer, poseStack, ctx.submitNodeCollector(), true, tl, bl, br, tr);
		poseStack.popPose();
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
		Vec3 origin = origin();
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
		double p1 = pivot.subtract(pos).dot(normal);
		double p2 = dir.dot(normal);
		
		// Avoid division by zero
		if(p2 == 0) return null;
		
		double t = p1 / p2;
		
		// Intersection happens behind the camera
		if(t < 0) return null;
		
		Vec3 hitPos = pos.add(dir.scale(t));
		Vec3 displayLocalCoords = worldToLocal(hitPos);
		Vec3 inputLocalCoords = displayLocalToRootLocal(displayLocalCoords);
		if(inputLocalCoords == null) {
			return new DisplayHitResult(this, null, hitPos, displayLocalCoords, null, null, t);
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
		if(p2 > 0) dist *= -1;
		
		DisplayHitResult result = new DisplayHitResult(this, hitSurface, hitPos, displayLocalCoords, inputLocalCoords, localCoordsRelative, dist);
		logPointerMapping(result);
		return result;
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
		
		public DisplayHitResult(WindowDisplay target, WLCSurface surface, Vec3 position, Vec3 surfaceLocalOrigin, Vec3 inputLocalOrigin, Vec3 surfaceLocalRelative, double dist) {
			this.target = target;
			this.surface = surface;
			this.position = position;
			this.surfaceLocalOrigin = surfaceLocalOrigin;
			this.inputLocalOrigin = inputLocalOrigin;
			this.surfaceLocalRelative = surfaceLocalRelative;
			this.dist = dist;
		}
		
		public boolean isMiss() {
			return surface == null;
		}
		
		@Override
		public String toString() {
			return "{target=" + target + ", surface=" + surface + ", position=" + position + ", local=" + surfaceLocalOrigin + ", inputLocal=" + inputLocalOrigin + ", relative=" + surfaceLocalRelative + ", dist=" + dist + "}";
		}
		
	}
	
}

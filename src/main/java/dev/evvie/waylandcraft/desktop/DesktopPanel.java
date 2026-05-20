package dev.evvie.waylandcraft.desktop;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.WaylandCraftUtils;
import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class DesktopPanel {

	private static final double PIXEL_SCALE = 1.0 / 500.0;
	private static final int WIDTH = 980;
	private static final int HEIGHT = 150;
	private static final int PAD = 12;
	private static final int BUTTON = 62;
	private static final int APP_BUTTON = 58;
	private static final int GAP = 10;
	private static final int BG = 0xdd11151c;
	private static final int BORDER = 0xffffffff;
	private static final int MUTED = 0xff49515c;
	private static final int ACTIVE = 0xff4bc17d;
	private static final int ALERT = 0xffffbf47;
	private static final int SHELF = 0xff262d37;
	private static final int ICON = 0xffffffff;

	public Vec3 pivot = new Vec3(0, 0, 0);
	private Vec3 normal = new Vec3(0, 0, 1);
	private Vec3 down = new Vec3(0, -1, 0);
	private List<ButtonRect> buttons = new ArrayList<ButtonRect>();

	public DesktopPanel() {
	}

	public void apply(PanelState state) {
		pivot = state.pivot.toVec3();
		normal = state.normal.toVec3();
		down = state.down.toVec3();
	}

	public PanelState snapshot() {
		PanelState state = new PanelState();
		state.pivot = Vec3State.from(pivot);
		state.normal = Vec3State.from(normal);
		state.down = Vec3State.from(down);
		return state;
	}

	public void anchorToEntity(Entity entity) {
		Vec3 look = WaylandCraftUtils.getLookVector(entity);
		Vec3 up = WaylandCraftUtils.getUpVector(entity);
		pivot = WaylandCraftUtils.getPosition(entity).add(look.scale(2.2)).add(up.scale(-0.35));
		normal = look.reverse();
		down = up.reverse();
	}

	public void render(LevelRenderContext ctx, WaylandCraft wlc, DesktopManager desktop) {
		buttons = buildButtons(wlc, desktop);

		Vec3 localX = localX();
		Vec3 localY = localY();
		Vec3 cameraPos = ctx.levelState().cameraRenderState.pos;
		Vec3 originRel = origin().subtract(cameraPos);
		PoseStack poseStack = ctx.poseStack();
		poseStack.pushPose();
		poseStack.translate(originRel.x, originRel.y, originRel.z);

		RenderUtils.renderSolidRect(poseStack, ctx.submitNodeCollector(), localX, localY, 0, 0, WIDTH, HEIGHT, 0.0, BG);
		RenderUtils.renderSolidOutline(poseStack, ctx.submitNodeCollector(), localX, localY, 0, 0, WIDTH, HEIGHT, 0.003, 4, BORDER);

		for(ButtonRect rect : buttons) {
			int color = switch(rect.kind) {
			case LAUNCHER -> ACTIVE;
			case MOVE -> MUTED;
			case WORKSPACE -> rect.active ? ACTIVE : MUTED;
			case WINDOW -> rect.alert ? ALERT : SHELF;
			default -> MUTED;
			};
			RenderUtils.renderSolidRect(poseStack, ctx.submitNodeCollector(), localX, localY, rect.x, rect.y, rect.w, rect.h, 0.006, color);
			RenderUtils.renderSolidOutline(poseStack, ctx.submitNodeCollector(), localX, localY, rect.x, rect.y, rect.w, rect.h, 0.009, 3, BORDER);
			renderButtonIcon(poseStack, ctx, localX, localY, rect, wlc);
		}

		poseStack.popPose();
	}

	public PanelHit intersect(Vec3 pos, Vec3 dir) {
		PlaneHit planeHit = intersectPlane(pos, dir);
		if(planeHit == null) return null;

		Vec3 local = worldToLocal(planeHit.position());
		if(local.x < 0 || local.y < 0 || local.x > WIDTH || local.y > HEIGHT) return null;
		for(ButtonRect rect : buttons) {
			if(rect.contains(local.x, local.y)) return new PanelHit(this, rect, planeHit.position(), local, planeHit.dist());
		}
		return new PanelHit(this, new ButtonRect(ButtonKind.NONE, 0, local.x, local.y, 0, 0), planeHit.position(), local, planeHit.dist());
	}

	public Vec3 normal() {
		return normal;
	}

	public Vec3 down() {
		return down;
	}

	public void moveWithGrab(Vec3 pos, Vec3 view, Vec3 up, Vec3 grabbedLocal) {
		normal = view.reverse();
		down = up.reverse();
		Vec3 target = pos.add(view.scale(2.2));
		pivot = target
				.add(localX().scale(WIDTH / 2.0 - grabbedLocal.x))
				.add(localY().scale(HEIGHT / 2.0 - grabbedLocal.y));
	}

	private Vec3 right() {
		return normal.cross(down);
	}

	private Vec3 localX() {
		return right().scale(PIXEL_SCALE);
	}

	private Vec3 localY() {
		return down.scale(PIXEL_SCALE);
	}

	private Vec3 origin() {
		return pivot.add(localX().scale(-WIDTH / 2.0)).add(localY().scale(-HEIGHT / 2.0));
	}

	private Vec3 worldToLocal(Vec3 in) {
		Vec3 world = in.subtract(origin());
		Vec3 localX = localX();
		Vec3 localY = localY();
		return new Vec3(world.dot(localX) / localX.lengthSqr(), world.dot(localY) / localY.lengthSqr(), world.dot(normal));
	}

	private PlaneHit intersectPlane(Vec3 pos, Vec3 dir) {
		double p1 = pivot.subtract(pos).dot(normal);
		double p2 = dir.dot(normal);
		if(Math.abs(p2) < 0.000001) return null;
		double t = p1 / p2;
		if(t < 0) return null;
		return new PlaneHit(pos.add(dir.scale(t)), t);
	}

	private List<ButtonRect> buildButtons(WaylandCraft wlc, DesktopManager desktop) {
		ArrayList<ButtonRect> result = new ArrayList<ButtonRect>();
		result.add(new ButtonRect(ButtonKind.LAUNCHER, 0, PAD, PAD, BUTTON, BUTTON));
		result.add(new ButtonRect(ButtonKind.MOVE, 0, PAD + BUTTON + GAP, PAD, BUTTON, BUTTON));

		double x = PAD + BUTTON * 2 + GAP * 3;
		for(int workspace = 1; workspace <= 4; workspace++) {
			ButtonRect rect = new ButtonRect(ButtonKind.WORKSPACE, workspace, x, PAD, BUTTON, BUTTON);
			rect.active = desktop.currentWorkspace() == workspace;
			result.add(rect);
			x += BUTTON + GAP;
		}

		x = PAD;
		double y = PAD + BUTTON + GAP;
		for(WLCToplevel toplevel : wlc.bridge.getMappedToplevels()) {
			ButtonRect rect = new ButtonRect(ButtonKind.WINDOW, toplevel.getHandle(), x, y, APP_BUTTON, APP_BUTTON);
			rect.active = wlc.hasDisplayFor(toplevel);
			rect.alert = !rect.active;
			result.add(rect);
			x += APP_BUTTON + GAP;
			if(x + APP_BUTTON > WIDTH - PAD) break;
		}
		return result;
	}

	private void renderButtonIcon(PoseStack poseStack, LevelRenderContext ctx, Vec3 localX, Vec3 localY, ButtonRect rect, WaylandCraft wlc) {
		double x = rect.x;
		double y = rect.y;
		double s = rect.w;
		double z = 0.014;
		double t = 5;
		switch(rect.kind) {
		case LAUNCHER:
			for(int row = 0; row < 2; row++) for(int col = 0; col < 2; col++) {
				RenderUtils.renderSolidRect(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * (0.24 + col * 0.28), y + s * (0.24 + row * 0.28), s * 0.18, s * 0.18, z, ICON);
			}
			break;
		case MOVE:
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.50, y + s * 0.22, x + s * 0.50, y + s * 0.78, z, t, ICON);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.22, y + s * 0.50, x + s * 0.78, y + s * 0.50, z, t, ICON);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.50, y + s * 0.22, x + s * 0.38, y + s * 0.34, z, t, ICON);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.50, y + s * 0.22, x + s * 0.62, y + s * 0.34, z, t, ICON);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.50, y + s * 0.78, x + s * 0.38, y + s * 0.66, z, t, ICON);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.50, y + s * 0.78, x + s * 0.62, y + s * 0.66, z, t, ICON);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.22, y + s * 0.50, x + s * 0.34, y + s * 0.38, z, t, ICON);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.22, y + s * 0.50, x + s * 0.34, y + s * 0.62, z, t, ICON);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.78, y + s * 0.50, x + s * 0.66, y + s * 0.38, z, t, ICON);
			RenderUtils.renderSolidLine(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.78, y + s * 0.50, x + s * 0.66, y + s * 0.62, z, t, ICON);
			break;
		case WORKSPACE:
			double inset = s * 0.24;
			RenderUtils.renderSolidRect(poseStack, ctx.submitNodeCollector(), localX, localY, x + inset, y + inset, s - inset * 2, s - inset * 2, z, rect.active ? BG : ICON);
			break;
		case WINDOW:
			WLCToplevel toplevel = wlc.bridge.getToplevel(rect.id);
			Identifier icon = null;
			if(toplevel != null) {
				DesktopEntry entry = wlc.xdgManager.forAppId(toplevel.appID);
				if(entry != null) icon = entry.getIcon();
			}
			if(icon != null) {
				Vec3 depth = new Vec3(0, 0, z);
				double pad = 7;
				Vec3 tl = localX.scale(x + pad).add(localY.scale(y + pad)).add(depth);
				Vec3 bl = localX.scale(x + pad).add(localY.scale(y + s - pad)).add(depth);
				Vec3 br = localX.scale(x + s - pad).add(localY.scale(y + s - pad)).add(depth);
				Vec3 tr = localX.scale(x + s - pad).add(localY.scale(y + pad)).add(depth);
				RenderUtils.renderTexture(poseStack, ctx.submitNodeCollector(), icon, false, tl, bl, br, tr);
			}
			else {
				RenderUtils.renderSolidOutline(poseStack, ctx.submitNodeCollector(), localX, localY, x + s * 0.25, y + s * 0.25, s * 0.50, s * 0.50, z, 4, ICON);
			}
			break;
		default:
			break;
		}
	}

	public static class PanelState {
		public Vec3State pivot = new Vec3State(0, 0, 0);
		public Vec3State normal = new Vec3State(0, 0, 1);
		public Vec3State down = new Vec3State(0, -1, 0);

		public static PanelState defaultState() {
			return new PanelState();
		}
	}

	public static class Vec3State {
		public double x;
		public double y;
		public double z;

		public Vec3State() {
		}

		public Vec3State(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public Vec3 toVec3() {
			return new Vec3(x, y, z);
		}

		public static Vec3State from(Vec3 vec) {
			return new Vec3State(vec.x, vec.y, vec.z);
		}
	}

	public static record PanelHit(DesktopPanel panel, ButtonRect button, Vec3 position, Vec3 local, double dist) {}
	public static record PlaneHit(Vec3 position, double dist) {}

	public static class ButtonRect {
		public final ButtonKind kind;
		public final long id;
		public final double x;
		public final double y;
		public final double w;
		public final double h;
		public boolean active;
		public boolean alert;

		public ButtonRect(ButtonKind kind, long id, double x, double y, double w, double h) {
			this.kind = kind;
			this.id = id;
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}

		public boolean contains(double px, double py) {
			return px >= x && py >= y && px <= x + w && py <= y + h;
		}
	}

	public static enum ButtonKind {
		NONE, LAUNCHER, MOVE, WORKSPACE, WINDOW;
	}

}

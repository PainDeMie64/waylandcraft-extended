package dev.evvie.waylandcraft.grabs;

import dev.evvie.waylandcraft.WindowDisplay;
import net.minecraft.world.phys.Vec3;

public class MonitorRotationGrab extends PointerGrab {

	public static enum Axis {
		NONE, X, Y, Z;
	}

	private final WindowDisplay window;
	private final Vec3 initialNormal;
	private final Vec3 initialDown;
	private Vec3 initialView = null;
	private Axis axis = Axis.NONE;

	public MonitorRotationGrab(WindowDisplay window) {
		super(-1);
		this.window = window;
		this.initialNormal = window.normal();
		this.initialDown = window.down();
	}

	public WindowDisplay window() {
		return window;
	}

	public void setAxis(Axis axis) {
		this.axis = axis;
		this.initialView = null;
	}

	public Axis axis() {
		return axis;
	}

	@Override
	public void init() throws GrabDroppedException {
		if(!window.isValid()) this.drop();
	}

	@Override
	public void release(boolean force) throws GrabDroppedException {
	}

	@Override
	public void moveWorld(Vec3 pos, Vec3 view, Vec3 up) throws GrabDroppedException {
		if(!window.isValid()) this.drop();

		if(axis == Axis.NONE) {
			window.rotate(view.reverse().normalize(), up.reverse().normalize());
			wlc.snapDisplayOrientation(window);
			return;
		}

		if(initialView == null) initialView = view.normalize();
		Vec3 axisVector = axisVector(axis);
		Vec3 from = projectOntoRotationPlane(initialView, axisVector);
		Vec3 to = projectOntoRotationPlane(view.normalize(), axisVector);
		if(from.lengthSqr() < 0.000001 || to.lengthSqr() < 0.000001) return;

		from = from.normalize();
		to = to.normalize();
		double dot = Math.clamp(from.dot(to), -1.0, 1.0);
		double angle = Math.acos(dot);
		double sign = Math.signum(axisVector.dot(from.cross(to)));
		if(sign == 0) sign = 1;
		angle *= sign;
		angle = wlc.snapAngleRadians(angle);

		window.rotate(rotateAroundAxis(initialNormal, axisVector, angle).normalize(), rotateAroundAxis(initialDown, axisVector, angle).normalize());
	}

	private static Vec3 axisVector(Axis axis) {
		return switch(axis) {
		case X -> new Vec3(1, 0, 0);
		case Y -> new Vec3(0, 1, 0);
		case Z -> new Vec3(0, 0, 1);
		default -> new Vec3(0, 1, 0);
		};
	}

	private static Vec3 projectOntoRotationPlane(Vec3 value, Vec3 axis) {
		return value.subtract(axis.scale(value.dot(axis)));
	}

	private static Vec3 rotateAroundAxis(Vec3 value, Vec3 axis, double angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		return value.scale(cos).add(axis.cross(value).scale(sin)).add(axis.scale(axis.dot(value) * (1 - cos)));
	}

}

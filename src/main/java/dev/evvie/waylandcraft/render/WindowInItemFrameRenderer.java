package dev.evvie.waylandcraft.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.evvie.waylandcraft.bridge.WLCToplevel;
import dev.evvie.waylandcraft.render.RenderUtils.FitRect;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

public class WindowInItemFrameRenderer {
	
	public void render(WLCToplevel toplevel, PoseStack poseStack, SubmitNodeCollector collector) {
		if(toplevel.framebuffer == null) return;
		
		poseStack.pushPose();
		poseStack.translate(1.0f, 1.0f, -0.01f);
		poseStack.scale(2.0f, 2.0f, 1.0f);
		poseStack.rotateAround(Axis.ZP.rotationDegrees(180), 0, 0, 0);
		
		int width = toplevel.geometry.width();
		int height = toplevel.geometry.height();
		int resolution;
		
		if(width > height) {
			resolution = width;
		}
		else {
			resolution = height;
		}
		
		float scale = 1.0f / resolution;
		poseStack.scale(scale, scale, 1.0f);
		
		FitRect fit = RenderUtils.aspectFit(toplevel.framebuffer, resolution / 2.0 - width / 2.0, resolution / 2.0 - height / 2.0, width, height);
		
		Vec3 tl = new Vec3(fit.x(), fit.y(), 0);
		Vec3 bl = new Vec3(fit.x(), fit.bottom(), 0);
		Vec3 br = new Vec3(fit.right(), fit.bottom(), 0);
		Vec3 tr = new Vec3(fit.right(), fit.y(), 0);
		
		RenderUtils.renderFramebuffer(toplevel.framebuffer, poseStack, collector, true, tl, bl, br, tr);
		
		poseStack.popPose();
	}
	
}

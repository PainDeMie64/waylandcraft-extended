package dev.evvie.waylandcraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class WaylandCraft implements ModInitializer, ClientModInitializer {
	public static final String MOD_ID = "waylandcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
	}

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing WaylandCraft");
		
		WorldRenderEvents.END.register(context -> {
			RenderSystem.enableDepthTest();
			Vec3 vec = new Vec3(-250, 65, -500);
			RenderUtils.drawTexturedQuad(context.camera(), new ResourceLocation("waylandcraft", "icon.png"),
					vec, vec.add(1, 0, 0), vec.add(1, 1, 0), vec.add(0, 1, 0),
					new Vec2(0, 1), new Vec2(1, 1), new Vec2(1, 0), new Vec2(0, 0));
			int offset = (int) (System.currentTimeMillis() / 10);
			vec = vec.add(1, 0, 0);
			RenderUtils.drawTexturedQuad(context.camera(), RenderUtils.getTestTexture(offset),
					vec, vec.add(1, 0, 0), vec.add(1, 1, 0), vec.add(0, 1, 0),
					new Vec2(0, 1), new Vec2(1, 1), new Vec2(1, 0), new Vec2(0, 0));
			RenderUtils.drawLine(context.camera(), new Vec3(-250, 65, -500), new Vec3(-250, 64, -500), 1.0f, 0.0f, 1.0f);
			RenderUtils.drawTracer(context.camera(), new Vec3(-250, 64, -500), 1.0f, 0.8f, 0.0f);
			RenderUtils.drawBlockOutline(context.camera(), new BlockPos(-256, 65, -500), 1.0f, 0.0f, 0.0f);
			RenderUtils.drawBlockOverlay(context.camera(), new BlockPos(-258, 65, -500), 1.0f, 0.0f, 0.0f);
			RenderUtils.drawBlockTexOverlay(context.camera(), new ResourceLocation("minecraft", "textures/block/diamond_ore.png"), new BlockPos(-260, 65, -500));
		});
	}
}
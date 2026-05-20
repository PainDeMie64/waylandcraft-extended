package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	
	@Inject(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V"), cancellable = true)
	public void onButton(long windowHandle, MouseButtonInfo buttonInfo, int action, CallbackInfo info) {
		if(WaylandCraft.instance.onButtonPress(windowHandle, buttonInfo.button(), action, buttonInfo.modifiers())) info.cancel();
	}
	
	@Inject(method = "onScroll", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;", ordinal = 1), cancellable = true)
	public void onScroll(long windowHandle, double scrollX, double scrollY, CallbackInfo info) {
		if(WaylandCraft.instance.onScroll(windowHandle, scrollX, scrollY)) info.cancel();
	}
	
	@Shadow public double accumulatedDX;
	@Shadow public double accumulatedDY;
	@Shadow private double xpos;
	@Shadow private double ypos;
	@Shadow private boolean ignoreFirstMove;

	@Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
	public void onMove(long windowHandle, double x, double y, CallbackInfo info) {
		if(WaylandCraft.instance == null) return;

		if(ignoreFirstMove) {
			if(WaylandCraft.instance.onRawMouseMove(windowHandle, 0, 0)) {
				xpos = x;
				ypos = y;
				ignoreFirstMove = false;
				info.cancel();
			}
			return;
		}

		if(WaylandCraft.instance.onRawMouseMove(windowHandle, x - xpos, y - ypos)) {
			xpos = x;
			ypos = y;
			info.cancel();
		}
	}
	
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	public void onTurnPlayer(double timeDelta, CallbackInfo info) {
		if(WaylandCraft.instance.onMouseTurn(accumulatedDX, accumulatedDY)) info.cancel();
	}
	
}

package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.debug.InputTrace;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	
	@Inject(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V"), cancellable = true)
	public void onButton(long windowHandle, MouseButtonInfo buttonInfo, int action, CallbackInfo info) {
		InputTrace.begin("glfw.mouse_button", "\"window\":" + windowHandle + ",\"button\":" + buttonInfo.button() + ",\"action\":" + action + ",\"modifiers\":" + buttonInfo.modifiers());
		try {
			boolean consumed = WaylandCraft.instance.onButtonPress(windowHandle, buttonInfo.button(), action, buttonInfo.modifiers());
			InputTrace.info("glfw.mouse_button.result", "\"consumed\":" + consumed);
			if(consumed) info.cancel();
		} finally {
			InputTrace.clear();
		}
	}
	
	@Inject(method = "onScroll", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;", ordinal = 1), cancellable = true)
	public void onScroll(long windowHandle, double scrollX, double scrollY, CallbackInfo info) {
		InputTrace.begin("glfw.scroll", "\"window\":" + windowHandle + ",\"scroll_x\":" + scrollX + ",\"scroll_y\":" + scrollY);
		try {
			boolean consumed = WaylandCraft.instance.onScroll(windowHandle, scrollX, scrollY);
			InputTrace.info("glfw.scroll.result", "\"consumed\":" + consumed);
			if(consumed) info.cancel();
		} finally {
			InputTrace.clear();
		}
	}
	
	@Shadow public double accumulatedDX;
	@Shadow public double accumulatedDY;
	@Shadow private double xpos;
	@Shadow private double ypos;
	@Shadow private boolean ignoreFirstMove;

	@Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
	public void onMove(long windowHandle, double x, double y, CallbackInfo info) {
		if(WaylandCraft.instance == null) return;

		InputTrace.begin("glfw.mouse_move", "\"window\":" + windowHandle + ",\"x\":" + x + ",\"y\":" + y + ",\"ignore_first\":" + ignoreFirstMove);
		if(ignoreFirstMove) {
			try {
				boolean consumed = WaylandCraft.instance.onRawMouseMove(windowHandle, 0, 0);
				InputTrace.info("glfw.mouse_move.result", "\"consumed\":" + consumed + ",\"dx\":0.0,\"dy\":0.0");
				if(consumed) {
					xpos = x;
					ypos = y;
					ignoreFirstMove = false;
					info.cancel();
				}
			} finally {
				InputTrace.clear();
			}
			return;
		}

		double dx = x - xpos;
		double dy = y - ypos;
		try {
			boolean consumed = WaylandCraft.instance.onRawMouseMove(windowHandle, dx, dy);
			InputTrace.info("glfw.mouse_move.result", "\"consumed\":" + consumed + ",\"dx\":" + dx + ",\"dy\":" + dy);
			if(consumed) {
				xpos = x;
				ypos = y;
				info.cancel();
			}
		} finally {
			InputTrace.clear();
		}
	}
	
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	public void onTurnPlayer(double timeDelta, CallbackInfo info) {
		InputTrace.begin("minecraft.turn_player", "\"accumulated_dx\":" + accumulatedDX + ",\"accumulated_dy\":" + accumulatedDY + ",\"time_delta\":" + timeDelta);
		try {
			boolean consumed = WaylandCraft.instance.onMouseTurn(accumulatedDX, accumulatedDY);
			InputTrace.info("minecraft.turn_player.result", "\"consumed\":" + consumed);
			if(consumed) info.cancel();
		} finally {
			InputTrace.clear();
		}
	}
	
}

package dev.evvie.waylandcraft.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.debug.InputTrace;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	
	@Inject(method = "keyPress", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;getKey(Lnet/minecraft/client/input/KeyEvent;)Lcom/mojang/blaze3d/platform/InputConstants$Key;", ordinal = 1), cancellable = true)
	public void onPressInGame(long windowHandle, int action, KeyEvent event, CallbackInfo info) {
		int scancode = WaylandCraft.correctScancode(event.scancode());
		
		if(Minecraft.getInstance().level == null) {
			InputTrace.info("minecraft.key.skip", "\"reason\":\"no-level\",\"key\":" + event.key() + ",\"scancode\":" + scancode + ",\"action\":" + action);
			return;
		}
		if(Minecraft.getInstance().screen != null) {
			InputTrace.info("minecraft.key.skip", "\"reason\":\"screen-open\",\"screen\":" + InputTrace.s(Minecraft.getInstance().screen.getClass().getSimpleName()) + ",\"key\":" + event.key() + ",\"scancode\":" + scancode + ",\"action\":" + action);
			return;
		}
		
		boolean consumed = WaylandCraft.instance.onKeyPress(windowHandle, event.key(), scancode, action, event.modifiers(), event);
		InputTrace.info("minecraft.key.result", "\"consumed\":" + consumed + ",\"key\":" + event.key() + ",\"scancode\":" + scancode + ",\"action\":" + action + ",\"modifiers\":" + event.modifiers());
		if(consumed) info.cancel();
	}
	
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = false)
	public void onPressGlobal(long windowHandle, int action, KeyEvent event, CallbackInfo info) {
		int scancode = WaylandCraft.correctScancode(event.scancode());
		InputTrace.begin("glfw.key", "\"window\":" + windowHandle + ",\"key\":" + event.key() + ",\"raw_scancode\":" + event.scancode() + ",\"corrected_scancode\":" + scancode + ",\"action\":" + action + ",\"modifiers\":" + event.modifiers());
		
		if(action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return;
		if(WaylandCraft.instance.bridge == null) return;
		
		WaylandCraft.instance.bridge.internalKeyUpdate(scancode, action == GLFW.GLFW_PRESS);
	}

	@Inject(method = "keyPress", at = @At("RETURN"), cancellable = false)
	public void onPressReturn(long windowHandle, int action, KeyEvent event, CallbackInfo info) {
		InputTrace.clear();
	}
	
}

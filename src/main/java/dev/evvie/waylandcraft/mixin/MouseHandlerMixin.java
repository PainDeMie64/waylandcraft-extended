package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.InputConstants;

import dev.evvie.waylandcraft.WaylandCraft;
import dev.evvie.waylandcraft.Window;
import dev.evvie.waylandcraft.Window.WindowHitResult;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.MouseHandler;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	
	@Inject(method = "onPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V"), cancellable = true)
	public void onPress(long windowHandle, int button, int action, int modifiers, CallbackInfo info) {
		WindowHitResult result = WaylandCraft.instance.hitResult;
		if(result == null) return;
		
		Window window = result.target;
		if(!window.isAlive()) {
			WaylandCraft.instance.hitResult = null;
			return;
		}
		
		info.cancel();
		
		KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(button), false);
		
		// Check if on the backside of the window
		if(result.dist < 0) return;
		
		// 0x110 is linux BTN_LEFT, see linux/input-event-codes.h
		WaylandCraft.instance.bridge.sendButton(0x110 + button, action);
	}
	
}

package dev.evvie.waylandcraft.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.evvie.waylandcraft.debug.TextureDebug;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

@Mixin(TextureManager.class)
public class TextureManagerMixin {

	@Shadow @Final private Map<Identifier, AbstractTexture> byPath;

	@Inject(method = "register", at = @At("HEAD"))
	private void waylandcraft$debugRegister(Identifier location, AbstractTexture texture, CallbackInfo info) {
		TextureDebug.textureManagerRegister(location, byPath.get(location), texture);
	}

	@Inject(method = "release", at = @At("HEAD"))
	private void waylandcraft$debugRelease(Identifier location, CallbackInfo info) {
		TextureDebug.textureManagerRelease(location, byPath.get(location));
	}

}

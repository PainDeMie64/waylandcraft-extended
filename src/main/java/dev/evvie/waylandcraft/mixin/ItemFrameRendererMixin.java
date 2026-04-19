package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.renderer.entity.ItemFrameRenderer;

@Mixin(ItemFrameRenderer.class)
public class ItemFrameRendererMixin {
	
//	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;IILcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;I)V"))
//	public void renderItem(ItemRenderer itemRenderer, ItemStack itemStack, ItemDisplayContext ctx, int light, int overlay, PoseStack poseStack, MultiBufferSource multiBufferSource, Level level, int itemFrameEntityId) {
//		if(itemStack.is(WindowItem.WINDOW)) {
//			WLCToplevel toplevel = WindowItem.getToplevel(itemStack);
//			if(toplevel != null) {
//				WaylandCraft.instance.windowInItemFrameRenderer.render(toplevel, poseStack, multiBufferSource);
//				return;
//			}
//		}
//		
//		itemRenderer.renderStatic(itemStack, ctx, light, overlay, poseStack, multiBufferSource, level, itemFrameEntityId);
//	}
//	
//	@Redirect(method = "getFrameModelResourceLoc", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
//	public boolean redirectItemIsModelLoc(ItemStack itemStack, Item item) {
//		if(itemStack.is(WindowItem.WINDOW) && WindowItem.getToplevel(itemStack) != null) return true;
//		return itemStack.is(item);
//	}

}

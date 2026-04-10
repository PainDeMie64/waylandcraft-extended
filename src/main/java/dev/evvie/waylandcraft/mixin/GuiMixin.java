package dev.evvie.waylandcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.evvie.waylandcraft.WaylandCraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

@Mixin(Gui.class)
public class GuiMixin {
	
	private static final ResourceLocation TLBR_DIAGONAL_CROSSHAIR = new ResourceLocation(WaylandCraft.MOD_ID, "crosshair/tlbr_diagonal");
	private static final ResourceLocation TRBL_DIAGONAL_CROSSHAIR = new ResourceLocation(WaylandCraft.MOD_ID, "crosshair/trbl_diagonal");
	private static final ResourceLocation LEFT_RIGHT_CROSSHAIR = new ResourceLocation(WaylandCraft.MOD_ID, "crosshair/left_right");
	private static final ResourceLocation TOP_BOTTOM_CROSSHAIR = new ResourceLocation(WaylandCraft.MOD_ID, "crosshair/top_bottom");
	
	@Redirect(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V", ordinal = 0))
	public void crosshairBlitSprite(GuiGraphics context, ResourceLocation original, int x, int y, int width, int height) {
		ResourceLocation crosshair = original;
		
		switch(WaylandCraft.instance.cursorShape) {
		case 0: return; // hide cursor
		case 18: crosshair = LEFT_RIGHT_CROSSHAIR; break; // e_resize
		case 19: crosshair = TOP_BOTTOM_CROSSHAIR; break; // n_resize
		case 20: crosshair = TRBL_DIAGONAL_CROSSHAIR; break; // ne_resize
		case 21: crosshair = TLBR_DIAGONAL_CROSSHAIR; break; // nw_resize
		case 22: crosshair = TOP_BOTTOM_CROSSHAIR; break; // s_resize
		case 23: crosshair = TLBR_DIAGONAL_CROSSHAIR; break; // se_resize
		case 24: crosshair = TRBL_DIAGONAL_CROSSHAIR; break; // sw_resize
		case 25: crosshair = LEFT_RIGHT_CROSSHAIR; break; // w_resize
		case 26: crosshair = LEFT_RIGHT_CROSSHAIR; break; // ew_resize
		case 27: crosshair = TOP_BOTTOM_CROSSHAIR; break; // ns_resize
		case 28: crosshair = TRBL_DIAGONAL_CROSSHAIR; break; // nesw_resize
		case 29: crosshair = TLBR_DIAGONAL_CROSSHAIR; break; // nwse_resize
		case 30: crosshair = LEFT_RIGHT_CROSSHAIR; break; // col_resize
		case 31: crosshair = TOP_BOTTOM_CROSSHAIR; break; // row_resize
		}
		
		context.blitSprite(crosshair, x, y, width, height);
	}
	
}

package com.hbm.airdefense.mixin;

import com.hbm.airdefense.AirDefense;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Our creative tab's section titles: a bar with the name over each row of filler stacks that starts a section
 * (see AirDefense.tab). Filler slots get no hover highlight.
 */
@Mixin(AbstractContainerScreen.class)
abstract class CreativeSectionsMixin {

    private static final int BAR = 0xFF2E3A33, LINE = 0xFF8FB89A, TEXT = 0xFFE4EEE6;

    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow protected @Nullable Slot hoveredSlot;

    @Inject(method = "extractSlots", at = @At("TAIL"))
    private void ntmad$sections(GuiGraphicsExtractor g, int mx, int my, CallbackInfo ci) {
        if (!((Object) this instanceof CreativeModeInventoryScreen)) return;
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (!stack.is(AirDefense.SECTION.get())) continue;
            String key = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("section", "");
            if (key.isEmpty()) continue;
            int x = slot.x - 1, y = slot.y - 1, w = 9 * 18;
            g.fill(x, y + 2, x + w, y + 16, BAR);
            g.fill(x, y + 15, x + w, y + 16, LINE);
            g.text(Minecraft.getInstance().font, Component.translatable("gui.ntm_air_defense.section." + key), x + 4, y + 5, TEXT, false);
        }
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"}, at = @At("HEAD"), cancellable = true)
    private void ntmad$noHighlight(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (hoveredSlot != null && hoveredSlot.getItem().is(AirDefense.SECTION.get())) ci.cancel();
    }
}

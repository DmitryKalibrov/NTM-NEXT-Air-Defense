package com.hbm.airdefense.mixin;

import com.hbm.airdefense.client.ConsoleClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The console hover card, drawn as a tooltip just right of the crosshair. */
@Mixin(Hud.class)
abstract class ConsoleHudMixin {

    @Inject(method = "extractCrosshair", at = @At("TAIL"))
    private void ntmad$consoleCard(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        if (ConsoleClient.card.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        g.tooltip(mc.font, ConsoleClient.card.stream().map(c -> ClientTooltipComponent.create(c.getVisualOrderText())).toList(),
                g.guiWidth() / 2 + 12, g.guiHeight() / 2,
                (sw, sh, x, y, w, h) -> new Vector2i(Math.min(x, sw - w - 4), Math.clamp(y - h / 2, 4, Math.max(4, sh - h - 4))), null);
    }
}

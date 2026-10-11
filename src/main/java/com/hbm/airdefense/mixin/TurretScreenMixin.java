package com.hbm.airdefense.mixin;

import com.hbm.airdefense.BlockEntityPantsir;
import com.hbm.client.gui.ScreenInfoContainer;
import com.hbm.client.gui.ScreenTurretBase;
import com.hbm.inventory.container.MenuTurretBase;
import com.hbm.util.TooltipStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NTM's turret screen: for the Pantsir, a second row of target toggles on a side panel left of the
 * window, for the missiles. NTM's own row then sets what the guns shoot at.
 */
@Mixin(ScreenTurretBase.class)
abstract class TurretScreenMixin extends ScreenInfoContainer<MenuTurretBase> {

    private static final int PX = -60, PY = 22, PW = 58, PH = 32, ROW_X = PX + 5, ROW_Y = PY + 17;
    private static final String[] KEYS = {"players", "animals", "mobs", "machines"};

    @Shadow @Final private Identifier texture;

    @Shadow
    private void send(CompoundTag tag) {}

    TurretScreenMixin(MenuTurretBase menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private @Nullable BlockEntityPantsir ntmad$pantsir() {
        return menu.blockEntity() instanceof BlockEntityPantsir p ? p : null;
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void ntmad$panel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        BlockEntityPantsir p = ntmad$pantsir();
        if (p == null) return;
        int x = leftPos + PX, y = topPos + PY;
        g.fill(x, y, x + PW, y + PH, 0xFF000000);
        g.fill(x + 1, y + 1, x + PW - 1, y + PH - 1, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, x + PW - 1, y + PH - 1, 0xFF555555);
        g.fill(x + 2, y + 2, x + PW - 2, y + PH - 2, 0xFFC6C6C6);
        g.text(font, Component.translatable("gui.ntm_air_defense.missiles"), x + 5, y + 5, 0xFF404040, false);
        for (int i = 0; i < 4; i++) {
            int bx = leftPos + ROW_X + i * 14, by = topPos + ROW_Y;
            // NTM's own toggle frame with its dark icon, lit icon on top when on
            g.blit(RenderPipelines.GUI_TEXTURED, texture, bx - 1, by - 1, 7 + i * 14, 29, 12, 12, 256, 256);
            if ((p.missileFilter & 1 << i) != 0) g.blit(RenderPipelines.GUI_TEXTURED, texture, bx, by, 176, i * 10, 10, 10, 256, 256);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ntmad$tooltips(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        BlockEntityPantsir p = ntmad$pantsir();
        if (p == null) return;
        for (int i = 0; i < 4; i++) {
            Component state = (p.missileFilter & 1 << i) != 0
                    ? Component.translatable("turret.on").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("turret.off").withStyle(ChatFormatting.RED);
            drawCustomInfoStat(g, mouseX, mouseY, ROW_X + i * 14, ROW_Y, 10, 10,
                    TooltipStyle.lines(Component.translatable("gui.ntm_air_defense.missiles." + KEYS[i], state)));
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ntmad$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ntmad$pantsir() == null) return;
        int mx = (int) event.x(), my = (int) event.y();
        for (int i = 0; i < 4; i++) {
            if (checkClick(mx, my, ROW_X + i * 14, ROW_Y, 10, 10)) {
                playClick();
                CompoundTag tag = new CompoundTag();
                tag.putInt("toggle", BlockEntityPantsir.MISSILE_BUTTON + i);
                send(tag);
                cir.setReturnValue(true);
                return;
            }
        }
    }
}

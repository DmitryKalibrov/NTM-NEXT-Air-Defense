package com.hbm.airdefense.mixin;

import com.hbm.airdefense.ItemTargetCaller;
import com.hbm.entity.missile.EntityMissileBaseNT;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Practice missiles from the debug caller: no warhead on impact, no blast or debris when shot down. */
@Mixin(EntityMissileBaseNT.class)
abstract class PracticeMissileMixin {

    @Redirect(method = "fly", at = @At(value = "INVOKE",
            target = "Lcom/hbm/entity/missile/EntityMissileBaseNT;onMissileImpact(Lnet/minecraft/world/phys/BlockHitResult;)V"))
    private void ntmad$impact(EntityMissileBaseNT missile, BlockHitResult hit) {
        if (!missile.entityTags().contains(ItemTargetCaller.PRACTICE)) missile.onMissileImpact(hit);
    }

    @Inject(method = "killMissile", at = @At("HEAD"), cancellable = true)
    private void ntmad$kill(CallbackInfo ci) {
        EntityMissileBaseNT missile = (EntityMissileBaseNT) (Object) this;
        if (!missile.entityTags().contains(ItemTargetCaller.PRACTICE)) return;
        missile.discard();
        ci.cancel();
    }
}

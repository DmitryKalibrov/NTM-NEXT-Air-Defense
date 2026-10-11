package com.hbm.airdefense.mixin;

import com.hbm.airdefense.EntitySAM;
import com.hbm.airdefense.EntitySpentStage;
import com.hbm.airdefense.EntityTargetDrone;
import com.hbm.airdefense.EntityTracer;
import com.hbm.entity.FarVisible;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NTM 1.0.3+ tracks and draws FarVisible entities out to each player's LOD range (FarTracking,
 * Crankshaft LOD): SAMs, their spent stages, practice targets and tracers fly far beyond view distance.
 */
@Mixin({EntitySAM.class, EntitySpentStage.class, EntityTargetDrone.class, EntityTracer.class})
abstract class FarVisibleMixin implements FarVisible {}

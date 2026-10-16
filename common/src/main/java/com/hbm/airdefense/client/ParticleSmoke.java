package com.hbm.airdefense.client;

import com.hbm.airdefense.AirDefense;
import com.hbm.api.client.ShaderPacks;
import com.hbm.client.particle.ParticleLayers;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Rocket smoke: a volumetric puff (textures/particle/smoke_*.png, rendered by tools/gen_smoke.py, lit from above
 * - so it never rolls, it is mirrored at random instead) that starts small and lit
 * orange by the flame it came out of, cools to grey within a few ticks, then swells, drifts
 * with the wind and thins out over many seconds - so a missile leaves a thick lingering column.
 */
public class ParticleSmoke extends SingleQuadParticle {

    private static final Identifier[] SPRITES = new Identifier[16];
    static {
        for (int i = 0; i < SPRITES.length; i++) SPRITES[i] = AirDefense.id("smoke_" + i);
    }
    /**
     * Our own smoke shader (shaders/core/smoke.*): vanilla's particle fog ends at render distance, which cut the
     * smoke off where Distant Horizons terrain goes on; this one hazes it gently over kilometres and has no
     * alpha cut-off. Translucent, no depth write, like vanilla's translucent particles.
     */
    private static final Layer LAYER = new Layer(true, TextureAtlas.LOCATION_PARTICLES, RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
            .withLocation(AirDefense.id("pipeline/smoke"))
            .withVertexShader(AirDefense.id("core/smoke"))
            .withFragmentShader(AirDefense.id("core/smoke"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .build());
    /** Fully lit, for the flame-lit start. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private final float startSize, endSize, grey, maxAlpha, growth;
    private final boolean flip;
    /** Ticks it glows in the flame's light before it is plain smoke. */
    private final int warm;
    private float spin;

    private ParticleSmoke(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                          float startSize, float endSize, int life, int warm, float grey, float alpha) {
        super(level, x, y, z, Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES)
                .getSprite(SPRITES[level.getRandom().nextInt(SPRITES.length)]));
        xd = vx;
        yd = vy;
        zd = vz;
        lifetime = life;
        hasPhysics = false;
        friction = 0.9f;
        gravity = -0.01f; // hot gas rises a little
        this.startSize = startSize;
        this.endSize = endSize;
        this.warm = warm;
        this.grey = grey;
        this.maxAlpha = alpha;
        quadSize = startSize;
        // a slight tilt only: the sprite's light comes from above
        roll = oRoll = (random.nextFloat() - 0.5f) * 0.5f;
        spin = (random.nextFloat() - 0.5f) * 0.006f;
        flip = random.nextBoolean();
        growth = 2f + random.nextFloat() * 2f;
        update();
    }

    /**
     * startSize/endSize: quad half-size in blocks when born / when gone; warm: flame-lit ticks; grey: brightness
     * of the cold smoke (0.9 white exhaust .. 0.4 dark); alpha: opacity of the densest part.
     */
    public static void spawn(Level level, double x, double y, double z, double vx, double vy, double vz,
                             float startSize, float endSize, int life, int warm, float grey, float alpha) {
        if (!(level instanceof ClientLevel client)) return;
        Minecraft.getInstance().particleEngine.add(new ParticleSmoke(client, x, y, z, vx, vy, vz, startSize, endSize, life, warm, grey, alpha));
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        roll += spin;
        spin *= 0.99f;
        // turbulence: each puff wanders on its own, so a trail is no row of identical beads
        xd += random.nextGaussian() * 0.004;
        yd += random.nextGaussian() * 0.003;
        zd += random.nextGaussian() * 0.004;
        // ponytail: one wind for the whole world, turning with the day; a weather system could drive it
        double a = level.getGameTime() / 24000.0 * 2.4;
        xd += Math.cos(a) * 0.0015;
        zd += Math.sin(a) * 0.0015;
        update();
    }

    private void update() {
        float t = (float) age / lifetime;
        quadSize = Mth.lerp(1 - (float) Math.pow(1 - t, growth), startSize, endSize);
        float k = warm > 0 ? Math.max(0f, 1f - (float) age / warm) : 0f;
        k *= k;
        rCol = Mth.lerp(k, grey, 1.0f);
        gCol = Mth.lerp(k, grey, 0.58f);
        bCol = Mth.lerp(k, grey * 1.02f, 0.22f);
        float in = Math.min(1f, (age + 1) / 3f);
        alpha = maxAlpha * in * (float) Math.pow(1 - t, 1.4);
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return age < warm ? FULL_BRIGHT : super.getLightCoords(partialTick);
    }

    @Override
    protected float getU0() {
        return flip ? super.getU1() : super.getU0();
    }

    @Override
    protected float getU1() {
        return flip ? super.getU0() : super.getU1();
    }

    /** Under a shaderpack our pipeline never reaches the picture: NTM's translucent layer is Iris-aware. */
    @Override
    protected Layer getLayer() {
        return ShaderPacks.inUse() ? ParticleLayers.TRANSLUCENT : LAYER;
    }
}

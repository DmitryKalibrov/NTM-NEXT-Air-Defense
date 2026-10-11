package com.hbm.airdefense;

import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** A recipe of the defence assembly hall: same JSON as NTM's assembly machine, own recipe type. */
public class DefenseRecipe extends GenericRecipe {

    public static final MapCodec<DefenseRecipe> MAP_CODEC = GenericRecipe.mapCodec(DefenseRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, DefenseRecipe> STREAM_CODEC = GenericRecipe.streamCodec(DefenseRecipe::new);
    public static final RecipeSerializer<DefenseRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public DefenseRecipe(String name) {
        super(name);
    }

    @Override
    public GenericRecipes<?, ?> table() {
        return DefenseAssemblerRecipes.INSTANCE;
    }
}

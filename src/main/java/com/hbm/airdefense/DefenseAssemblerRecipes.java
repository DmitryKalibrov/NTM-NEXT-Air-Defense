package com.hbm.airdefense;

import com.hbm.inventory.recipes.loader.SimpleGenericRecipes;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Recipe type "ntm_air_defense:defense_assembler", registered and synced through NTM's addon hook. */
public class DefenseAssemblerRecipes extends SimpleGenericRecipes<DefenseRecipe> {

    public static final DefenseAssemblerRecipes INSTANCE = new DefenseAssemblerRecipes();

    @Override
    protected String registryName() {
        return AirDefense.MOD_ID + ":defense_assembler";
    }

    @Override
    protected RecipeSerializer<DefenseRecipe> serializer() {
        return DefenseRecipe.SERIALIZER;
    }
}

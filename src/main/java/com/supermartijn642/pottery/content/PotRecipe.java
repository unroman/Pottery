package com.supermartijn642.pottery.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.PotDecorations;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Created 01/12/2023 by SuperMartijn642
 */
public class PotRecipe extends ShapedRecipe {

    public static final Serializer SERIALIZER = new Serializer();

    private final Ingredient dyeIngredient;
    private final int[] sherdIndices;

    public PotRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack output, boolean showNotification, Ingredient dyeIngredient, int[] sherdIndices){
        super(group, category, pattern, output, showNotification);
        this.dyeIngredient = dyeIngredient;
        this.sherdIndices = sherdIndices;
    }

    public Ingredient getDyeIngredient(){
        return this.dyeIngredient;
    }

    @Override
    public boolean matches(CraftingInput input, Level level){
        return this.findRecipeDecorations(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider){
        ItemStack stack = super.assemble(input, provider);

        // Add the decorations
        PotDecorations decorations = this.findRecipeDecorations(input);
        Objects.requireNonNull(decorations);
        if(!decorations.equals(PotDecorations.EMPTY))
            stack.set(DataComponents.POT_DECORATIONS, decorations);

        return stack;
    }

    @Override
    public RecipeSerializer<?> getSerializer(){
        return SERIALIZER;
    }

    private PotDecorations findRecipeDecorations(CraftingInput input){
        if(!this.canCraftInDimensions(input.width(), input.height()))
            return null;

        for(int x = 0; x <= input.width() - this.getWidth(); ++x){
            for(int y = 0; y <= input.height() - this.getHeight(); ++y){
                PotDecorations decorations = this.matchesSubGrid(input, x, y, true);
                if(decorations == null)
                    decorations = this.matchesSubGrid(input, x, y, false);
                if(decorations != null)
                    return decorations;
            }
        }
        return null;
    }

    private PotDecorations matchesSubGrid(CraftingInput input, int startX, int startY, boolean mirrored){
        boolean foundDye = false;
        for(int x = 0; x < input.width(); ++x){
            for(int y = 0; y < input.height(); ++y){
                ItemStack stack = input.getItem(x + y * input.width());
                if(this.dyeIngredient != null && this.dyeIngredient.test(stack)){
                    if(foundDye)
                        return null;
                    foundDye = true;
                    continue;
                }

                int relativeX = x - startX;
                int relativeY = y - startY;
                if(relativeX >= 0 && relativeY >= 0 && relativeX < this.getWidth() && relativeY < this.getHeight()){
                    Ingredient ingredient = this.getIngredients().get(mirrored ?
                        this.getWidth() - relativeX - 1 + relativeY * this.getWidth() :
                        relativeX + relativeY * this.getWidth()
                    );
                    if(!ingredient.test(stack))
                        return null;
                }else if(!Ingredient.EMPTY.test(stack))
                    return null;
            }
        }

        if(this.dyeIngredient != null && !foundDye)
            return null;

        Item front = input.getItem(startX + this.sherdIndices[0] % this.getWidth() + (startY + this.sherdIndices[0] / this.getWidth()) * input.width()).getItem();
        Item left = input.getItem(startX + this.sherdIndices[1] % this.getWidth() + (startY + this.sherdIndices[1] / this.getWidth()) * input.width()).getItem();
        Item right = input.getItem(startX + this.sherdIndices[2] % this.getWidth() + (startY + this.sherdIndices[2] / this.getWidth()) * input.width()).getItem();
        Item back = input.getItem(startX + this.sherdIndices[3] % this.getWidth() + (startY + this.sherdIndices[3] / this.getWidth()) * input.width()).getItem();
        return new PotDecorations(back, left, right, front);
    }

    public static class Serializer implements RecipeSerializer<PotRecipe> {

        private static final Function<Integer,DataResult<Integer>> GEQUAL_TO_ZERO = integer -> integer < 0 ? DataResult.error(() -> "Value '" + integer + "' is less than 0!") : DataResult.success(integer);
        private static final MapCodec<PotRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ShapedRecipe.Serializer.CODEC.fieldOf("recipe").forGetter(recipe -> null),
            Ingredient.CODEC_NONEMPTY.optionalFieldOf("dye_ingredient").forGetter(recipe -> Optional.of(recipe.dyeIngredient)),
            Codec.INT.flatXmap(GEQUAL_TO_ZERO, GEQUAL_TO_ZERO).listOf().fieldOf("sherds").forGetter(recipe -> IntStream.of(recipe.sherdIndices).boxed().toList())
        ).apply(instance, (shapedRecipe, dyeIngredient, sherdIndices) -> new PotRecipe(
            shapedRecipe.getGroup(),
            shapedRecipe.category(),
            shapedRecipe.pattern,
            shapedRecipe.getResultItem(null),
            shapedRecipe.showNotification(),
            dyeIngredient.orElse(null),
            sherdIndices.stream().mapToInt(i -> i).toArray()
        )));
        private static final StreamCodec<RegistryFriendlyByteBuf,PotRecipe> STREAM_CODEC = StreamCodec.of(Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<PotRecipe> codec(){
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf,PotRecipe> streamCodec(){
            return STREAM_CODEC;
        }

        public static PotRecipe fromNetwork(RegistryFriendlyByteBuf buffer){
            ShapedRecipe shapedRecipe = RecipeSerializer.SHAPED_RECIPE.streamCodec().decode(buffer);
            Ingredient dyeIngredient = buffer.readBoolean() ? Ingredient.CONTENTS_STREAM_CODEC.decode(buffer) : null;
            int[] sherdIndices = buffer.readVarIntArray(4);
            if(sherdIndices.length != 4)
                throw new IllegalArgumentException();
            return new PotRecipe(
                shapedRecipe.getGroup(),
                shapedRecipe.category(),
                shapedRecipe.pattern,
                shapedRecipe.getResultItem(null),
                shapedRecipe.showNotification(),
                dyeIngredient,
                sherdIndices
            );
        }

        public static void toNetwork(RegistryFriendlyByteBuf buffer, PotRecipe recipe){
            RecipeSerializer.SHAPED_RECIPE.streamCodec().encode(buffer, recipe);
            buffer.writeBoolean(recipe.dyeIngredient != null);
            if(recipe.dyeIngredient != null)
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.dyeIngredient);
            buffer.writeVarIntArray(recipe.sherdIndices);
        }
    }
}

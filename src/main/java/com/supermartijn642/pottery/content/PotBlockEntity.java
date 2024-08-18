package com.supermartijn642.pottery.content;

import com.supermartijn642.core.CommonUtils;
import com.supermartijn642.core.block.BaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.ticks.ContainerSingleItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Created 27/11/2023 by SuperMartijn642
 */
public class PotBlockEntity extends BaseBlockEntity implements RandomizableContainer, ContainerSingleItem.BlockContainerSingleItem {

    private PotDecorations decorations = PotDecorations.EMPTY;
    private ItemStack items = ItemStack.EMPTY;
    public long wobbleStartedAtTick;
    @Nullable
    public DecoratedPotBlockEntity.WobbleStyle lastWobbleStyle;
    @Nullable
    protected ResourceKey<LootTable> lootTable;
    protected long lootTableSeed;

    public PotBlockEntity(PotType type, BlockPos pos, BlockState state){
        super(type.getBlockEntityType(), pos, state);
    }

    public ItemStack itemFromDecorations(){
        ItemStack stack = new ItemStack(this.getBlockState().getBlock());
        stack.applyComponents(this.collectComponents());
        return stack;
    }

    public PotDecorations getDecorations(){
        return this.decorations;
    }

    public void updateDecorations(PotDecorations decorations){
        this.decorations = decorations;
        this.dataChanged();
    }

    public Direction getFacing(){
        return this.getBlockState().getValue(PotBlock.HORIZONTAL_FACING);
    }

    @Nullable
    @Override
    public ResourceKey<LootTable> getLootTable(){
        return this.lootTable;
    }

    @Override
    public void setLootTable(ResourceKey<LootTable> lootTable){
        this.lootTable = lootTable;
    }

    @Override
    public long getLootTableSeed(){
        return this.lootTableSeed;
    }

    @Override
    public void setLootTableSeed(long lootTableSeed){
        this.lootTableSeed = lootTableSeed;
    }

    @Override
    public ItemStack getTheItem(){
        this.unpackLootTable(null);
        return this.items;
    }

    @Override
    public ItemStack splitTheItem(int count){
        this.unpackLootTable(null);
        ItemStack split = this.items.split(count);
        if(this.items.isEmpty())
            this.items = ItemStack.EMPTY;
        return split;
    }

    @Override
    public void setTheItem(ItemStack stack){
        this.unpackLootTable(null);
        this.items = stack;
    }

    @Override
    public BlockEntity getContainerBlockEntity(){
        return this;
    }

    public void wobble(DecoratedPotBlockEntity.WobbleStyle style){
        if(this.level == null || this.level.isClientSide)
            return;
        this.level.blockEvent(this.getBlockPos(), this.getBlockState().getBlock(), 1, style.ordinal());
    }

    @Override
    public boolean triggerEvent(int identifier, int data){
        if(this.level != null && identifier == 1 && data >= 0 && data < DecoratedPotBlockEntity.WobbleStyle.values().length){
            this.wobbleStartedAtTick = this.level.getGameTime();
            this.lastWobbleStyle = DecoratedPotBlockEntity.WobbleStyle.values()[data];
            return true;
        }
        return super.triggerEvent(identifier, data);
    }

    @Override
    protected CompoundTag writeData(){
        CompoundTag data = new CompoundTag();
        if(this.decorations.equals(PotDecorations.EMPTY))
            data.putBoolean("decorationsEmpty", true);
        else
            this.decorations.save(data);
        if(!this.trySaveLootTable(data) && !this.items.isEmpty())
            data.put("items", this.items.saveOptional(this.level.registryAccess()));
        return data;
    }

    @Override
    protected void saveAdditional(CompoundTag compound, HolderLookup.Provider provider){
        super.saveAdditional(compound, provider);
        this.decorations.save(compound);
    }

    @Override
    protected CompoundTag writeItemStackData(){
        CompoundTag data = this.writeData();
        if(data != null)
            data.remove("decorationsEmpty");
        return data;
    }

    @Override
    protected void readData(CompoundTag data){
        this.decorations = PotDecorations.load(data);
        if(!this.tryLoadLootTable(data))
            this.items = data.contains("items", Tag.TAG_COMPOUND) ? ItemStack.parseOptional(CommonUtils.getRegistryAccess(), data.getCompound("items")) : ItemStack.EMPTY;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder){
        super.collectImplicitComponents(builder);
        builder.set(DataComponents.POT_DECORATIONS, this.decorations);
        builder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(this.items)));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput dataComponentInput){
        super.applyImplicitComponents(dataComponentInput);
        this.decorations = dataComponentInput.getOrDefault(DataComponents.POT_DECORATIONS, PotDecorations.EMPTY);
        this.items = dataComponentInput.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne();
    }

    @Override
    public void removeComponentsFromTag(CompoundTag compoundTag){
        super.removeComponentsFromTag(compoundTag);
        compoundTag.remove("sherds");
        compoundTag.remove("item");
    }
}

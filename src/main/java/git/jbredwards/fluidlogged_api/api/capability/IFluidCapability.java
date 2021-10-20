package git.jbredwards.fluidlogged_api.api.capability;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * allows chunks to store fluid states
 * @author jbred
 *
 */
public interface IFluidCapability
{
    @Nonnull Map<BlockPos, IBlockState> getFluidStateMap();
    //don't call directly, instead use FluidloggedUtils#getFluidState please!
    @Nullable IBlockState getFluidState(@Nonnull BlockPos pos);
    //don't call directly, instead use FluidloggedUtils#setFluidState please!
    void setFluidState(@Nonnull BlockPos pos, @Nullable IBlockState fluidState);
    //get this from a capability provider
    @SuppressWarnings("ConstantConditions")
    @Nullable
    static IFluidCapability get(@Nullable ICapabilityProvider p) {
        return p != null && p.hasCapability(Impl.CAPABILITY, null) ? p.getCapability(Impl.CAPABILITY, null) : null;
    }

    //default implementation
    class Impl implements IFluidCapability, Capability.IStorage<IFluidCapability>, ICapabilitySerializable<NBTBase>
    {
        @CapabilityInject(IFluidCapability.class)
        public static final Capability<IFluidCapability> CAPABILITY = null;
        @Nonnull
        protected final Map<BlockPos, IBlockState> fluidStates = new HashMap<>();

        @Nonnull
        @Override
        public Map<BlockPos, IBlockState> getFluidStateMap() { return fluidStates; }

        @Nullable
        @Override
        public IBlockState getFluidState(@Nonnull BlockPos pos) { return fluidStates.get(pos); }

        @Override
        public void setFluidState(@Nonnull BlockPos pos, @Nullable IBlockState fluidState) {
            if(fluidState == null) fluidStates.remove(pos);
            else fluidStates.put(pos, fluidState);
        }

        @Nullable
        @Override
        public NBTBase writeNBT(@Nullable Capability<IFluidCapability> capability, @Nonnull IFluidCapability instance, @Nullable EnumFacing side) {
            final NBTTagList list = new NBTTagList();
            for(Map.Entry<BlockPos, IBlockState> entry : instance.getFluidStateMap().entrySet()) {
                NBTTagCompound nbt = new NBTTagCompound();

                IBlockState state = entry.getValue();
                nbt.setString("id", Objects.requireNonNull(state.getBlock().getRegistryName()).toString());
                nbt.setInteger("meta", state.getBlock().getMetaFromState(state));
                nbt.setLong("pos", entry.getKey().toLong());

                list.appendTag(nbt);
            }

            return list;
        }

        @SuppressWarnings("deprecation")
        @Override
        public void readNBT(@Nullable Capability<IFluidCapability> capability, @Nonnull IFluidCapability instance, @Nullable EnumFacing side, @Nullable NBTBase nbtIn) {
            if(nbtIn instanceof NBTTagList) {
                for(NBTBase tag : (NBTTagList)nbtIn) {
                    if(tag instanceof NBTTagCompound) {
                        NBTTagCompound nbt = (NBTTagCompound)tag;
                        Block block = Block.getBlockFromName(nbt.getString("id"));
                        if(block != null) instance.setFluidState(BlockPos.fromLong(nbt.getLong("pos")), block.getStateFromMeta(nbt.getInteger("meta")));
                    }
                }
            }
        }

        //===================
        //CAPABILITY PROVIDER
        //===================

        @SuppressWarnings("ConstantConditions")
        @Override
        public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
            return capability.equals(CAPABILITY);
        }

        @SuppressWarnings("ConstantConditions")
        @Nullable
        @Override
        public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
            return hasCapability(capability, facing) ? CAPABILITY.cast(this) : null;
        }

        @SuppressWarnings("ConstantConditions")
        @Nullable
        @Override
        public NBTBase serializeNBT() { return CAPABILITY.writeNBT(this, null); }

        @SuppressWarnings("ConstantConditions")
        @Override
        public void deserializeNBT(@Nullable NBTBase nbt) { CAPABILITY.readNBT(this, null, nbt); }
    }
}
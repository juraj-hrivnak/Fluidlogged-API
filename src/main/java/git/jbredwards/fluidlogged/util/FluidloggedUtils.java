package git.jbredwards.fluidlogged.util;

import git.jbredwards.fluidlogged.common.block.BlockFluidloggedTE;
import git.jbredwards.fluidlogged.common.block.IFluidloggable;
import git.jbredwards.fluidlogged.common.block.TileEntityFluidlogged;
import git.jbredwards.fluidlogged.common.event.FluidloggedEvent;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * helpful functions
 * @author jbred
 *
 */
public enum FluidloggedUtils
{
    ;

    //returns the stored fluidlogged block, null if there is none
    @Nullable
    public static IBlockState getStored(@Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        final @Nullable TileEntity te = world.getTileEntity(pos);

        if(!(te instanceof TileEntityFluidlogged)) return null;
        else return ((TileEntityFluidlogged)te).getStored();
    }

    //should be used instead of world.getBlockState wherever possible
    public static IBlockState getStoredOrReal(@Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        final IBlockState here = world.getBlockState(pos);

        if(!(here.getBlock() instanceof BlockFluidloggedTE)) return here;
        else return ((BlockFluidloggedTE)here.getBlock()).getStored(world, pos);
    }

    //if the block is fluidlogged, replaces the stored block, else does world.setBlockState()
    //this should be used instead of world.setBlockState wherever possible
    public static void setStoredOrReal(@Nonnull World world, @Nonnull BlockPos pos, @Nullable IBlockState here, @Nullable IBlockState state, boolean notify) {
        final @Nullable IBlockState stored = getStored(world, pos);
        if(state == null) state = Blocks.AIR.getDefaultState();

        //block here isn't fluidlogged
        if(stored == null) {
            world.setBlockState(pos, state, notify ? 3 : 0);
            return;
        }

        //if the here state is null
        if(here == null) here = world.getBlockState(pos);
        if(here.getBlock() instanceof BlockFluidloggedTE) {
            //if the state to be set is air and the block here is fluidlogged, set to the fluid
            if(state.getBlock() == Blocks.AIR) {
                world.setBlockState(pos, ((BlockFluidloggedTE)here.getBlock()).fluid.getBlock().getDefaultState(), notify ? 3 : 0);
                return;
            }

            //if the state to be set can't be fluidlogged
            if(!isStateFluidloggable(state)) {
                world.setBlockState(pos, state, notify ? 3 : 0);
                return;
            }

            ((BlockFluidloggedTE)here.getBlock()).setStored(world, pos, state, notify);
        }
        else world.setBlockState(pos, state, notify ? 3 : 0);
    }

    //returns true if the state can be fluidlogged in general
    public static boolean isStateFluidloggable(@Nullable IBlockState state) {
        return isStateFluidloggable(state, null);
    }

    //returns true if the state can be fluidlogged with the given fluid
    public static boolean isStateFluidloggable(@Nullable IBlockState state, @Nullable Fluid fluid) {
        if(state == null) return false;

        final FluidloggedEvent.CheckFluidloggable event = new FluidloggedEvent.CheckFluidloggable(state, fluid);

        //event did stuff
        if(MinecraftForge.EVENT_BUS.post(event)) return false;
        else if(event.getResult() == Event.Result.DENY) return false;
        else if(event.getResult() == Event.Result.ALLOW) return true;
        //default
        else {
            final Block block = state.getBlock();

            //modded
            if(block instanceof IFluidloggable) return ((IFluidloggable)block).isFluidValid(state, fluid);
            //unsupported tile entity blocks
            else if(block instanceof ITileEntityProvider) return false;
            //normal vanilla blocks
            else return (block instanceof BlockSlab && !((BlockSlab)block).isDouble())
                        || block instanceof BlockStairs
                        || block instanceof BlockPane
                        || block instanceof BlockFence
                        || block instanceof BlockEndRod
                        || block instanceof BlockWall
                        || block instanceof BlockBarrier
                        || block instanceof BlockLeaves
                        || block instanceof BlockFenceGate
                        || block instanceof BlockTrapDoor;
        }
    }

    //tries to fluidlog the block here with the fluid
    //returns true if the block was successfully fluidlogged
    public static boolean tryFluidlogBlock(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull Fluid fluid, boolean ignoreVaporize) {
        return tryFluidlogBlock(world, pos, world.getBlockState(pos), fluid, ignoreVaporize);
    }

    //tries to fluidlog the block here with the fluid
    //returns true if the block was successfully fluidlogged
    public static boolean tryFluidlogBlock(@Nonnull World world, @Nonnull BlockPos pos, @Nullable IBlockState here, @Nonnull Fluid fluid, boolean ignoreVaporize) {
        if(isStateFluidloggable(here, fluid)) {
            final IBlockState stored = (here.getBlock() instanceof IFluidloggable ? ((IFluidloggable)here.getBlock()).getFluidloggedState(world, pos, here) : here);
            final @Nullable BlockFluidloggedTE block = FluidloggedConstants.FLUIDLOGGED_TE_LOOKUP.get(fluid);

            if(block != null) {
                final FluidloggedEvent.Fluidlog event = new FluidloggedEvent.Fluidlog(world, pos, here, stored, block, new TileEntityFluidlogged(), ignoreVaporize);

                //event did stuff
                if(MinecraftForge.EVENT_BUS.post(event)) return false;
                else if(event.getResult() == Event.Result.DENY) return false;
                else if(event.getResult() == Event.Result.ALLOW) return true;
                    //default
                else {
                    //vaporizes water if in the nether
                    if(!event.ignoreVaporize && world.provider.doesWaterVaporize() && fluid.doesVaporize(new FluidStack(fluid, Fluid.BUCKET_VOLUME))) {
                        for(int i = 0; i < 8; ++i) world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX() + Math.random(), pos.getY() + Math.random(), pos.getZ() + Math.random(), 0, 0, 0);
                        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 2.6f + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.8f);

                        return false;
                    }

                    event.te.setStored(event.stored, false);

                    world.setBlockState(pos, event.block.getDefaultState());
                    world.setTileEntity(pos, event.te);

                    return true;
                }
            }
        }

        //default
        return false;
    }

    //tries to un-fluidlog the block here
    //returns true if the block was successfully un-fluidlogged
    public static boolean tryUnfluidlogBlock(@Nonnull World world, @Nonnull BlockPos pos, @Nullable IBlockState here) {
        return tryUnfluidlogBlock(world, pos, here, getStored(world, pos));
    }

    public static boolean tryUnfluidlogBlock(@Nonnull World world, @Nonnull BlockPos pos, @Nullable IBlockState here, @Nullable IBlockState stored) {
        if(stored == null) return false;
        if(here == null) here = world.getBlockState(pos);

        final IBlockState toCreate = (stored.getBlock() instanceof IFluidloggable ? ((IFluidloggable)stored.getBlock()).getNonFluidloggedState(world, pos, stored) : stored);
        final FluidloggedEvent.UnFluidlog event = new FluidloggedEvent.UnFluidlog(world, pos, here, stored, toCreate);

        //event did stuff
        if(MinecraftForge.EVENT_BUS.post(event)) return false;
        else if(event.getResult() == Event.Result.DENY) return false;
        else if(event.getResult() == Event.Result.ALLOW) return true;
        //default
        else {
            world.setBlockState(pos, event.toCreate);
            return true;
        }
    }

    //fills the bucket stack with the fluid
    public static ItemStack getFilledBucket(@Nonnull ItemStack empty, @Nonnull Fluid fluid) {
        empty = ItemHandlerHelper.copyStackWithSize(empty, 1);
        final @Nullable IFluidHandlerItem handler = FluidUtil.getFluidHandler(empty);

        if(handler != null) {
            handler.fill(new FluidStack(fluid, Fluid.BUCKET_VOLUME), true);
            return handler.getContainer();
        }

        //not a bucket
        return empty;
    }

    //empties the bucket stack
    public static ItemStack getEmptyBucket(@Nonnull ItemStack filled) {
        filled = ItemHandlerHelper.copyStackWithSize(filled, 1);
        final @Nullable IFluidHandlerItem handler = FluidUtil.getFluidHandler(filled);

        if(handler != null) {
            handler.drain(Fluid.BUCKET_VOLUME, true);
            return handler.getContainer();
        }

        //not a bucket
        return filled;
    }
}

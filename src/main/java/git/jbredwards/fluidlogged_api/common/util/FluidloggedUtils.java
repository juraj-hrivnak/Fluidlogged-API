package git.jbredwards.fluidlogged_api.common.util;

import git.jbredwards.fluidlogged_api.Main;
import git.jbredwards.fluidlogged_api.asm.replacements.BlockLiquidBase;
import git.jbredwards.fluidlogged_api.common.block.IFluidloggable;
import git.jbredwards.fluidlogged_api.common.block.IFluidloggableFluid;
import git.jbredwards.fluidlogged_api.common.config.FluidloggedConfig;
import git.jbredwards.fluidlogged_api.common.event.FluidloggedEvent;
import git.jbredwards.fluidlogged_api.common.network.FluidStateMessage;
import net.minecraft.block.*;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

/**
 * helpful functions
 * @author jbred
 *
 */
public enum FluidloggedUtils
{
    ;

    //forms the FluidState from the IBlockState here if it's a fluid block,
    //if not a fluid block, return FluidState stored via the capability
    @Nonnull
    public static FluidState getFluidState(@Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        return getFluidState(world, pos, world.getBlockState(pos));
    }

    //same as above method, but takes in the here state rather than getting it from the world
    //(useful for avoiding unnecessary lookups)
    @Nonnull
    public static FluidState getFluidState(@Nullable IBlockAccess world, @Nonnull BlockPos pos, @Nonnull IBlockState here) {
        final @Nullable Fluid fluidHere = getFluidFromState(here);
        return fluidHere == null ? FluidState.get(world, pos) : new FluidState(fluidHere, here);
    }

    //tries to get the fluid at the pos (prioritizing ones physically in the world, then the fluid capability),
    //if none return world#getBlockState
    @Nonnull
    public static IBlockState getFluidOrReal(@Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        return getFluidOrReal(world, pos, world.getBlockState(pos));
    }

    //same as above method, but takes in the here state rather than getting it from the world
    //(useful for avoiding unnecessary lookups)
    @Nonnull
    public static IBlockState getFluidOrReal(@Nullable IBlockAccess world, @Nonnull BlockPos pos, @Nonnull IBlockState here) {
        return getFluidFromState(here) != null ? here : Optional.ofNullable(FluidState.get(world, pos).getState()).orElse(here);
    }

    //convenience method
    public static boolean setFluidState(@Nonnull World world, @Nonnull BlockPos pos, @Nullable IBlockState here, @Nonnull FluidState fluidState, boolean checkVaporize) {
        return setFluidState(world, pos, here, fluidState, checkVaporize, Constants.BlockFlags.DEFAULT_AND_RERENDER);
    }

    public static boolean setFluidState(@Nonnull World world, @Nonnull BlockPos pos, @Nullable IBlockState here, @Nonnull FluidState fluidState, boolean checkVaporize, int flags) {
        if(world.isOutsideBuildHeight(pos) || world.getWorldType() == WorldType.DEBUG_ALL_BLOCK_STATES) return false;

        final Chunk chunk = world.getChunkFromBlockCoords(pos);
        if(here == null) here = chunk.getBlockState(pos);

        final FluidloggedEvent event = new FluidloggedEvent(world, chunk, pos, here, fluidState, checkVaporize, flags);
        //event did stuff
        if(MinecraftForge.EVENT_BUS.post(event) && event.getResult() != Event.Result.DEFAULT) return event.getResult() == Event.Result.ALLOW;
        //default
        else {
            //if the world is to warm for the fluid, vaporize it
            final @Nullable Fluid fluid = event.fluidState.getFluid();
            if(event.checkVaporize && fluid != null && world.provider.doesWaterVaporize() && fluid.doesVaporize(new FluidStack(fluid, Fluid.BUCKET_VOLUME))) {
                fluid.vaporize(null, world, pos, new FluidStack(fluid, Fluid.BUCKET_VOLUME));
                return true;
            }

            //check for IFluidloggable
            if(here.getBlock() instanceof IFluidloggable) {
                final EnumActionResult result = ((IFluidloggable)here.getBlock()).onFluidChange(world, pos, here, event.fluidState, event.flags);
                if(result != EnumActionResult.PASS) return result == EnumActionResult.SUCCESS;
            }

            //moved to separate function, as to allow easy calling by IFluidloggable instances that use IFluidloggable#onFluidChange
            FluidloggedUtils.setFluidState_Internal(world, chunk, here, pos, event.fluidState, event.flags);

            //default
            return event.getResult() != Event.Result.DENY;
        }
    }

    //if you're not an event instance or an IFluidloggable instance, use setFluidState instead!
    //moved to separate function, as to allow easy calling by IFluidloggable instances that use IFluidloggable#onFluidChange
    public static void setFluidState_Internal(@Nonnull World world, @Nonnull Chunk chunk, @Nonnull IBlockState here, @Nonnull BlockPos pos, @Nonnull FluidState fluidState, int flags) {
        final @Nullable IFluidStateCapability cap = IFluidStateCapability.get(chunk);
        if(cap == null) throw new NullPointerException("There was a critical internal error involving the Fluidlogged API mod, notify the mod author!");

        //fix small graphical flicker with blocks placed inside fluids
        if(world.isRemote && !fluidState.isEmpty()) cap.setFluidState(pos, fluidState);

        //only do these on server
        if(!world.isRemote) {
            //send changes to server
            cap.setFluidState(pos, fluidState);

            //send changes to client
            if((flags & Constants.BlockFlags.SEND_TO_CLIENTS) != 0) {
                Main.wrapper.sendToAllAround(new FluidStateMessage(pos, fluidState),
                        new NetworkRegistry.TargetPoint(world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(), 64)
                );
            }

            //update light levels
            world.profiler.startSection("checkLight");
            world.checkLight(pos);
            world.profiler.endSection();

            //post fluid added
            if(!fluidState.isEmpty()) fluidState.getBlock().onBlockAdded(world, pos, fluidState.getState());
        }

        //update world
        if((flags & Constants.BlockFlags.NOTIFY_NEIGHBORS) != 0)
            world.markAndNotifyBlock(pos, chunk, here, here, flags);
    }

    //fork of Main.CommonProxy#getChunk
    @Nullable
    public static Chunk getChunk(@Nullable IBlockAccess world, @Nonnull BlockPos pos) { return Main.proxy.getChunk(world, pos); }

    //fork of IFluidloggable#canFluidFlow
    public static boolean canFluidFlow(@Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nullable Fluid fluid, @Nonnull EnumFacing side) {
        if(state.getBlock() instanceof IFluidloggable) return ((IFluidloggable)state.getBlock()).canFluidFlow(world, pos, state, fluid, side);
        else return state.getBlockFaceShape(world, pos, side) != BlockFaceShape.SOLID;
    }

    //gets the fluid from the state (null if there is no fluid)
    @Nullable
    public static Fluid getFluidFromState(@Nonnull IBlockState fluid) { return getFluidFromBlock(fluid.getBlock()); }

    //fork of IFluidBlock#getFluid
    //note that this mod has any classes that extend BlockLiquid extend BlockLiquidBase instead during runtime
    @Nullable
    public static Fluid getFluidFromBlock(@Nonnull Block fluid) { return (fluid instanceof IFluidBlock) ? ((IFluidBlock)fluid).getFluid() : null; }

    @SuppressWarnings("deprecation")
    public static boolean isFluidFluidloggable(@Nullable Block fluid) {
        //allow vanilla fluid blocks & certain modded ones
        if(fluid instanceof IFluidloggableFluid) return ((IFluidloggableFluid)fluid).isFluidloggableFluid();
        //restrict forge fluids to BlockFluidClassic
        else return fluid instanceof BlockFluidClassic && !fluid.hasTileEntity();
    }

    //same as above method, but also checks for fluid level
    public static boolean isFluidFluidloggable(@Nonnull IBlockState fluid) {
        if(!isFluidFluidloggable(fluid.getBlock())) return false;
        final int level = fluid.getValue(BlockLiquidBase.LEVEL);
        return level == 0 || (level >= 8 && AccessorUtils.canCreateSources(fluid.getBlock()));
    }

    public static boolean isStateFluidloggable(@Nonnull IBlockState state, @Nullable Fluid fluid) {
        //config
        final EnumActionResult result = FluidloggedConfig.isStateFluidloggable(state, fluid);
        if(result != EnumActionResult.PASS) return result == EnumActionResult.SUCCESS;
        //defaults
        else {
            //modded
            final Block block = state.getBlock();
            if(block instanceof IFluidloggable) {
                if(fluid == null) return ((IFluidloggable)block).isFluidloggable(state);
                else              return ((IFluidloggable)block).isFluidValid(state, fluid);
            }
            //vanilla
            else return (block instanceof BlockSlab && !((BlockSlab)block).isDouble())
                    || block instanceof BlockStairs
                    || block instanceof BlockPane
                    || block instanceof BlockFence
                    || block instanceof BlockEndRod
                    || block instanceof BlockWall
                    || block instanceof BlockBarrier
                    || block instanceof BlockLeaves
                    || block instanceof BlockFenceGate
                    || block instanceof BlockTrapDoor
                    || block instanceof BlockRailBase
                    || block instanceof BlockHopper
                    || block instanceof BlockChest
                    || block instanceof BlockEnderChest
                    || block instanceof BlockSkull
                    || block instanceof BlockSign
                    || block instanceof BlockDoor;
        }
    }
}

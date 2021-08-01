package git.jbredwards.fluidlogged_api.asm;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import git.jbredwards.fluidlogged_api.asm.plugin.modded.BFReflector;
import git.jbredwards.fluidlogged_api.asm.plugin.modded.OFReflector;
import git.jbredwards.fluidlogged_api.util.FluidloggedConstants;
import git.jbredwards.fluidlogged_api.common.block.AbstractFluidloggedBlock;
import git.jbredwards.fluidlogged_api.common.block.BlockFluidloggedTE;
import git.jbredwards.fluidlogged_api.common.block.TileEntityFluidlogged;
import git.jbredwards.fluidlogged_api.util.FluidloggedUtils;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static net.minecraft.block.BlockStairs.FACING;
import static net.minecraft.block.BlockStairs.HALF;
import static net.minecraft.util.EnumFacing.*;

/**
 * class exists cause SpongeForge
 * NOTE THAT MOST OF THESE METHODS ARE MEANT TO ONLY BE USED IN CERTAIN CASES,
 * PRIOR TO INTEGRATING THEM TO YOUR OWN MOD, VIEW THE PLUGIN CLASS ASSOCIATED
 * @author jbred
 *
 */
@SuppressWarnings("unused")
public enum ASMHooks
{
    ;

    //BlockFenceGatePlugin
    public static boolean setStoredOrRealSimple(World world, BlockPos pos, IBlockState state, int flags) {
        final TileEntity te = world.getTileEntity(pos);

        //fluidlogged
        if(te instanceof TileEntityFluidlogged) {
            ((TileEntityFluidlogged)te).setStored(state, true);
            return true;
        }

        //default
        return world.setBlockState(pos, state, flags);
    }

    //BlockFluidClassicPlugin
    public static int getQuantaValue(BlockFluidClassic fluid, IBlockState state, int quantaPerBlock) {
        //modded
        if(state.getBlock() instanceof BlockFluidBase && fluid.getFluid().getBlock() instanceof BlockFluidBase) {
            final int level = state.getValue(BlockFluidBase.LEVEL);
            return (((BlockFluidBase)state.getBlock()).getFluid().getBlock() == fluid.getFluid().getBlock()) ? (quantaPerBlock - level) : -1;
        }
        //vanilla
        else if(state.getBlock() instanceof BlockLiquid && fluid.getFluid().getBlock() instanceof BlockLiquid) {
            final int level = state.getValue(BlockLiquid.LEVEL);
            return (state.getMaterial() == fluid.getDefaultState().getMaterial()) ? (quantaPerBlock - level) : -1;
        }
        //default
        else return -1;
    }

    //BlockFluidClassicPlugin
    public static Block isSourceBlock(Block block, BlockFluidClassic obj, IBlockAccess world, BlockPos pos) {
        final boolean flag = FluidloggedUtils.getFluidFromBlock(obj) == FluidloggedUtils.getFluidFromBlock(block);
        return flag ? obj : null;
    }

    //BlockFluidClassicPlugin
    public static int getLargerQuanta(BlockFluidClassic obj, World world, BlockPos pos, int compare, EnumFacing facing, Fluid fluidIn, int quantaPerBlock, int densityDir) {
        final boolean hasVerticalFlow = hasVerticalFlow(world, pos, densityDir, obj) == obj;
        final int quantaValue = obj.getQuantaValue(world, pos);
        int quantaRemaining = (quantaValue > 0 && quantaValue < quantaPerBlock && hasVerticalFlow ? quantaPerBlock : quantaValue);
        if(quantaRemaining <= 0) return compare;

        //new stuff
        final IBlockState state = world.getBlockState(pos);
        if(FluidloggedUtils.getFluidFromBlock(state.getBlock()) == fluidIn) {
            if(state.getBlock() instanceof AbstractFluidloggedBlock && !((AbstractFluidloggedBlock)state.getBlock()).canSideFlow(state, world, pos, facing.getOpposite())) return compare;
        }

        return Math.max(quantaRemaining, compare);
    }

    //BlockSpongePlugin
    public static boolean absorb(BlockSponge sponge, World world, BlockPos posIn) {
        final Queue<Pair<BlockPos, Integer>> queue = Lists.newLinkedList();
        queue.add(Pair.of(posIn, 0));

        int blocksDrained = 0;

        while(!queue.isEmpty()) {
            Pair<BlockPos, Integer> pair = queue.poll();
            BlockPos pos = pair.getLeft();
            int distance = pair.getRight();

            for(EnumFacing facing : EnumFacing.values()) {
                BlockPos offset = pos.offset(facing);
                IBlockState state = world.getBlockState(offset);

                if(state.getMaterial() == Material.WATER) {
                    //custom drain action
                    if(state.getBlock() instanceof AbstractFluidloggedBlock) ((AbstractFluidloggedBlock)state.getBlock()).drain(world, offset, true);
                    else world.setBlockState(offset, Blocks.AIR.getDefaultState());

                    ++blocksDrained;

                    if(distance < 6) queue.add(Pair.of(offset, distance + 1));
                }
            }

            if(blocksDrained > 64) break;
        }

        return blocksDrained > 0;
    }

    //BlockStairsPlugin
    public static BlockStairs.EnumShape getShape(IBlockState state, IBlockAccess world, BlockPos pos) {
        final EnumFacing face = state.getValue(FACING);
        IBlockState neighbor = FluidloggedUtils.getStoredOrReal(world, pos.offset(face));
        //if outer shape
        if(neighbor.getBlock() instanceof BlockStairs && state.getValue(HALF) == neighbor.getValue(HALF)) {
            final EnumFacing neighborFace = neighbor.getValue(FACING);

            if(neighborFace.getAxis() != face.getAxis() && isDifferentStairs(state, world, pos, neighborFace.getOpposite())) {
                if(neighborFace == face.rotateYCCW()) return BlockStairs.EnumShape.OUTER_LEFT;
                else return BlockStairs.EnumShape.OUTER_RIGHT;
            }
        }
        //if inner shape
        neighbor = FluidloggedUtils.getStoredOrReal(world, pos.offset(face.getOpposite()));
        if(neighbor.getBlock() instanceof BlockStairs && state.getValue(HALF) == neighbor.getValue(HALF)) {
            final EnumFacing neighborFace = neighbor.getValue(FACING);

            if(neighborFace.getAxis() != face.getAxis() && isDifferentStairs(state, world, pos, neighborFace)) {
                if(neighborFace == face.rotateYCCW()) return BlockStairs.EnumShape.INNER_LEFT;
                else return BlockStairs.EnumShape.INNER_RIGHT;
            }
        }

        //default return statement
        return BlockStairs.EnumShape.STRAIGHT;
    }

    //BlockStairsPlugin
    public static boolean isDifferentStairs(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing offset) {
        final IBlockState neighbor = FluidloggedUtils.getStoredOrReal(world, pos.offset(offset));
        return !(neighbor.getBlock() instanceof BlockStairs) || state.getValue(FACING) != neighbor.getValue(FACING) || state.getValue(HALF) != neighbor.getValue(HALF);
    }

    //BlockWallPlugin
    public static boolean isAirBlock(IBlockAccess world, BlockPos pos) {
        final IBlockState state = world.getBlockState(pos);
        //fluid
        if(FluidloggedUtils.getFluidFromBlock(state.getBlock()) != null && state.getBlock().isReplaceable(world, pos)) return true;
        //air
        else return state.getBlock().isAir(state, world, pos);
    }

    //FluidPlugin
    @SuppressWarnings({"ConstantConditions", "deprecation"})
    public static void registerFluidloggedBlock(Fluid fluid) {
        if(fluid.getName() != null && FluidRegistry.isFluidRegistered(fluid) && fluid.getBlock() instanceof BlockFluidClassic && !fluid.getBlock().hasTileEntity() && !FluidloggedConstants.FLUIDLOGGED_TE_LOOKUP.containsKey(fluid)) {
            //generates the block
            final BlockFluidloggedTE block = new BlockFluidloggedTE(fluid, fluid.getBlock().getDefaultState().getMaterial(), fluid.getBlock().getDefaultState().getMapColor(null, null));
            final String unlocalizedName = fluid.getUnlocalizedName();
            //registers the block
            FluidloggedConstants.FLUIDLOGGED_TE_LOOKUP.put(fluid, block);
            ForgeRegistries.BLOCKS.register(block.setRegistryName(new ResourceLocation(fluid.getName()).getResourcePath() + "logged_te").setUnlocalizedName(unlocalizedName.substring(unlocalizedName.indexOf('.'))));
        }
    }

    //FluidPlugin
    public static void fluidBlockErrorSpamFix(Logger logger, String message, Block block, String fluidName, Block old) {}

    //RenderChunkPlugin
    public static boolean canRenderInLayer(Block blockIn, IBlockState state, BlockRenderLayer layer, IBlockAccess world, BlockPos pos) {
        //renders fluid rather than the actual block
        if(blockIn instanceof AbstractFluidloggedBlock) {
            final AbstractFluidloggedBlock block = (AbstractFluidloggedBlock)blockIn;
            block.updateQuanta();

            final Block fluidBlock = block.getFluid().getBlock();
            return fluidBlock.canRenderInLayer(fluidBlock.getDefaultState(), layer) && block.shouldFluidRender(state, world, pos);
        }

        //default
        return canRenderInLayerBase(blockIn, state, layer);
    }

    //RenderChunkPlugin
    public static boolean canRenderInLayerOF(Object blockIn, Object ignored, Object[] args, IBlockAccess world, BlockPos pos) {
        //OF version calls above method, as there is no difference in function, but only params
        return canRenderInLayer((Block)blockIn, (IBlockState)args[0], (BlockRenderLayer)args[1], world, pos);
    }

    //runs Block::canRenderInLayer with no special case for fluidlogged blocks
    public static boolean canRenderInLayerBase(Block block, IBlockState state, BlockRenderLayer layer) {
        BFReflector.load();
        //better foliage compat
        if(BFReflector.canRenderBlockInLayer != null) {
            try { return (boolean)BFReflector.canRenderBlockInLayer.invoke(null, block, state, layer); }
            catch (Exception ignored) {}
        }
        //default
        return block.canRenderInLayer(state, layer);
    }

    //RenderChunkPlugin
    public static IBlockState fixFluidState(IBlockState state) {
        //default do nothing
        if(!(state.getBlock() instanceof AbstractFluidloggedBlock)) return state;
        //transfers the properties to the fluid state to make it render right
        else {
            IExtendedBlockState extendedState = (IExtendedBlockState)state;
            IExtendedBlockState fluidState = (IExtendedBlockState)((AbstractFluidloggedBlock)state.getBlock()).getFluid().getBlock().getDefaultState();
            fluidState = applyFrom(fluidState, extendedState, BlockFluidBase.FLOW_DIRECTION, -1000f);
            fluidState = applyFrom(fluidState, extendedState, BlockFluidBase.SIDE_OVERLAYS[0], false);
            fluidState = applyFrom(fluidState, extendedState, BlockFluidBase.SIDE_OVERLAYS[1], false);
            fluidState = applyFrom(fluidState, extendedState, BlockFluidBase.SIDE_OVERLAYS[2], false);
            fluidState = applyFrom(fluidState, extendedState, BlockFluidBase.SIDE_OVERLAYS[3], false);
            fluidState = applyFrom(fluidState, extendedState, BlockFluidBase.LEVEL_CORNERS[0], 8f/9);
            fluidState = applyFrom(fluidState, extendedState, BlockFluidBase.LEVEL_CORNERS[1], 8f/9);
            fluidState = applyFrom(fluidState, extendedState, BlockFluidBase.LEVEL_CORNERS[2], 8f/9);
            fluidState = applyFrom(fluidState, extendedState, BlockFluidBase.LEVEL_CORNERS[3], 8f/9);

            return fluidState;
        }
    }

    //returns the 'apply' state, but with the property value from 'source'
    public static <V> IExtendedBlockState applyFrom(IExtendedBlockState apply, IExtendedBlockState source, IUnlistedProperty<V> property, V defaultValue) {
        final @Nullable V value = source.getValue(property);

        if(value == null) return apply.withProperty(property, defaultValue);
        else              return apply.withProperty(property, value);
    }

    //RenderChunkPlugin
    @SideOnly(Side.CLIENT)
    public static boolean renderBlock(BlockRendererDispatcher renderer, IBlockState state, BlockPos pos, IBlockAccess world, BufferBuilder builder) {
        if(state.getBlock() instanceof AbstractFluidloggedBlock) {
            if(world.getWorldType() != WorldType.DEBUG_ALL_BLOCK_STATES) state = state.getActualState(world, pos);
            state = state.getBlock().getExtendedState(state, world, pos);

            final IBakedModel model = renderer.getModelForState(((AbstractFluidloggedBlock)state.getBlock()).getFluid().getBlock().getDefaultState());
            return renderer.getBlockModelRenderer().renderModel(world, model, fixFluidState(state), pos, builder, true);
        }

        //default
        return renderer.renderBlock(state, pos, world, builder);
    }

    //RenderChunkPlugin
    @SideOnly(Side.CLIENT)
    public static boolean renderBlockBF(BlockRendererDispatcher renderer, IBlockState state, BlockPos pos, IBlockAccess world, BufferBuilder builder, BlockRenderLayer layer) {
        if(state.getBlock() instanceof AbstractFluidloggedBlock) {
            if(world.getWorldType() != WorldType.DEBUG_ALL_BLOCK_STATES) state = state.getActualState(world, pos);
            state = state.getBlock().getExtendedState(state, world, pos);

            final IBakedModel model = renderer.getModelForState(((AbstractFluidloggedBlock)state.getBlock()).getFluid().getBlock().getDefaultState());
            return renderer.getBlockModelRenderer().renderModel(world, model, fixFluidState(state), pos, builder, true);
        }

        //default
        return renderBlockStored(renderer, state, pos, world, builder, layer);
    }

    //runs BlockRendererDispatcher::renderBlock with no special case for fluidlogged blocks
    @SideOnly(Side.CLIENT)
    public static boolean renderBlockStored(BlockRendererDispatcher renderer, IBlockState state, BlockPos pos, IBlockAccess world, BufferBuilder builder, BlockRenderLayer layer) {
        BFReflector.load();
        //better foliage compat
        if(BFReflector.renderWorldBlock != null) {
            try { return (boolean)BFReflector.renderWorldBlock.invoke(null, renderer, state, pos, world, builder, layer); }
            catch(Exception ignored) { }
        }
        //default
        return renderer.renderBlock(state, pos, world, builder);
    }

    //RenderChunkPlugin
    @SideOnly(Side.CLIENT)
    public static void renderFluidloggedBlock(boolean[] array, ChunkCompileTaskGenerator generator, CompiledChunk compiledChunk, IBlockState block, IBlockAccess world, BlockPos pos, BlockPos chunkPos) {
        IBlockState stored = block;
        if(block.getBlock() instanceof BlockFluidloggedTE) {
            stored = Optional.ofNullable(FluidloggedUtils.getStored(world, pos)).orElse(Blocks.BARRIER.getDefaultState());
        }

        if(block.getBlock() instanceof AbstractFluidloggedBlock) {
            final BlockRendererDispatcher renderer = Minecraft.getMinecraft().getBlockRendererDispatcher();
            if(stored.getRenderType() != EnumBlockRenderType.INVISIBLE) {
                for(BlockRenderLayer layer : BlockRenderLayer.values()) {
                    if(canRenderInLayerBase(stored.getBlock(), stored, layer)) {
                        ForgeHooksClient.setRenderLayer(layer);
                        BufferBuilder buffer = generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(layer);

                        if(!compiledChunk.isLayerStarted(layer)) {
                            compiledChunk.setLayerStarted(layer);
                            buffer.begin(7, DefaultVertexFormats.BLOCK);
                            buffer.setTranslation(-chunkPos.getX(), -chunkPos.getY(), -chunkPos.getZ());
                        }

                        array[layer.ordinal()] |= renderBlockStored(renderer, stored, pos, world, buffer, layer);
                    }
                }

                ForgeHooksClient.setRenderLayer(null);
            }
        }
    }

    //RenderChunkPlugin
    @SideOnly(Side.CLIENT)
    public static void renderFluidloggedBlockOF(boolean[] array, RenderChunk renderChunk, ChunkCompileTaskGenerator generator, CompiledChunk compiledChunk, IBlockState block, IBlockAccess chunkCacheOF, BlockPos pos, BlockPos chunkPos) {
        OFReflector.load();

        IBlockState stored = block;
        if(block.getBlock() instanceof BlockFluidloggedTE) {
            stored = Optional.ofNullable(FluidloggedUtils.getStored(chunkCacheOF, pos)).orElse(Blocks.BARRIER.getDefaultState());
        }

        if(block.getBlock() instanceof AbstractFluidloggedBlock) {
            final BlockRendererDispatcher renderer = Minecraft.getMinecraft().getBlockRendererDispatcher();
            if(stored.getRenderType() != EnumBlockRenderType.INVISIBLE) {
                for(BlockRenderLayer layer : BlockRenderLayer.values()) {
                    if(canRenderInLayerBase(stored.getBlock(), stored, layer)) {
                        try {
                            ForgeHooksClient.setRenderLayer(layer);

                            BufferBuilder buffer = generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(layer);
                            Objects.requireNonNull(OFReflector.setBlockLayer).invoke(buffer, layer);

                            Object renderEnv = Objects.requireNonNull(OFReflector.getRenderEnv).invoke(buffer, stored, pos);
                            Objects.requireNonNull(OFReflector.setRegionRenderCacheBuilder).invoke(renderEnv, generator.getRegionRenderCacheBuilder());

                            if(!compiledChunk.isLayerStarted(layer)) {
                                compiledChunk.setLayerStarted(layer);
                                buffer.begin(7, DefaultVertexFormats.BLOCK);
                                buffer.setTranslation(-chunkPos.getX(), -chunkPos.getY(), -chunkPos.getZ());
                            }

                            array[layer.ordinal()] |= renderBlockStored(renderer, stored, pos, chunkCacheOF, buffer, layer);

                            //post shader stuff
                            if((boolean)Objects.requireNonNull(OFReflector.isOverlaysRendered).invoke(renderEnv)) {
                                Objects.requireNonNull(OFReflector.postRenderOverlays).invoke(renderChunk, generator.getRegionRenderCacheBuilder(), compiledChunk, array);
                                Objects.requireNonNull(OFReflector.setOverlaysRendered).invoke(renderEnv, false);
                            }
                        }

                        //shouldn't catch, but if it does, do nothing
                        catch(Exception e) {
                            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(TextFormatting.RED + e.toString()));
                        }
                    }
                }

                ForgeHooksClient.setRenderLayer(null);
            }
        }
    }

    //BlockLiquidPlugin
    public static boolean getFlow(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing facing, BlockLiquid obj) {
        final Fluid fluidIn = FluidloggedUtils.getFluidFromBlock(obj);
        final @Nullable Fluid fluid = FluidloggedUtils.getFluidFromBlock(state.getBlock());
        //fluidlogged block
        if(fluid == fluidIn && state.getBlock() instanceof AbstractFluidloggedBlock) {
            if(!((AbstractFluidloggedBlock)state.getBlock()).canSideFlow(state, world, pos, facing.getOpposite())) return true;
        }
        //if fluids aren't equal
        if(fluid != null && fluid != fluidIn) return true;
            //default
        else return state.getMaterial().blocksMovement();
    }

    //BlockDynamicLiquidPlugin
    public static int checkBlockHorizontal(BlockDynamicLiquid obj, World world, BlockPos offset, int currentMinLevel, EnumFacing facing) {
        int level = getDepth(obj, world, offset, facing);
        if(level < 0) return currentMinLevel;
        else {
            if(level == 0) ++obj.adjacentSourceBlocks;
            if(level >= 8) level = 0;

            return currentMinLevel >= 0 && level >= currentMinLevel ? currentMinLevel : level;
        }
    }

    //BlockDynamicLiquidPlugin
    public static int getDepth(BlockDynamicLiquid obj, World world, BlockPos offset, EnumFacing facing) {
        final IBlockState state = world.getBlockState(offset);
        final boolean fluidMatches = FluidloggedUtils.getFluidFromBlock(state.getBlock()) == FluidloggedUtils.getFluidFromBlock(obj);
        final boolean canSideFlow = !(state.getBlock() instanceof AbstractFluidloggedBlock) || ((AbstractFluidloggedBlock)state.getBlock()).canSideFlow(state, world, offset, facing.getOpposite());
        return fluidMatches && canSideFlow ? state.getValue(BlockLiquid.LEVEL) : -1;
    }

    //BlockDynamicLiquidPlugin
    public static boolean canFlowDown(BlockDynamicLiquid obj, World world, BlockPos offset, IBlockState down, IBlockState here) {
        final boolean isSource = FluidloggedUtils.isSource(world, offset.up(), here);
        final boolean fluidMatches = FluidloggedUtils.getFluidFromBlock(obj) == FluidloggedUtils.getFluidFromBlock(down.getBlock());
        return (!isSource || !fluidMatches) && isReplaceable(down, world, offset);
    }

    //BlockDynamicLiquidPlugin
    public static Material checkForMixing(World world, BlockPos pos) {
        final BlockPos offset = pos.down();
        final IBlockState state = world.getBlockState(offset);

        if(state.getMaterial() == Material.WATER) {
            if(!(state.getBlock() instanceof AbstractFluidloggedBlock) || ((AbstractFluidloggedBlock)state.getBlock()).canSideFlow(state, world, offset, UP)) return Material.WATER;
        }

        //default
        return Material.ROCK;
    }

    //BlockDynamicLiquidPlugin
    public static Set<EnumFacing> flowInto(BlockDynamicLiquid obj, World world, BlockPos pos, int i, int j) {
        final int[] flowCost = new int[4];
        int level = i + j;
        //has vertical flow
        if(i >= 8) level = 1;
        //cannot flow further
        else if(level < 0 || level >= 8) return new HashSet<>();

        //flows outward in each possible horizontal direction
        for(int index = 0; index < 4; index++) {
            EnumFacing facing = getHorizontal(index);
            BlockPos offset = pos.offset(facing);
            IBlockState state = world.getBlockState(offset);

            flowCost[index] = 1000;

            //side cannot flow
            if(FluidloggedUtils.isSource(world, offset, state) || !isReplaceable(state, world, offset)) continue;

            if(isReplaceable(world.getBlockState(offset.down(1)), world, offset.down(1))) flowCost[index] = 0;
            else flowCost[index] = calculateFlowCost(obj, world, offset, 1, index);
        }

        //does the flow
        final int min = Ints.min(flowCost);
        for(int index = 0; index < 4; index++) {
            if(flowCost[index] == min) {
                EnumFacing facing = getHorizontal(index);
                BlockPos offset = pos.offset(facing);
                IBlockState state = world.getBlockState(offset);

                if(FluidloggedUtils.getFluidFromBlock(obj) != FluidloggedUtils.getFluidFromBlock(state.getBlock()) && isReplaceable(state, world, offset)) {
                    if(state.getBlock() != Blocks.SNOW_LAYER) state.getBlock().dropBlockAsItem(world, offset, state, 0);
                    world.setBlockState(offset, obj.getDefaultState().withProperty(BlockLiquid.LEVEL, level));
                }
            }
        }

        //returns empty set cause that will prevent the old code from doing stuff
        return new HashSet<>();
    }

    //BlockDynamicLiquidPlugin
    public static int calculateFlowCost(BlockDynamicLiquid obj, World world, BlockPos pos, int recurseDepth, int index) {
        final EnumFacing facing = getHorizontal(index);
        int cost = 1000;

        for(int adjIndex = 0; adjIndex < 4; adjIndex++) {
            EnumFacing adjFacing = getHorizontal(adjIndex);
            //won't take the origin into account
            if(adjFacing == facing.getOpposite()) continue;

            BlockPos offset = pos.offset(adjFacing);
            IBlockState state = world.getBlockState(offset);

            //side cannot flow
            if(FluidloggedUtils.isSource(world, offset, state) || !isReplaceable(state, world, offset)) continue;

            //flow down
            if(isReplaceable(world.getBlockState(offset.down(1)), world, offset.down(1))) return recurseDepth;

            //side cannot flow
            if(recurseDepth >= (obj.getDefaultState().getMaterial() == Material.LAVA && !world.provider.doesWaterVaporize() ? 2 : 4)) continue;

            cost = Math.min(cost, calculateFlowCost(obj, world, offset, recurseDepth + 1, adjIndex));
        }

        return cost;
    }

    //BlockFluidBasePlugin
    public static Map<Block, Boolean> defaultDisplacements(Map<Block, Boolean> map) {
        final Map<Block, Boolean> ret = new HashMap<>();
        //restore old entries
        ret.put(Blocks.OAK_DOOR,                       false);
        ret.put(Blocks.SPRUCE_DOOR,                    false);
        ret.put(Blocks.BIRCH_DOOR,                     false);
        ret.put(Blocks.JUNGLE_DOOR,                    false);
        ret.put(Blocks.ACACIA_DOOR,                    false);
        ret.put(Blocks.DARK_OAK_DOOR,                  false);
        ret.put(Blocks.TRAPDOOR,                       false);
        ret.put(Blocks.IRON_TRAPDOOR,                  false);
        ret.put(Blocks.OAK_FENCE,                      false);
        ret.put(Blocks.SPRUCE_FENCE,                   false);
        ret.put(Blocks.BIRCH_FENCE,                    false);
        ret.put(Blocks.JUNGLE_FENCE,                   false);
        ret.put(Blocks.DARK_OAK_FENCE,                 false);
        ret.put(Blocks.ACACIA_FENCE,                   false);
        ret.put(Blocks.NETHER_BRICK_FENCE,             false);
        ret.put(Blocks.OAK_FENCE_GATE,                 false);
        ret.put(Blocks.SPRUCE_FENCE_GATE,              false);
        ret.put(Blocks.BIRCH_FENCE_GATE,               false);
        ret.put(Blocks.JUNGLE_FENCE_GATE,              false);
        ret.put(Blocks.DARK_OAK_FENCE_GATE,            false);
        ret.put(Blocks.ACACIA_FENCE_GATE,              false);
        ret.put(Blocks.WOODEN_PRESSURE_PLATE,          false);
        ret.put(Blocks.STONE_PRESSURE_PLATE,           false);
        ret.put(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,  false);
        ret.put(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,  false);
        ret.put(Blocks.LADDER,                         false);
        ret.put(Blocks.IRON_BARS,                      false);
        ret.put(Blocks.GLASS_PANE,                     false);
        ret.put(Blocks.STAINED_GLASS_PANE,             false);
        ret.put(Blocks.PORTAL,                         false);
        ret.put(Blocks.END_PORTAL,                     false);
        ret.put(Blocks.COBBLESTONE_WALL,               false);
        ret.put(Blocks.BARRIER,                        false);
        ret.put(Blocks.STANDING_BANNER,                false);
        ret.put(Blocks.WALL_BANNER,                    false);
        ret.put(Blocks.CAKE,                           false);
        ret.put(Blocks.IRON_DOOR,                      false);
        ret.put(Blocks.STANDING_SIGN,                  false);
        ret.put(Blocks.WALL_SIGN,                      false);
        ret.put(Blocks.REEDS,                          false);
        //any new entries added by other mods (never actually seen mods do this, but just in case)
        ret.putAll(map);

        return ret;
    }

    //BlockFluidBasePlugin
    public static Block canDisplace(Block block, BlockFluidBase fluid, IBlockAccess world, BlockPos pos) {
        final boolean flag = FluidloggedUtils.getFluidFromBlock(block) == fluid.getFluid();
        return flag || !isReplaceable(block.getDefaultState(), world, pos) ? fluid : null;
    }

    //BlockFluidBasePlugin
    public static Block hasVerticalFlow(IBlockAccess world, BlockPos pos, int densityDir, BlockFluidBase obj) {
        final BlockPos offset = pos.down(densityDir);
        final IBlockState state = world.getBlockState(offset);

        if(FluidloggedUtils.getFluidFromBlock(state.getBlock()) == obj.getFluid()) {
            final EnumFacing facing = getFacingFromVector(0, densityDir, 0);
            final boolean flag = (!(state.getBlock() instanceof AbstractFluidloggedBlock) || ((AbstractFluidloggedBlock)state.getBlock()).canSideFlow(state, world, offset, facing));

            return flag ? obj : null;
        }

        //default (false)
        return null;
    }

    //BlockFluidBasePlugin
    public static float getFluidHeightForRender(BlockFluidBase obj, IBlockAccess world, BlockPos pos, IBlockState up, int i, int j, int densityDir, int quantaPerBlock, float quantaFraction, float quantaPerBlockFloat) {
        final Fluid fluid = obj.getFluid();

        //check block above
        if(FluidloggedUtils.getFluidFromBlock(up.getBlock()) == fluid) {
            if(!(up.getBlock() instanceof AbstractFluidloggedBlock) || ((AbstractFluidloggedBlock)up.getBlock()).canSideFlow(up, world, pos.down(densityDir), densityDir == 1 ? UP : DOWN)) return 1;
        }

        final IBlockState state = world.getBlockState(pos);

        //is air
        if(state.getBlock().isAir(state, world, pos)) return 0;

        final boolean canSideFlow = canSideFlow(fluid, state, world, pos, i, j);
        final boolean fluidMatches = (FluidloggedUtils.getFluidFromBlock(state.getBlock()) == fluid);

        if(fluidMatches && canSideFlow) {
            //max render height
            if(state.getValue(BlockLiquid.LEVEL) == obj.getMaxRenderHeightMeta()) return quantaFraction;
        }

        //not fluid
        if(!fluidMatches || !canSideFlow) return (-1 / quantaPerBlockFloat) * quantaFraction;
        //fluid
        else return ((quantaPerBlock - state.getValue(BlockLiquid.LEVEL)) / quantaPerBlockFloat) * quantaFraction;
    }

    //BlockFluidBasePlugin (corrects side angles)
    @Nonnull public static boolean[] fixN = {false, false};
    @Nonnull public static boolean[] fixS = {false, false};
    @Nonnull public static boolean[] fixE = {false, false};
    @Nonnull public static boolean[] fixW = {false, false};

    //BlockFluidBasePlugin
    public static boolean canSideFlow(Fluid fluid, IBlockState state, IBlockAccess world, BlockPos pos, int i, int j) {
        //SE
        if(i == 0 && j == 0) {
            if(state.getBlock() instanceof AbstractFluidloggedBlock) return canSideFlowDir(fluid, state, world, pos, SOUTH, EAST);
            else return state.getBlockFaceShape(world, pos, SOUTH) != BlockFaceShape.SOLID || state.getBlockFaceShape(world, pos, EAST) != BlockFaceShape.SOLID;
        }
        //S
        else if(i == 1  && j == 0) {
            fixS = new boolean[]{false, false};
            if(state.getBlock() instanceof AbstractFluidloggedBlock) {
                if(canSideFlowDir(fluid, state, world, pos, SOUTH, null)) return true;

                //fix uneven corners
                final boolean flag1 = canSideFlowDir(fluid, state, world, pos, SOUTH, EAST);
                final boolean flag2 = canSideFlowDir(fluid, state, world, pos, SOUTH, WEST);
                if(flag1 != flag2) {
                    if(flag1) fixS[0] = true;
                    else      fixS[1] = true;
                }

                return flag1 || flag2;
            }
            else return state.getBlockFaceShape(world, pos, SOUTH) != BlockFaceShape.SOLID;
        }
        //SW
        else if(i == 2  && j == 0) {
            if(state.getBlock() instanceof AbstractFluidloggedBlock) return canSideFlowDir(fluid, state, world, pos, SOUTH, WEST);
            else return state.getBlockFaceShape(world, pos, SOUTH) != BlockFaceShape.SOLID || state.getBlockFaceShape(world, pos, WEST) != BlockFaceShape.SOLID;
        }
        //E
        else if(i == 0 && j == 1)  {
            fixE = new boolean[]{false, false};
            if(state.getBlock() instanceof AbstractFluidloggedBlock) {
                if(canSideFlowDir(fluid, state, world, pos, EAST, null)) return true;

                //fix uneven corners
                final boolean flag1 = canSideFlowDir(fluid, state, world, pos, EAST, SOUTH);
                final boolean flag2 = canSideFlowDir(fluid, state, world, pos, EAST, NORTH);
                if(flag1 != flag2) {
                    if(flag1) fixE[0] = true;
                    else      fixE[1] = true;
                }

                return flag1 || flag2;
            }
            else return state.getBlockFaceShape(world, pos, EAST) != BlockFaceShape.SOLID;
        }
        //W
        else if(i == 2  && j == 1)  {
            fixW = new boolean[]{false, false};
            if(state.getBlock() instanceof AbstractFluidloggedBlock) {
                if(canSideFlowDir(fluid, state, world, pos, WEST, null)) return true;

                //fix uneven corners
                final boolean flag1 = canSideFlowDir(fluid, state, world, pos, WEST, SOUTH);
                final boolean flag2 = canSideFlowDir(fluid, state, world, pos, WEST, NORTH);
                if(flag1 != flag2) {
                    if(flag1) fixW[0] = true;
                    else      fixW[1] = true;
                }

                return flag1 || flag2;
            }
            else return state.getBlockFaceShape(world, pos, WEST) != BlockFaceShape.SOLID;
        }
        //NE
        else if(i == 0 && j == 2)  {
            if(state.getBlock() instanceof AbstractFluidloggedBlock) return canSideFlowDir(fluid, state, world, pos, NORTH, EAST);
            else return state.getBlockFaceShape(world, pos, NORTH) != BlockFaceShape.SOLID || state.getBlockFaceShape(world, pos, EAST) != BlockFaceShape.SOLID;
        }
        //N
        else if(i == 1  && j == 2)  {
            fixN = new boolean[]{false, false};
            if(state.getBlock() instanceof AbstractFluidloggedBlock) {
                if(canSideFlowDir(fluid, state, world, pos, NORTH, null)) return true;

                //fix uneven corners
                final boolean flag1 = canSideFlowDir(fluid, state, world, pos, NORTH, EAST);
                final boolean flag2 = canSideFlowDir(fluid, state, world, pos, NORTH, WEST);
                if(flag1 != flag2) {
                    if(flag1) fixN[0] = true;
                    else      fixN[1] = true;
                }

                return flag1 || flag2;
            }
            else return state.getBlockFaceShape(world, pos, NORTH) != BlockFaceShape.SOLID;
        }
        //NW
        else if(i == 2  && j == 2)  {
            if(state.getBlock() instanceof AbstractFluidloggedBlock) return canSideFlowDir(fluid, state, world, pos, NORTH, WEST);
            else return state.getBlockFaceShape(world, pos, NORTH) != BlockFaceShape.SOLID || state.getBlockFaceShape(world, pos, WEST) != BlockFaceShape.SOLID;
        }

        //should never pass
        return !state.getMaterial().isSolid();
    }

    //BlockFluidBasePlugin
    public static boolean canSideFlowDir(Fluid fluid, IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing facing1, @Nullable EnumFacing facing2) {
        final AbstractFluidloggedBlock block = (AbstractFluidloggedBlock)state.getBlock();
        if(block.canSideFlow(state, world, pos, facing1) && canFlowInto(fluid, block, world, pos, facing1)) return true;
        else return facing2 != null && block.canSideFlow(state, world, pos, facing2) && canFlowInto(fluid, block, world, pos, facing2);
    }

    //BlockFluidBasePlugin
    public static boolean canFlowInto(Fluid fluid, AbstractFluidloggedBlock block, IBlockAccess world, BlockPos pos, EnumFacing facing) {
        final BlockPos offset = pos.offset(facing);
        final IBlockState state = world.getBlockState(offset);
        final boolean matches = FluidloggedUtils.getFluidFromBlock(state.getBlock()) == fluid;
        final boolean replaceable = isReplaceable(state, world, offset);

        return (matches && replaceable) || block.canDisplace(world, pos.offset(facing));
    }

    //BlockFluidBasePlugin
    public static boolean isFluid(IBlockState up, BlockFluidBase obj, IBlockAccess world, int densityDir, BlockPos pos) {
        if(FluidloggedUtils.getFluidFromBlock(up.getBlock()) == obj.getFluid()) {
            return !(up.getBlock() instanceof AbstractFluidloggedBlock) || ((AbstractFluidloggedBlock)up.getBlock()).canSideFlow(up, world, pos.down(densityDir), densityDir == 1 ? UP : DOWN);
        }

        //default
        return false;
    }

    //BlockFluidBasePlugin
    public static Block causesDownwardCurrent(Block block, BlockFluidBase obj, Fluid fluid) {
        return FluidloggedUtils.getFluidFromBlock(block) == fluid ? obj : null;
    }

    //BlockFluidBasePlugin
    public static int getFlowDecay(BlockFluidBase obj, IBlockAccess world, BlockPos pos, @Nullable EnumFacing facing, Fluid fluidIn, int quantaPerBlock, int densityDir) {
        final IBlockState state = world.getBlockState(pos);
        final @Nullable Fluid fluid = FluidloggedUtils.getFluidFromBlock(state.getBlock());
        int quantaValue;

        if(state.getBlock().isAir(state, world, pos)) quantaValue = 0;
        else if(fluid != fluidIn) quantaValue = -1;
        else if(facing != null && state.getBlock() instanceof AbstractFluidloggedBlock && !((AbstractFluidloggedBlock)state.getBlock()).canSideFlow(state, world, pos, facing.getOpposite())) quantaValue = -1;
        else quantaValue = quantaPerBlock - state.getValue(BlockLiquid.LEVEL);

        return quantaPerBlock - (quantaValue > 0 && quantaValue < quantaPerBlock && hasVerticalFlow(world, pos, densityDir, obj) == obj ? quantaPerBlock : quantaValue);
    }

    //BlockFluidBasePlugin
    public static boolean getFlowVector(IBlockAccess world, BlockPos pos, EnumFacing facing, Fluid fluidIn) {
        final IBlockState state = world.getBlockState(pos);
        final @Nullable Fluid fluid = FluidloggedUtils.getFluidFromBlock(state.getBlock());
        //fluidlogged block
        if(fluid == fluidIn && state.getBlock() instanceof AbstractFluidloggedBlock) {
            if(!((AbstractFluidloggedBlock)state.getBlock()).canSideFlow(state, world, pos, facing.getOpposite())) return true;
        }
        //if fluids aren't equal
        if(fluid != null && fluid != fluidIn) return true;
        //default
        else return state.getMaterial().blocksMovement();
    }

    //BlockFluidBasePlugin
    public static boolean shouldSideBeRendered(IBlockState here, IBlockState offset, IBlockAccess world, BlockPos pos, EnumFacing facing, int densityDir) {
        //handles fluidlogged fluid blocks
        final boolean exempt = offset.getBlock() instanceof AbstractFluidloggedBlock && !((AbstractFluidloggedBlock)offset.getBlock()).canSideFlow(offset, world, pos.offset(facing), facing.getOpposite());

        //handles fluid blocks
        if(!exempt) {
            final @Nullable Fluid fluid = FluidloggedUtils.getFluidFromBlock(offset.getBlock());
            if(fluid != null) return fluid != FluidloggedUtils.getFluidFromBlock(here.getBlock());
        }

        //top face shouldn't perform the rest of the checks
        if(facing == (densityDir < 0 ? EnumFacing.UP : EnumFacing.DOWN)) return true;

        //handles normal blocks
        final AxisAlignedBB aabb = here.getBoundingBox(world, pos);

        //bounding box isn't full on the given side
        switch(facing) {
            case DOWN:  if(aabb.minY > 0) return true;
            case UP:    if(aabb.maxY < 1) return true;
            case NORTH: if(aabb.minZ > 0) return true;
            case SOUTH: if(aabb.maxZ < 1) return true;
            case WEST:  if(aabb.minX > 0) return true;
            case EAST:  if(aabb.maxX < 1) return true;
        }

        //default
        return !offset.doesSideBlockRendering(world, pos.offset(facing), facing.getOpposite());
    }

    //BlockFluidRendererPlugin
    public static float getFluidHeightForRender(Fluid fluid, IBlockAccess world, BlockPos pos, IBlockState up, int i, int j) {
        //check block above
        if(isFluid(up, fluid, world, pos)) return 1;

        final IBlockState state = world.getBlockState(pos);

        //is air
        if(state.getBlock().isAir(state, world, pos)) return 0;

        final boolean canSideFlow = canSideFlow(fluid, state, world, pos, i, j);
        final boolean fluidMatches = (FluidloggedUtils.getFluidFromBlock(state.getBlock()) == fluid);

        if(fluidMatches && canSideFlow) {
            //max render height
            if(state.getValue(BlockLiquid.LEVEL) == 0) return 8f / 9;
        }

        //not fluid
        if(!fluidMatches|| !canSideFlow) return (-1 / 8f) * (8f / 9);
        //fluid
        else return ((8 - state.getValue(BlockLiquid.LEVEL)) / 8f) * (8f / 9);
    }

    //BlockFluidRendererPlugin
    public static float getFluidHeightAverage(float quantaFraction, int i, int j, float... flow) {
        float total = 0;
        int count = 0;

        for(int index = 0; index < flow.length; index++) {
            //fixes corners flowing into illegal sides (vanilla 1.13 bug)
            if(fixN[i] && j == 1 && index % 2 == 1) continue;
            if(fixS[i] && j == 0 && index % 2 == 0) continue;
            if(fixE[j] && i == 0 && index < 2) continue;
            if(fixW[j] && i == 1 && index > 1) continue;

            //old
            if(flow[index] >= quantaFraction) {
                total += flow[index] * 10;
                count += 10;
            }

            if(flow[index] >= 0) {
                total += flow[index];
                count++;
            }
        }

        return total / count;
    }

    //BlockFluidRendererPlugin
    public static boolean isFluid(IBlockState up, Fluid fluid, IBlockAccess world, BlockPos pos) {
        if(FluidloggedUtils.getFluidFromBlock(up.getBlock()) == fluid) {
            return !(up.getBlock() instanceof AbstractFluidloggedBlock) || ((AbstractFluidloggedBlock)up.getBlock()).canSideFlow(up, world, pos.up(), DOWN);
        }

        //default
        return false;
    }

    //ModelFluidPlugin
    public static float fixTextureFightingZ(float old, int index) {
        final EnumFacing facing = EnumFacing.getHorizontal((5 - index) % 4); // [W, S, E, N]
        if(facing.getAxis() == Axis.X) return old;
        else return old == 1 ? 0.998f : 0.002f;
    }

    //ModelFluidPlugin
    public static float fixTextureFightingX(float old, int index) {
        final EnumFacing facing = EnumFacing.getHorizontal((5 - index) % 4); // [W, S, E, N]
        if(facing.getAxis() == Axis.Z) return old;
        else return old == 1 ? 0.998f : 0.002f;
    }

    //FluidUtilPlugin
    public static boolean isStateFluidloggableCache = false;
    public static boolean isReplaceable(boolean replaceable, IBlockState here, Fluid fluid) {
        if(FluidloggedUtils.isStateFluidloggable(here, fluid)) {
            isStateFluidloggableCache = true;
            return true;
        }
        else return replaceable;
    }

    //FluidBlockWrapperPlugin
    public static int placeModded(IFluidBlock fluidBlock, World world, BlockPos pos, FluidStack resource, boolean doFill) {
        if(doFill) {
            if(FluidloggedUtils.tryFluidlogBlock(world, pos, null, resource.getFluid(), false, isStateFluidloggableCache)) {
                isStateFluidloggableCache = false;
                return Fluid.BUCKET_VOLUME;
            }

            isStateFluidloggableCache = false;
        }
        //default
        return fluidBlock.place(world, pos, resource, doFill);
    }

    //BlockLiquidWrapperPlugin
    public static void placeVanilla(World world, BlockPos pos, IBlockState fluidBlock, int flags, FluidStack resource) {
        if(!FluidloggedUtils.tryFluidlogBlock(world, pos, null, resource.getFluid(), false, isStateFluidloggableCache)) world.setBlockState(pos, fluidBlock, flags);
        isStateFluidloggableCache = false;
    }

    //not for any one plugin, but rather for a lot
    public static boolean isReplaceable(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getBlock().isReplaceable(world, pos) || (!state.getMaterial().blocksMovement() && !state.getMaterial().isLiquid());
    }

    //BlockRailBasePlugin
    public static boolean setBlockToAir(World world, BlockPos pos) {
        final @Nullable Fluid fluid = FluidloggedUtils.getFluidFromBlock(world.getBlockState(pos).getBlock());
        if(fluid == null) return world.setBlockToAir(pos);
        else return world.setBlockState(pos, fluid.getBlock().getDefaultState(), 11);
    }

    //BlockRailBasePlugin
    public static boolean setStoredOrRealSimple(World world, BlockPos pos, IBlockState state) {
        return setStoredOrRealSimple(world, pos, state, 3);
    }

    //WorldServerPlugin
    public static IBlockState getStoredOrReal(IBlockState here, WorldServer world, BlockPos pos) {
        if(!(here.getBlock() instanceof BlockFluidloggedTE)) return here;
        else return ((BlockFluidloggedTE)here.getBlock()).getStored(world, pos);
    }

    //WorldServerPlugin
    public static IBlockState correctStoredOrLiquid(WorldServer world, BlockPos pos, Block compare) {
        final IBlockState here = world.getBlockState(pos);
        //liquid
        if(Block.isEqualTo(here.getBlock(), compare)) return here;
        //stored
        else if(here.getBlock() instanceof BlockFluidloggedTE) return ((BlockFluidloggedTE)here.getBlock()).getStored(world, pos);
        //old
        return here;
    }

    //==========
    //MOD COMPAT
    //==========
    public static boolean ASCompat(IBlockState state, World world, BlockPos pos, EnumFacing facing, Fluid fluid) {
        return FluidloggedUtils.getFluidFromBlock(state.getBlock()) != fluid && !(state.getBlock() instanceof AbstractFluidloggedBlock && ((AbstractFluidloggedBlock)state.getBlock()).canSideFlow(state, world, pos.offset(facing), facing.getOpposite()));
    }

    public static Block BOPCompat(IBlockState state, Block obj, World world, BlockPos pos, EnumFacing facing, Fluid fluid) {
        return ASCompat(state, world, pos, facing, fluid) ? null : obj;
    }

    public static RayTraceResult TSCompat(FillBucketEvent event) {
        if(event.isCanceled() || event.getResult() != Event.Result.DEFAULT) return null;
        else return event.getTarget();
    }
}
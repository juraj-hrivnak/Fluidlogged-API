package git.jbredwards.fluidlogged_api.mod.asm.mixins.vanilla.block;

import git.jbredwards.fluidlogged_api.api.block.IFluidloggable;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;

import javax.annotation.Nonnull;

/**
 * makes piston bases fluidloggable by default
 * @author jbred
 *
 */
@Mixin(BlockPistonBase.class)
public abstract class MixinBlockPistonBase implements IFluidloggable
{
    @Override
    public boolean isFluidloggable(@Nonnull IBlockState state) {
        return state.getValue(BlockPistonBase.EXTENDED);
    }
}
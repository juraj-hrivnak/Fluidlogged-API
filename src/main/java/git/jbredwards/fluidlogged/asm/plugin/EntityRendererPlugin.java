package git.jbredwards.fluidlogged.asm.plugin;

import git.jbredwards.fluidlogged.asm.ASMUtils;
import git.jbredwards.fluidlogged.asm.AbstractPlugin;
import org.objectweb.asm.tree.*;

import javax.annotation.Nonnull;

import static org.objectweb.asm.Opcodes.*;

/**
 * fixes underwater block selection
 * for real though, it's hard-coded to not render underwater, but if I remove that, works fine underwater, WHY?! lol
 * @author jbred
 *
 */
public final class EntityRendererPlugin extends AbstractPlugin
{
    @Nonnull
    @Override
    public String getMethodName(boolean obfuscated) {
        return obfuscated ? "func_175068_a" : "renderWorldPass";
    }

    @Nonnull
    @Override
    public String getMethodDesc() {
        return "(IFJ)V";
    }

    @Override
    protected boolean transform(InsnList instructions, MethodNode method, AbstractInsnNode insn, boolean obfuscated) {
        //removes '!entity.isInsideOfMaterial()' at 1409 and replaces it with '!false'
        if(insn.getOpcode() == INVOKEVIRTUAL && ASMUtils.checkMethod(insn, obfuscated ? "func_70055_a" : "isInsideOfMaterial", "(Lnet/minecraft/block/material/Material;)Z")) {
            //ICONST_0 can also refer to 'false'
            instructions.insert(insn, new InsnNode(ICONST_0));
            instructions.remove(ASMUtils.getPrevious(insn, 2));
            instructions.remove(ASMUtils.getPrevious(insn, 1));
            instructions.remove(ASMUtils.getPrevious(insn, 0));

            return true;
        }

        return false;
    }
}

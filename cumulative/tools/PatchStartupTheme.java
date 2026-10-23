import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Apply the same theme before the core's machine chooser and standalone tool windows. */
public final class PatchStartupTheme implements Opcodes {
    static byte[] bytes(MethodNode m){ClassWriter w=new ClassWriter(0);w.visit(V1_8,ACC_PUBLIC,"Fixture",null,"java/lang/Object",null);m.accept(w);return w.toByteArray();}
    public static void main(String[] args)throws Exception{
        byte[] source=Files.readAllBytes(Paths.get(args[0]));ClassReader reader=new ClassReader(source);ClassNode before=new ClassNode(ASM8),after=new ClassNode(ASM8);reader.accept(before,0);reader.accept(after,0);int hooks=0;
        for(MethodNode m:after.methods)if(m.name.equals("main")&&m.desc.equals("([Ljava/lang/String;)V")){
            int existing=0;for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)i;if(c.owner.equals("com/kenjoy/uitrial/ProductionHooks")&&c.name.equals("begin"))existing++;}
            if(existing==1){Files.write(Paths.get(args[1]),source);System.out.println("STARTUP_THEME_PATCH existingHookReused=1 binary=unchanged");return;}
            if(existing>1)throw new IllegalStateException("Duplicate startup theme hooks");
            m.instructions.insert(new MethodInsnNode(INVOKESTATIC,"com/kenjoy/uitrial/ProductionHooks","begin","()V",false));hooks++;
        }
        if(hooks!=1)throw new IllegalStateException("Expected one main");ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);after.accept(writer);byte[] result=writer.toByteArray();ClassNode verified=new ClassNode(ASM8);new ClassReader(result).accept(verified,0);int preserved=0;
        for(int i=0;i<before.methods.size();i++){MethodNode a=before.methods.get(i),b=verified.methods.get(i);if(a.access!=b.access||!a.name.equals(b.name)||!a.desc.equals(b.desc))throw new IllegalStateException("ABI changed");if(!a.name.equals("main")){if(!Arrays.equals(bytes(a),bytes(b)))throw new IllegalStateException("Unrelated method changed");preserved++;}}
        Files.write(Paths.get(args[1]),result);System.out.println("STARTUP_THEME_PATCH hooks=1 unrelatedMethodsPreserved="+preserved+" ABI=unchanged");
    }
}

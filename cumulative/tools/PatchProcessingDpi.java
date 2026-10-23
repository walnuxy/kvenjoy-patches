import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Replace only the two IDA-confirmed screen-DPI consumers used for processing. */
public final class PatchProcessingDpi implements Opcodes {
    static byte[] methodBytes(MethodNode m){ClassWriter w=new ClassWriter(0);w.visit(V1_8,ACC_PUBLIC,"Fixture",null,"java/lang/Object",null);m.accept(w);return w.toByteArray();}
    public static void main(String[] args)throws Exception{
        ClassReader reader=new ClassReader(Files.readAllBytes(Paths.get(args[0])));ClassNode before=new ClassNode(ASM8),after=new ClassNode(ASM8);reader.accept(before,0);reader.accept(after,0);
        if(!after.name.equals("com/kenjoy/kenjoycnc/model/Settings")&&!after.name.equals("com/kenjoy/kenjoycnc/I"))throw new IllegalArgumentException("Unexpected DPI target");
        int hooks=0;Set<String> changed=new HashSet<String>();
        for(MethodNode method:after.methods)for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode){
            MethodInsnNode call=(MethodInsnNode)instruction;
            if(call.owner.equals("java/awt/Toolkit")&&call.name.equals("getScreenResolution")&&call.desc.equals("()I")){
                call.setOpcode(INVOKESTATIC);call.owner="com/kenjoy/uitrial/NativeDpi";call.name="processingDpi";call.desc="(Ljava/awt/Toolkit;)I";call.itf=false;hooks++;changed.add(method.name+method.desc);
            }
        }
        if(hooks!=1)throw new IllegalStateException("Expected one processing DPI call: "+hooks);
        ClassWriter writer=new ClassWriter(reader,0);after.accept(writer);byte[] data=writer.toByteArray();ClassNode verified=new ClassNode(ASM8);new ClassReader(data).accept(verified,0);int preserved=0;
        if(before.fields.size()!=verified.fields.size()||before.methods.size()!=verified.methods.size())throw new IllegalStateException("ABI changed");
        for(int i=0;i<before.methods.size();i++){MethodNode a=before.methods.get(i),b=verified.methods.get(i);if(a.access!=b.access||!a.name.equals(b.name)||!a.desc.equals(b.desc))throw new IllegalStateException("Method signature changed");if(!changed.contains(a.name+a.desc)){if(!Arrays.equals(methodBytes(a),methodBytes(b)))throw new IllegalStateException("Unrelated method changed");preserved++;}}
        Files.write(Paths.get(args[1]),data);System.out.println("PROCESSING_DPI_PATCH class="+after.name+" hooks="+hooks+" unrelatedMethodsPreserved="+preserved+" ABI=unchanged");
    }
}

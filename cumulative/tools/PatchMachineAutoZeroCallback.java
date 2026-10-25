import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Prevent a canceled auto-zero handoff from starting a delayed write. */
public final class PatchMachineAutoZeroCallback implements Opcodes {
    private static byte[] methodBytes(MethodNode method){ClassWriter writer=new ClassWriter(0);writer.visit(V1_8,ACC_PUBLIC,"Fixture",null,"java/lang/Object",null);method.accept(writer);return writer.toByteArray();}
    public static void main(String[] args)throws Exception{
        ClassReader reader=new ClassReader(Files.readAllBytes(Paths.get(args[0])));ClassNode before=new ClassNode(ASM8),node=new ClassNode(ASM8);reader.accept(before,0);reader.accept(node,0);
        if(!node.name.equals("com/kenjoy/kenjoycnc/machine/c/g"))throw new IllegalArgumentException(node.name);
        MethodNode callback=null;for(MethodNode method:node.methods)if(method.name.equals("onEvent")&&method.desc.equals("(Lflash/events/Event;)V"))callback=method;
        if(callback==null)throw new IllegalStateException("Missing auto-zero callback");
        callback.name="modernizationOnEvent";callback.access=ACC_PRIVATE;
        MethodNode wrapper=new MethodNode(ASM8,ACC_PUBLIC|ACC_FINAL,"onEvent","(Lflash/events/Event;)V",null,null);wrapper.visitCode();
        wrapper.visitVarInsn(ALOAD,0);wrapper.visitFieldInsn(GETFIELD,node.name,"a","Lcom/kenjoy/kenjoycnc/machine/c/f;");
        wrapper.visitVarInsn(ALOAD,0);wrapper.visitVarInsn(ALOAD,1);
        wrapper.visitMethodInsn(INVOKESTATIC,"com/kenjoy/uitrial/MachinePanelWorkflow","dispatchAutoZeroCallback","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",false);
        wrapper.visitInsn(RETURN);wrapper.visitMaxs(3,2);wrapper.visitEnd();node.methods.add(wrapper);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);node.accept(writer);byte[] patched=writer.toByteArray();ClassNode after=new ClassNode(ASM8);new ClassReader(patched).accept(after,0);
        int preserved=0;for(MethodNode old:before.methods)if(!old.name.equals("onEvent")){
            MethodNode same=null;for(MethodNode candidate:after.methods)if(candidate.name.equals(old.name)&&candidate.desc.equals(old.desc))same=candidate;
            if(same==null||same.access!=old.access||!Arrays.equals(methodBytes(old),methodBytes(same)))throw new IllegalStateException("Unrelated method changed: "+old.name+old.desc);preserved++;
        }
        Files.write(Paths.get(args[1]),patched);System.out.println("MACHINE_AUTOZERO_PATCH callbackGuard=1 unrelatedMethodsPreserved="+preserved);
    }
}

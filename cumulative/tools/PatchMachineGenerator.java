import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Observe the existing G-code generation runnable without changing its command logic. */
public final class PatchMachineGenerator implements Opcodes {
    private static byte[] methodBytes(MethodNode method){
        ClassWriter writer=new ClassWriter(0);writer.visit(V1_8,ACC_PUBLIC,"Fixture",null,"java/lang/Object",null);method.accept(writer);return writer.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        ClassReader reader=new ClassReader(Files.readAllBytes(Paths.get(args[0])));
        ClassNode before=new ClassNode(ASM8),node=new ClassNode(ASM8);reader.accept(before,0);reader.accept(node,0);
        if(!node.name.equals("com/kenjoy/kenjoycnc/machine/c/f"))throw new IllegalArgumentException(node.name);
        MethodNode worker=null;for(MethodNode method:node.methods)if(method.name.equals("run")&&method.desc.equals("()V"))worker=method;
        if(worker==null)throw new IllegalStateException("Missing machine generator");
        int bindings=0;
        for(MethodNode method:node.methods)if(method.name.equals("<init>"))for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==RETURN){
            InsnList bind=new InsnList();bind.add(new VarInsnNode(ALOAD,0));bind.add(new FieldInsnNode(GETFIELD,node.name,"a","Lcom/kenjoy/kenjoycnc/machine/c/c;"));bind.add(new VarInsnNode(ALOAD,0));bind.add(new MethodInsnNode(INVOKESTATIC,"com/kenjoy/uitrial/MachinePanelWorkflow","bindGenerator","(Ljava/lang/Object;Ljava/lang/Object;)V",false));method.instructions.insertBefore(instruction,bind);bindings++;
        }
        if(bindings!=1)throw new IllegalStateException("Expected one worker constructor return, found "+bindings);
        int autoZeroHooks=0;
        for(AbstractInsnNode instruction:worker.instructions.toArray())if(instruction instanceof MethodInsnNode){
            MethodInsnNode call=(MethodInsnNode)instruction;
            if(call.getOpcode()==INVOKEVIRTUAL&&call.owner.equals("com/kenjoy/kenjoycnc/machine/h")&&call.name.equals("e")&&call.desc.equals("()V")){
                InsnList argsForGuard=new InsnList();argsForGuard.add(new VarInsnNode(ALOAD,0));argsForGuard.add(new FieldInsnNode(GETFIELD,node.name,"a","Lcom/kenjoy/kenjoycnc/machine/c/c;"));argsForGuard.add(new VarInsnNode(ALOAD,0));worker.instructions.insertBefore(instruction,argsForGuard);
                MethodInsnNode guarded=new MethodInsnNode(INVOKESTATIC,"com/kenjoy/uitrial/MachinePanelWorkflow","startAutoZero","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",false);worker.instructions.set(instruction,guarded);
                InsnList mark=new InsnList();mark.add(new VarInsnNode(ALOAD,0));mark.add(new InsnNode(SWAP));mark.add(new FieldInsnNode(PUTFIELD,node.name,"modernizationAutoZeroStarted","Z"));worker.instructions.insert(guarded,mark);autoZeroHooks++;
            }
        }
        if(autoZeroHooks!=1)throw new IllegalStateException("Expected one auto-zero handoff, found "+autoZeroHooks);
        node.fields.add(new FieldNode(ASM8,ACC_PRIVATE,"modernizationAutoZeroStarted","Z",null,null));
        worker.name="modernizationRun";worker.access=ACC_PRIVATE;
        MethodNode wrapper=new MethodNode(ASM8,ACC_PUBLIC|ACC_FINAL,"run","()V",null,null);wrapper.visitCode();
        Label start=new Label(),end=new Label(),handler=new Label();wrapper.visitTryCatchBlock(start,end,handler,null);
        Label allowed=new Label();wrapper.visitVarInsn(ALOAD,0);wrapper.visitFieldInsn(GETFIELD,node.name,"a","Lcom/kenjoy/kenjoycnc/machine/c/c;");wrapper.visitVarInsn(ALOAD,0);
        wrapper.visitMethodInsn(INVOKESTATIC,"com/kenjoy/uitrial/MachinePanelWorkflow","generationStarted","(Ljava/lang/Object;Ljava/lang/Object;)Z",false);
        wrapper.visitJumpInsn(IFNE,allowed);wrapper.visitInsn(RETURN);wrapper.visitLabel(allowed);wrapper.visitFrame(F_SAME,0,null,0,null);
        wrapper.visitLabel(start);
        wrapper.visitVarInsn(ALOAD,0);wrapper.visitMethodInsn(INVOKESPECIAL,node.name,"modernizationRun","()V",false);wrapper.visitLabel(end);
        notifyFinished(wrapper,node.name);wrapper.visitInsn(RETURN);
        wrapper.visitLabel(handler);wrapper.visitFrame(F_FULL,1,new Object[]{node.name},1,new Object[]{"java/lang/Throwable"});wrapper.visitVarInsn(ASTORE,1);
        notifyFinished(wrapper,node.name);wrapper.visitVarInsn(ALOAD,1);wrapper.visitInsn(ATHROW);
        wrapper.visitMaxs(3,2);wrapper.visitEnd();node.methods.add(wrapper);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);node.accept(writer);byte[] patched=writer.toByteArray();
        ClassNode after=new ClassNode(ASM8);new ClassReader(patched).accept(after,0);int preserved=0;
        for(MethodNode old:before.methods)if(!old.name.equals("run")&&!old.name.equals("<init>")){
            MethodNode same=null;for(MethodNode candidate:after.methods)if(candidate.name.equals(old.name)&&candidate.desc.equals(old.desc))same=candidate;
            if(same==null||same.access!=old.access||!Arrays.equals(methodBytes(old),methodBytes(same)))throw new IllegalStateException("Unrelated method changed: "+old.name+old.desc);
            preserved++;
        }
        Files.write(Paths.get(args[1]),patched);
        System.out.println("MACHINE_GENERATOR_PATCH constructorBindings="+bindings+" autoZeroHooks="+autoZeroHooks+" unrelatedMethodsPreserved="+preserved);
    }
    private static void notifyFinished(MethodVisitor visitor,String owner){
        visitor.visitVarInsn(ALOAD,0);visitor.visitFieldInsn(GETFIELD,owner,"a","Lcom/kenjoy/kenjoycnc/machine/c/c;");
        visitor.visitVarInsn(ALOAD,0);
        visitor.visitVarInsn(ALOAD,0);visitor.visitFieldInsn(GETFIELD,owner,"modernizationAutoZeroStarted","Z");
        visitor.visitMethodInsn(INVOKESTATIC,"com/kenjoy/uitrial/MachinePanelWorkflow","generationFinished","(Ljava/lang/Object;Ljava/lang/Object;Z)V",false);
    }
}

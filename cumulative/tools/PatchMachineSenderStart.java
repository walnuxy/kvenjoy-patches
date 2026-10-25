import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Mark the point at which the vendor hands a prepared job to the global sender. */
public final class PatchMachineSenderStart implements Opcodes {
    public static void main(String[] args)throws Exception{
        byte[] input=Files.readAllBytes(Paths.get(args[0]));ClassReader reader=new ClassReader(input);
        ClassNode node=new ClassNode(ASM8);reader.accept(node,0);
        if(!node.name.equals("com/kenjoy/kenjoycnc/machine/c/c"))throw new IllegalArgumentException(node.name);
        MethodNode send=null;for(MethodNode method:node.methods)if(method.name.equals("a")&&method.desc.equals("(Lcom/kenjoy/kenjoycnc/machine/c/c;Ljava/lang/String;)V"))send=method;
        if(send==null)throw new IllegalStateException("Global sender entry changed");
        for(AbstractInsnNode instruction:send.instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
            if(call.owner.equals("com/kenjoy/uitrial/MachinePanelWorkflow")&&call.name.equals("startGlobalSender")){Files.write(Paths.get(args[1]),input);System.out.println("MACHINE_SENDER_PATCH existingHookReused=1");return;}
        }
        int oldHooks=0;
        for(AbstractInsnNode instruction:send.instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
            if(call.owner.equals("com/kenjoy/uitrial/MachinePanelWorkflow")&&call.name.equals("senderAttempted")){
                AbstractInsnNode load=instruction.getPrevious();if(!(load instanceof VarInsnNode)||load.getOpcode()!=ALOAD||((VarInsnNode)load).var!=0)throw new IllegalStateException("Unexpected old sender hook");
                send.instructions.remove(instruction);send.instructions.remove(load);oldHooks++;
            }
        }
        if(oldHooks>1)throw new IllegalStateException("Multiple old sender hooks: "+oldHooks);
        int hooks=0;
        for(AbstractInsnNode instruction:send.instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
            if(call.owner.equals("com/kenjoy/kenjoycnc/machine/h")&&call.name.equals("e")&&call.desc.equals("()V")){
                send.instructions.insertBefore(instruction,new VarInsnNode(ALOAD,0));
                send.instructions.set(instruction,new MethodInsnNode(INVOKESTATIC,"com/kenjoy/uitrial/MachinePanelWorkflow","startGlobalSender","(Ljava/lang/Object;Ljava/lang/Object;)V",false));hooks++;
            }
        }
        if(hooks!=1)throw new IllegalStateException("Expected one global sender start, found "+hooks);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);node.accept(writer);
        Files.write(Paths.get(args[1]),writer.toByteArray());System.out.println("MACHINE_SENDER_PATCH senderStarts="+hooks+" oldHooksMigrated="+oldHooks);
    }
}

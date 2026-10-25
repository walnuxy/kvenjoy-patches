import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Patch the production main class; the editable UI slice is not installed. */
public final class PatchMainSafety implements Opcodes {
    private static final String OWNER="com/kenjoy/kenjoycnc/KenjoyDraw";
    private static final String HELPER="com/kenjoy/uitrial/MainWindowSafety";
    private static final String IMPORT="com/kenjoy/uitrial/SettingsImportWorkflow";
    private static final String MODE="(Lcom/kenjoy/kenjoycnc/processTabs/IProcessTab;)V";
    private static final String EVENT="(Lflash/events/Event;)V";
    private static MethodNode method(ClassNode node,String name,String desc){for(MethodNode m:node.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;throw new IllegalStateException("Missing "+name+desc);}
    private static byte[] bytes(MethodNode m){ClassWriter w=new ClassWriter(0);w.visit(V1_8,ACC_PUBLIC,"Fixture",null,"java/lang/Object",null);m.accept(w);return w.toByteArray();}
    public static void main(String[] args)throws Exception{
        byte[] input=Files.readAllBytes(Paths.get(args[0]));ClassReader reader=new ClassReader(input);ClassNode before=new ClassNode(ASM8),node=new ClassNode(ASM8);reader.accept(before,0);reader.accept(node,0);
        if(!node.name.equals(OWNER))throw new IllegalArgumentException(node.name);
        if(node.methods.stream().anyMatch(m->m.name.equals("modernizationSwitchMode"))){Files.write(Paths.get(args[1]),input);System.out.println("MAIN_SAFETY_PATCH existingHookReused=1 binary=unchanged");return;}
        MethodNode mode=method(node,"a",MODE);int modeAccess=mode.access;mode.name="modernizationSwitchMode";mode.access=ACC_PRIVATE;
        int canvas=0;for(AbstractInsnNode insn:mode.instructions.toArray())if(insn instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)insn;
            if(call.getOpcode()==INVOKEINTERFACE&&call.name.equals("createCanvas")&&call.desc.equals("()Ljava/awt/Component;")){
                mode.instructions.set(insn,new MethodInsnNode(INVOKESTATIC,HELPER,"preparedCanvas","(Ljava/lang/Object;)Ljava/awt/Component;",false));canvas++;
            }
        }
        if(canvas!=1)throw new IllegalStateException("Expected one canvas creation, found "+canvas);
        MethodNode modeWrapper=new MethodNode(ASM8,modeAccess,"a",MODE,null,null);modeWrapper.visitCode();modeWrapper.visitVarInsn(ALOAD,0);modeWrapper.visitVarInsn(ALOAD,1);
        modeWrapper.visitMethodInsn(INVOKESTATIC,HELPER,"switchMode","(Ljava/lang/Object;Ljava/lang/Object;)V",false);modeWrapper.visitInsn(RETURN);modeWrapper.visitMaxs(2,2);modeWrapper.visitEnd();node.methods.add(modeWrapper);

        MethodNode importMethod=method(node,"i","()V");importMethod.instructions.clear();importMethod.tryCatchBlocks.clear();if(importMethod.localVariables!=null)importMethod.localVariables.clear();
        importMethod.visitCode();importMethod.visitVarInsn(ALOAD,0);importMethod.visitMethodInsn(INVOKESTATIC,IMPORT,"run","(Ljava/lang/Object;)V",false);importMethod.visitInsn(RETURN);importMethod.visitMaxs(1,1);importMethod.visitEnd();
        MethodNode action=method(node,"actionPerformed","(Ljava/awt/event/ActionEvent;)V");int bypass=0;
        for(AbstractInsnNode insn:action.instructions.toArray())if(insn instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)insn;
            if(call.owner.equals(OWNER)&&call.name.equals("i")&&call.desc.equals("()V")){
                boolean sawExit=false;for(AbstractInsnNode next=insn.getNext();next!=null&&next.getOpcode()!=RETURN;){AbstractInsnNode following=next.getNext();
                    if(next instanceof MethodInsnNode){MethodInsnNode m=(MethodInsnNode)next;if(m.owner.equals("java/lang/System")&&m.name.equals("exit"))sawExit=true;}
                    if(next.getOpcode()>=0)action.instructions.set(next,new InsnNode(NOP));next=following;
                }
                if(!sawExit)throw new IllegalStateException("Import success branch changed");bypass++;
            }
        }
        if(bypass!=1)throw new IllegalStateException("Expected one import branch, found "+bypass);

        MethodNode event=method(node,"onEvent",EVENT);int eventAccess=event.access;event.name="modernizationOnEvent";event.access=ACC_PRIVATE;int comparisons=0;
        for(AbstractInsnNode insn:event.instructions.toArray())if(insn.getOpcode()==IF_ACMPNE){JumpInsnNode old=(JumpInsnNode)insn;
            event.instructions.insertBefore(insn,new MethodInsnNode(INVOKEVIRTUAL,"java/lang/String","equals","(Ljava/lang/Object;)Z",false));
            event.instructions.set(insn,new JumpInsnNode(IFEQ,old.label));comparisons++;
        }
        if(comparisons!=2)throw new IllegalStateException("Expected two firmware event comparisons, found "+comparisons);
        MethodNode eventWrapper=new MethodNode(ASM8,eventAccess,"onEvent",EVENT,null,null);eventWrapper.visitCode();eventWrapper.visitVarInsn(ALOAD,0);eventWrapper.visitVarInsn(ALOAD,1);
        eventWrapper.visitMethodInsn(INVOKESTATIC,HELPER,"firmwareEvent","(Ljava/lang/Object;Ljava/lang/Object;)V",false);eventWrapper.visitInsn(RETURN);eventWrapper.visitMaxs(2,2);eventWrapper.visitEnd();node.methods.add(eventWrapper);

        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);node.accept(writer);byte[] output=writer.toByteArray();ClassNode after=new ClassNode(ASM8);new ClassReader(output).accept(after,0);
        Set<String> changed=new HashSet<String>(Arrays.asList("a"+MODE,"i()V","actionPerformed(Ljava/awt/event/ActionEvent;)V","onEvent"+EVENT));int preserved=0;
        for(MethodNode old:before.methods)if(!changed.contains(old.name+old.desc)){
            MethodNode same=method(after,old.name,old.desc);if(same.access!=old.access||!Arrays.equals(bytes(old),bytes(same)))throw new IllegalStateException("Unrelated method changed: "+old.name+old.desc);preserved++;
        }
        Files.write(Paths.get(args[1]),output);System.out.println("MAIN_SAFETY_PATCH canvas="+canvas+" import="+bypass+" eventComparisons="+comparisons+" unrelatedMethodsPreserved="+preserved+" ABI=unchanged");
    }
}

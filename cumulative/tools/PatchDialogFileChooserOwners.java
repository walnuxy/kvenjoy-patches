import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Pass modal dialog itself (`this`) instead of blocked background main frame to file choosers. */
public final class PatchDialogFileChooserOwners implements Opcodes {
    private static byte[] methodBytes(MethodNode method){
        ClassWriter writer=new ClassWriter(0);
        writer.visit(V1_8,ACC_PUBLIC,"Fixture",null,"java/lang/Object",null);
        method.accept(writer);
        return writer.toByteArray();
    }

    public static void main(String[] args)throws Exception{
        ClassReader reader=new ClassReader(Files.readAllBytes(Paths.get(args[0])));
        ClassNode before=new ClassNode(ASM8),node=new ClassNode(ASM8);
        reader.accept(before,0);
        reader.accept(node,0);
        if(!node.name.equals("com/kenjoy/kenjoycnc/machine/c/b/f"))throw new IllegalArgumentException(node.name);
        int fixes=0;
        Set<String> targetMethods=new HashSet<String>(Arrays.asList("b","c"));
        for(MethodNode method:node.methods){
            if(!targetMethods.contains(method.name)||!method.desc.equals("(Ljava/awt/AWTEvent;)V"))continue;
            for(AbstractInsnNode instruction:method.instructions.toArray()){
                if(!(instruction instanceof MethodInsnNode))continue;
                MethodInsnNode getApp=(MethodInsnNode)instruction;
                if(getApp.getOpcode()!=INVOKESTATIC||!getApp.owner.equals("com/kenjoy/kenjoycnc/KenjoyDraw")||!getApp.name.equals("a")||!getApp.desc.equals("()Lcom/kenjoy/kenjoycnc/KenjoyDraw;"))continue;
                AbstractInsnNode next=getApp.getNext();
                while(next!=null&&next.getOpcode()==-1)next=next.getNext();
                if(next instanceof MethodInsnNode){
                    MethodInsnNode getFrame=(MethodInsnNode)next;
                    if(getFrame.getOpcode()==INVOKEVIRTUAL&&getFrame.owner.equals("com/kenjoy/kenjoycnc/KenjoyDraw")&&getFrame.name.equals("f")&&getFrame.desc.equals("()Ljavax/swing/JFrame;")){
                        method.instructions.insertBefore(getApp,new VarInsnNode(ALOAD,0));
                        method.instructions.remove(getApp);
                        method.instructions.remove(getFrame);
                        fixes++;
                    }
                }
            }
        }
        if(fixes!=2)throw new IllegalStateException("Expected 2 owner fixes in Grbl settings dialog, found "+fixes);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        byte[] patched=writer.toByteArray();
        ClassNode after=new ClassNode(ASM8);
        new ClassReader(patched).accept(after,0);
        int preserved=0;
        for(MethodNode old:before.methods){
            if(targetMethods.contains(old.name)&&old.desc.equals("(Ljava/awt/AWTEvent;)V"))continue;
            MethodNode same=null;
            for(MethodNode candidate:after.methods)if(candidate.name.equals(old.name)&&candidate.desc.equals(old.desc))same=candidate;
            if(same==null||same.access!=old.access||!Arrays.equals(methodBytes(old),methodBytes(same)))
                throw new IllegalStateException("Unrelated method changed: "+old.name+old.desc);
            preserved++;
        }
        Files.write(Paths.get(args[1]),patched);
        System.out.println("DIALOG_OWNER_PATCH class="+node.name+" fixes="+fixes+" unrelatedMethodsPreserved="+preserved);
    }
}

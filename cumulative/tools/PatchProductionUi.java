import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Add presentation hooks to the current installed class; leave authentication/startup methods intact. */
public final class PatchProductionUi implements Opcodes {
    static final String HOOK="com/kenjoy/uitrial/ProductionHooks";
    static byte[] methodBytes(MethodNode m){ClassWriter w=new ClassWriter(0);w.visit(V1_8,ACC_PUBLIC,"Fixture",null,"java/lang/Object",null);m.accept(w);w.visitEnd();return w.toByteArray();}
    public static void main(String[] args)throws Exception{
        Path input=Paths.get(args[0]),output=Paths.get(args[1]);if(input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))throw new IllegalArgumentException("Separate output required");
        ClassReader reader=new ClassReader(Files.readAllBytes(input));ClassNode before=new ClassNode(ASM8),after=new ClassNode(ASM8);reader.accept(before,0);reader.accept(after,0);
        if(!after.name.equals("com/kenjoy/kenjoycnc/KenjoyDraw"))throw new IllegalArgumentException("Unexpected target");
        int starts=0,frames=0,ready=0,refresh=0;Set<String> changed=new HashSet<String>();
        for(MethodNode m:after.methods){
            boolean ctor=m.name.equals("<init>")&&m.desc.equals("()V");
            boolean mode=m.name.equals("a")&&m.desc.equals("(Lcom/kenjoy/kenjoycnc/processTabs/IProcessTab;)V");
            if(!ctor&&!mode)continue;
            for(AbstractInsnNode i=m.instructions.getFirst();i!=null;){AbstractInsnNode next=i.getNext();
                if(ctor&&i.getOpcode()==NEW&&((TypeInsnNode)i).desc.equals("javax/swing/JFrame")){m.instructions.insertBefore(i,new MethodInsnNode(INVOKESTATIC,HOOK,"begin","()V",false));starts++;}
                if(i instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)i;if(c.owner.equals(HOOK))throw new IllegalStateException("Target already patched");
                    if(ctor&&c.owner.equals("javax/swing/JFrame")&&c.name.equals("<init>")&&c.desc.equals("()V")){
                        InsnList hook=new InsnList();hook.add(new InsnNode(DUP));hook.add(new MethodInsnNode(INVOKESTATIC,HOOK,"frame","(Ljavax/swing/JFrame;)V",false));m.instructions.insert(i,hook);frames++;
                    }
                }
                if(i.getOpcode()==RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new MethodInsnNode(INVOKESTATIC,HOOK,ctor?"ready":"refresh","(Ljava/lang/Object;)V",false));m.instructions.insertBefore(i,hook);if(ctor)ready++;else refresh++;}
                i=next;
            }
            changed.add(m.name+m.desc);
        }
        if(starts!=1||frames!=1||ready!=1||refresh<1||changed.size()!=2)throw new IllegalStateException("Unexpected hook sites "+starts+"/"+frames+"/"+ready+"/"+refresh);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);after.accept(writer);byte[] data=writer.toByteArray();ClassNode verified=new ClassNode(ASM8);new ClassReader(data).accept(verified,0);int preserved=0;
        if(before.methods.size()!=verified.methods.size()||before.fields.size()!=verified.fields.size())throw new IllegalStateException("ABI changed");
        for(int i=0;i<before.methods.size();i++){MethodNode a=before.methods.get(i),b=verified.methods.get(i);if(!a.name.equals(b.name)||!a.desc.equals(b.desc)||a.access!=b.access)throw new IllegalStateException("Method signature changed");if(!changed.contains(a.name+a.desc)){if(!Arrays.equals(methodBytes(a),methodBytes(b)))throw new IllegalStateException("Unrelated method changed "+a.name+a.desc);preserved++;}}
        Files.write(output,data);System.out.println("PRODUCTION_PATCH frame="+frames+" ready="+ready+" refresh="+refresh+" unrelatedMethodsPreserved="+preserved+" ABI=unchanged");
    }
}

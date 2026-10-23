import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Screen entry points only. All model, conversion, export and device methods remain identical. */
public final class PatchWorkspaces implements Opcodes {
    static byte[] bytes(MethodNode m){ClassWriter w=new ClassWriter(0);w.visit(V1_8,ACC_PUBLIC,"Fixture",null,"java/lang/Object",null);m.accept(w);return w.toByteArray();}
    public static void main(String[] args)throws Exception{
        ClassReader r=new ClassReader(Files.readAllBytes(Paths.get(args[0])));ClassNode before=new ClassNode(ASM8),node=new ClassNode(ASM8);r.accept(before,0);r.accept(node,0);
        String changed;int hooks=0;
        if(node.name.equals("com/kenjoy/kenjoycnc/h")){
            changed="paint(Ljava/awt/Graphics;)V";
            MethodNode original=null;for(MethodNode m:node.methods)if((m.name+m.desc).equals(changed))original=m;
            if(original==null)throw new IllegalStateException("Missing paint");original.name="modernizationPaint";original.access=ACC_PRIVATE;
            MethodNode m=new MethodNode(ASM8,ACC_PUBLIC,"paint","(Ljava/awt/Graphics;)V",null,null);m.visitCode();
            m.visitVarInsn(ALOAD,1);m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESTATIC,"com/kenjoy/uitrial/ScreenGraphics","prepare","(Ljava/awt/Graphics;Ljavax/swing/JComponent;)Ljava/awt/Graphics;",false);m.visitVarInsn(ASTORE,2);
            Label start=new Label(),end=new Label(),handler=new Label();m.visitTryCatchBlock(start,end,handler,null);m.visitLabel(start);m.visitVarInsn(ALOAD,0);m.visitVarInsn(ALOAD,2);m.visitMethodInsn(INVOKESPECIAL,node.name,"modernizationPaint","(Ljava/awt/Graphics;)V",false);m.visitLabel(end);m.visitVarInsn(ALOAD,2);m.visitMethodInsn(INVOKEVIRTUAL,"java/awt/Graphics","dispose","()V",false);m.visitInsn(RETURN);
            m.visitLabel(handler);m.visitFrame(F_FULL,3,new Object[]{node.name,"java/awt/Graphics","java/awt/Graphics"},1,new Object[]{"java/lang/Throwable"});m.visitVarInsn(ASTORE,3);m.visitVarInsn(ALOAD,2);m.visitMethodInsn(INVOKEVIRTUAL,"java/awt/Graphics","dispose","()V",false);m.visitVarInsn(ALOAD,3);m.visitInsn(ATHROW);m.visitMaxs(2,4);m.visitEnd();node.methods.add(m);hooks=1;
        }else if(node.name.equals("com/kenjoy/kenjoycnc/G")){
            changed="display(Lcom/jogamp/opengl/GLAutoDrawable;)V";
            for(MethodNode m:node.methods)if((m.name+m.desc).equals(changed))for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)i;
                if(f.getOpcode()==GETFIELD&&f.owner.equals(node.name)&&((f.name.equals("L")&&f.desc.equals("Ljava/awt/Color;"))||(f.name.equals("w")&&f.desc.equals("[B")))){
                    AbstractInsnNode next=i.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();if(f.name.equals("w")&&next!=null&&next.getOpcode()==ARRAYLENGTH)continue;
                    InsnList h=new InsnList();h.add(new VarInsnNode(ALOAD,0));h.add(new MethodInsnNode(INVOKESTATIC,"com/kenjoy/uitrial/GlPalette",f.name.equals("L")?"background":"colors",f.name.equals("L")?"(Ljava/awt/Color;Ljavax/swing/JComponent;)Ljava/awt/Color;":"([BLjavax/swing/JComponent;)[B",false));m.instructions.insert(i,h);hooks++;
                }
            }
            if(hooks!=4)throw new IllegalStateException("Unexpected GL hooks "+hooks);
        }else throw new IllegalArgumentException(node.name);
        ClassWriter w=new ClassWriter(r,ClassWriter.COMPUTE_MAXS);node.accept(w);byte[] data=w.toByteArray();ClassNode verified=new ClassNode(ASM8);new ClassReader(data).accept(verified,0);int preserved=0;
        for(MethodNode a:before.methods)if(!(a.name+a.desc).equals(changed)){
            MethodNode b=null;for(MethodNode v:verified.methods)if(v.name.equals(a.name)&&v.desc.equals(a.desc))b=v;
            if(b==null||a.access!=b.access||!Arrays.equals(bytes(a),bytes(b)))throw new IllegalStateException("Unrelated method changed "+a.name+a.desc);preserved++;
        }
        Files.write(Paths.get(args[1]),data);System.out.println("WORKSPACE_PATCH class="+node.name+" screenHooks="+hooks+" unrelatedMethodsPreserved="+preserved);
    }
}

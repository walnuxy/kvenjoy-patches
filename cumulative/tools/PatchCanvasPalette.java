import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Modify only drawing colors in a copied existing Note canvas class; preserve its ABI. */
public final class PatchCanvasPalette implements Opcodes {
    static final String HELPER="com/kenjoy/uitrial/UiCanvasColors";
    static byte[] methodBytes(MethodNode m){ClassWriter w=new ClassWriter(0);w.visit(V1_8,ACC_PUBLIC,"Fixture",null,"java/lang/Object",null);m.accept(w);w.visitEnd();return w.toByteArray();}
    public static void main(String[] args)throws Exception{
        if(Paths.get(args[0]).toAbsolutePath().normalize().equals(Paths.get(args[1]).toAbsolutePath().normalize()))throw new IllegalArgumentException("Input must remain unchanged");
        byte[] source=Files.readAllBytes(Paths.get(args[0]));ClassReader reader=new ClassReader(source);
        ClassNode old=new ClassNode(ASM8);reader.accept(old,0);ClassNode node=new ClassNode(ASM8);reader.accept(node,0);
        Set<String> changed=new HashSet<String>();int colors=0;
        for(MethodNode m:node.methods){
            if(m.name.equals("paint")&&m.desc.equals("(Ljava/awt/Graphics;)V"))throw new IllegalStateException("Unexpected existing paint override");
            if(!m.name.equals("a")||!(m.desc.equals("(Ljava/awt/Graphics2D;)V")||m.desc.equals("(Ljava/awt/Graphics2D;Z)V")))continue;
            boolean flag=m.desc.contains(";Z");
            for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode){
                MethodInsnNode call=(MethodInsnNode)i;
                if(call.name.equals("setColor")&&call.desc.equals("(Ljava/awt/Color;)V")&&call.owner.equals("java/awt/Graphics2D")){
                    InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(flag?new VarInsnNode(ILOAD,2):new InsnNode(ICONST_0));
                    hook.add(new MethodInsnNode(INVOKESTATIC,HELPER,"map","(Ljava/awt/Color;Ljavax/swing/JComponent;Z)Ljava/awt/Color;",false));m.instructions.insertBefore(i,hook);colors++;
                }
            }
            changed.add(m.name+m.desc);
        }
        if(colors!=7||changed.size()!=2)throw new IllegalStateException("Unexpected drawing hooks: "+colors+" "+changed);
        MethodNode paint=new MethodNode(ASM8,ACC_PUBLIC,"paint","(Ljava/awt/Graphics;)V",null,null);
        paint.visitCode();paint.visitVarInsn(ALOAD,1);paint.visitVarInsn(ALOAD,0);paint.visitMethodInsn(INVOKESTATIC,HELPER,"prepare","(Ljava/awt/Graphics;Ljavax/swing/JComponent;)Ljava/awt/Graphics;",false);paint.visitVarInsn(ASTORE,2);
        Label start=new Label(),end=new Label(),handler=new Label();paint.visitTryCatchBlock(start,end,handler,null);paint.visitLabel(start);
        paint.visitVarInsn(ALOAD,0);paint.visitVarInsn(ALOAD,2);paint.visitMethodInsn(INVOKESPECIAL,node.superName,"paint","(Ljava/awt/Graphics;)V",false);paint.visitLabel(end);
        paint.visitVarInsn(ALOAD,2);paint.visitMethodInsn(INVOKEVIRTUAL,"java/awt/Graphics","dispose","()V",false);paint.visitInsn(RETURN);
        paint.visitLabel(handler);paint.visitFrame(F_FULL,3,new Object[]{node.name,"java/awt/Graphics","java/awt/Graphics"},1,new Object[]{"java/lang/Throwable"});paint.visitVarInsn(ASTORE,3);paint.visitVarInsn(ALOAD,2);paint.visitMethodInsn(INVOKEVIRTUAL,"java/awt/Graphics","dispose","()V",false);paint.visitVarInsn(ALOAD,3);paint.visitInsn(ATHROW);paint.visitMaxs(2,4);paint.visitEnd();node.methods.add(paint);
        int preserved=0;
        for(int i=0;i<old.methods.size();i++){MethodNode a=old.methods.get(i),b=node.methods.get(i);if(!changed.contains(a.name+a.desc)){if(!Arrays.equals(methodBytes(a),methodBytes(b)))throw new IllegalStateException("Business method changed: "+a.name+a.desc);preserved++;}}
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);node.accept(writer);byte[] result=writer.toByteArray();
        ClassNode verified=new ClassNode(ASM8);new ClassReader(result).accept(verified,0);
        for(int i=0;i<old.methods.size();i++){MethodNode a=old.methods.get(i),b=verified.methods.get(i);if(!changed.contains(a.name+a.desc)&&!Arrays.equals(methodBytes(a),methodBytes(b)))throw new IllegalStateException("Final business method changed: "+a.name+a.desc);}
        Files.write(Paths.get(args[1]),result);
        System.out.println("CANVAS_PATCH colors="+colors+" unchangedMethods="+preserved+" changedDrawingMethods=2 addedPaint=1");
    }
}

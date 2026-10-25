import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Guard the vendor's private PDF preview render entry, including all rerender paths. */
public final class PatchPdfDialog implements Opcodes {
    public static void main(String[] args)throws Exception{
        byte[] input=Files.readAllBytes(Paths.get(args[0]));ClassReader reader=new ClassReader(input);
        ClassNode node=new ClassNode(ASM8);reader.accept(node,0);
        if(!node.name.equals("com/kenjoy/kenjoycnc/z"))throw new IllegalArgumentException(node.name);
        MethodNode render=null;for(MethodNode method:node.methods)if(method.name.equals("a")&&method.desc.equals("(I)V"))render=method;
        if(render==null)throw new IllegalStateException("PDF render method changed");
        boolean guarded=false,completed=false;
        for(AbstractInsnNode instruction:render.instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
            if(call.owner.equals("com/kenjoy/uitrial/PdfRenderSafety")){
                if(call.name.equals("allow"))guarded=true;
                if(call.name.equals("completed"))completed=true;
            }
        }
        if(guarded&&completed){Files.write(Paths.get(args[1]),input);System.out.println("PDF_RENDER_PATCH existingHooksReused=1");return;}
        if(!guarded){
            InsnList guard=new InsnList();LabelNode permitted=new LabelNode();
            guard.add(new VarInsnNode(ALOAD,0));guard.add(new VarInsnNode(ILOAD,1));
            guard.add(new MethodInsnNode(INVOKESTATIC,"com/kenjoy/uitrial/PdfRenderSafety","allow","(Ljava/lang/Object;I)Z",false));
            guard.add(new JumpInsnNode(IFNE,permitted));guard.add(new InsnNode(RETURN));guard.add(permitted);
            render.instructions.insert(guard);
        }
        int completion=0;
        for(AbstractInsnNode instruction:render.instructions.toArray())if(!completed&&instruction instanceof FieldInsnNode){FieldInsnNode field=(FieldInsnNode)instruction;
            if(field.getOpcode()==PUTSTATIC&&field.owner.equals(node.name)&&field.name.equals("a")&&field.desc.equals("Ljava/awt/image/BufferedImage;")){
                InsnList done=new InsnList();done.add(new VarInsnNode(ALOAD,0));done.add(new VarInsnNode(ILOAD,1));
                done.add(new MethodInsnNode(INVOKESTATIC,"com/kenjoy/uitrial/PdfRenderSafety","completed","(Ljava/lang/Object;I)V",false));
                render.instructions.insert(instruction,done);completion++;
            }
        }
        if(completion!=1)throw new IllegalStateException("Expected one PDF preview image assignment, found "+completion);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);node.accept(writer);
        Files.write(Paths.get(args[1]),writer.toByteArray());System.out.println("PDF_RENDER_PATCH guardedMethods=1 completedMethods="+completion);
    }
}

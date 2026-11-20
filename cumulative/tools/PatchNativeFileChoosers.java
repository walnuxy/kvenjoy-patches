import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;

/** Replace only legacy chooser display calls; keep each caller's format and path workflow. */
public final class PatchNativeFileChoosers {
    private static final String BRIDGE="com/kenjoy/kenjoycnc/note/NoteFileDialogs";
    public static void main(String[] args)throws Exception {
        byte[] original=Files.readAllBytes(Paths.get(args[0]));ClassReader reader=new ClassReader(original);
        final int expectedOpen=Integer.parseInt(args[2]),expectedSave=Integer.parseInt(args[3]);
        final int[] changed={0,0},existing={0,0};final boolean shared=reader.getClassName().equals("com/kenjoy/kenjoycnc/L");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM8,writer){
            public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                final boolean saveFactory=shared&&(access&Opcodes.ACC_STATIC)!=0&&descriptor.equals("(Ljava/awt/Component;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/io/File;");
                return new MethodVisitor(Opcodes.ASM8,super.visitMethod(access,name,descriptor,signature,exceptions)){
                    public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){
                        int kind=method.equals("showOpenDialog")?0:method.equals("showSaveDialog")?1:-1;
                        if(kind>=0&&owner.equals(BRIDGE)&&opcode==Opcodes.INVOKESTATIC){existing[kind]++;}
                        if(kind>=0&&(owner.equals("javax/swing/JFileChooser")||owner.equals("com/kenjoy/kenjoycnc/PathFileChooser"))){
                            if(opcode!=Opcodes.INVOKEVIRTUAL||!desc.equals("(Ljava/awt/Component;)I"))throw new IllegalStateException("Unexpected chooser signature: "+method+desc);
                            String target="(Ljavax/swing/JFileChooser;Ljava/awt/Component;)I";
                            if(kind==1&&shared){if(!saveFactory)throw new IllegalStateException("Save extension local is not the known shared factory");super.visitVarInsn(Opcodes.ALOAD,3);target="(Ljavax/swing/JFileChooser;Ljava/awt/Component;Ljava/lang/String;)I";}
                            changed[kind]++;super.visitMethodInsn(Opcodes.INVOKESTATIC,BRIDGE,method,target,false);
                            // The native save has already finalized the suffix and confirmed overwrite.
                            // Disable L's later case-sensitive append (e.g. output.SVG -> output.SVG.svg).
                            if(kind==1&&shared){super.visitInsn(Opcodes.ACONST_NULL);super.visitVarInsn(Opcodes.ASTORE,3);}
                            return;
                        }
                        if((owner.equals("javax/swing/JFileChooser")||owner.equals("com/kenjoy/kenjoycnc/PathFileChooser"))&&method.equals("showDialog"))throw new IllegalStateException("Review custom chooser before patching");
                        super.visitMethodInsn(opcode,owner,method,desc,itf);
                    }
                };
            }
        },0);
        if(changed[0]+existing[0]!=expectedOpen||changed[1]+existing[1]!=expectedSave)throw new IllegalStateException("Chooser call count differs in "+reader.getClassName());
        boolean reused=changed[0]+changed[1]==0;Files.write(Paths.get(args[1]),reused?original:writer.toByteArray());
        System.out.println("NATIVE_FILE_DIALOG_PATCH class="+reader.getClassName()+" open="+expectedOpen+" save="+expectedSave+" changed="+(changed[0]+changed[1])+" reused="+reused);
    }
}

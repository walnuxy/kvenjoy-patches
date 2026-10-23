import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Replace screen-only font preview palette literals; retain glyph data, paths and metrics. */
public final class PatchFontPreview implements Opcodes {
    static byte[] bytes(MethodNode m){ClassWriter w=new ClassWriter(0);w.visit(V1_8,ACC_PUBLIC,"Fixture",null,"java/lang/Object",null);m.accept(w);return w.toByteArray();}
    public static void main(String[] args)throws Exception{
        ClassReader reader=new ClassReader(Files.readAllBytes(Paths.get(args[0])));ClassNode before=new ClassNode(ASM8),after=new ClassNode(ASM8);reader.accept(before,0);reader.accept(after,0);
        if(!after.name.equals("com/kenjoy/kenjoycnc/l/e"))throw new IllegalArgumentException("Unexpected preview class");
        int colors=0,preserved=0;
        for(MethodNode m:after.methods)if(m.name.equals("paint")&&m.desc.equals("(Ljava/awt/Graphics;)V"))for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof FieldInsnNode){
            FieldInsnNode f=(FieldInsnNode)i;if(f.getOpcode()==GETSTATIC&&f.owner.equals("java/awt/Color")){
                String role=f.name.equals("WHITE")?"WELL":f.name.equals("BLACK")?"INK":f.name.equals("RED")?"GUIDE":f.name.equals("BLUE")?"PRIMARY_EDGE":null;
                if(role==null)throw new IllegalStateException("Unexpected color "+f.name);f.owner="com/kenjoy/uitrial/UiPalette";f.name=role;colors++;
            }
        }
        if(colors!=4)throw new IllegalStateException("Expected four preview palette literals, got "+colors);
        ClassWriter out=new ClassWriter(reader,0);after.accept(out);byte[] data=out.toByteArray();ClassNode verified=new ClassNode(ASM8);new ClassReader(data).accept(verified,0);
        for(int i=0;i<before.methods.size();i++){MethodNode a=before.methods.get(i),b=verified.methods.get(i);if(!a.name.equals(b.name)||!a.desc.equals(b.desc)||a.access!=b.access)throw new IllegalStateException("ABI changed");if(!a.name.equals("paint")){if(!Arrays.equals(bytes(a),bytes(b)))throw new IllegalStateException("Non-paint method changed");preserved++;}}
        Files.write(Paths.get(args[1]),data);System.out.println("FONT_PREVIEW_PATCH colors=4 businessMethodsPreserved="+preserved+" ABI=unchanged");
    }
}

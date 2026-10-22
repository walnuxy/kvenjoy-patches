import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;

/** Only changes the three shared JFileChooser constructor sites; preserves all other code. */
public final class PatchFileChoosers {
    public static void main(String[] args) throws Exception {
        final String base = "javax/swing/JFileChooser";
        final String replacement = "com/kenjoy/kenjoycnc/PathFileChooser";
        final int[] sites = new int[2];
        ClassReader reader = new ClassReader(Files.readAllBytes(Paths.get(args[0])));
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM8, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW && type.equals(base)) { type = replacement; sites[0]++; }
                        super.visitTypeInsn(opcode, type);
                    }
                    public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                        if (opcode == Opcodes.INVOKESPECIAL && owner.equals(base) && method.equals("<init>")) {
                            if (!desc.equals("()V")) throw new IllegalStateException("Unknown file chooser constructor");
                            owner = replacement; sites[1]++;
                        }
                        super.visitMethodInsn(opcode, owner, method, desc, itf);
                    }
                };
            }
        }, 0);
        if (sites[0] != 3 || sites[1] != 3) throw new IllegalStateException("Expected exactly three shared chooser sites");
        Files.write(Paths.get(args[1]), writer.toByteArray());
        System.out.println("Patched three shared file chooser constructor sites");
    }
}

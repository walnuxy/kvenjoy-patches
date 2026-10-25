import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Synthetic 0.020 -> 0.021 migration helper; selected JAR supplies UpdatePatcher. */
public final class UpgradeChainChecks {
    static final String ID="A1B2C3D4E5F60718A1B2C3D4E5F60718";
    static final byte[] MACHINE=ID.substring(0,16).getBytes(StandardCharsets.US_ASCII);
    static final byte[] KEY="TEST-PACKAGE-KEY".getBytes(StandardCharsets.US_ASCII);
    public static void main(String[] args) throws Exception {
        Path root=Paths.get(args[1]);
        if (args[0].equals("fixture")) {
            Files.createDirectories(root.resolve("gcodeFonts"));
            Map<String,byte[]> source=FontPatcher.unzip(Files.readAllBytes(Paths.get(args[2])));
            Map<String,byte[]> encrypted=new LinkedHashMap<String,byte[]>();
            for (Map.Entry<String,byte[]> e:source.entrySet())
                encrypted.put(e.getKey(),e.getKey().endsWith(".class")?FontPatcher.encrypt(FontPatcher.encrypt(e.getValue(),KEY),MACHINE):e.getValue());
            Files.write(root.resolve("core"),FontPatcher.encrypt(FontPatcher.zip(encrypted),MACHINE));
            Files.write(root.resolve("licene"),FontPatcher.encrypt(("{\"machineId\":\""+ID+"\",\"key\":\""+Base64.getEncoder().encodeToString(KEY)+"\"}").getBytes(StandardCharsets.UTF_8),MACHINE));
            Files.write(root.resolve("info.txt"),ID.getBytes(StandardCharsets.US_ASCII));
            Files.write(root.resolve("personal-note.kjnote"),new byte[]{9,8,7});
        } else if (args[0].equals("apply")) {
            new UpdatePatcher(root).apply(false);
        } else if (args[0].equals("rollback")) {
            new UpdatePatcher(root).rollback();
        } else throw new IllegalArgumentException(args[0]);
        System.out.println(args[0]+" OK");
    }
}

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Synthetic-identity regression tests; never use real license or installed core. */
public final class FontPatcherTest {
    static final String ID = "A1B2C3D4E5F60718A1B2C3D4E5F60718";
    static final byte[] MACHINE = ID.substring(0, 16).getBytes(StandardCharsets.US_ASCII);
    static final byte[] PACKAGE = "TEST-PACKAGE-KEY".getBytes(StandardCharsets.US_ASCII);
    static int checks = 0;

    interface Action { void run() throws Exception; }

    static void yes(boolean ok, String name) throws Exception {
        if (!ok) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }

    static void fails(Action action, String name) throws Exception {
        try { action.run(); }
        catch (Exception expected) {
            checks++;
            System.out.println("PASS " + name);
            return;
        }
        throw new AssertionError("Should fail: " + name);
    }

    static Map<String,byte[]> readJar(Path path) throws Exception {
        Map<String,byte[]> entries = new LinkedHashMap<String,byte[]>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry e;
            while ((e=in.getNextEntry())!=null) {
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n=in.read(buf))!=-1) out.write(buf,0,n);
                entries.put(e.getName(),out.toByteArray());
            }
        }
        return entries;
    }

    static byte[] core(Map<String,byte[]> plain) throws Exception {
        Map<String,byte[]> encoded=new LinkedHashMap<String,byte[]>();
        for (Map.Entry<String,byte[]> e:plain.entrySet()) {
            byte[] value=e.getValue();
            if (e.getKey().endsWith(".class"))
                value=FontPatcher.encrypt(FontPatcher.encrypt(value,PACKAGE),MACHINE);
            encoded.put(e.getKey(),value);
        }
        return FontPatcher.encrypt(FontPatcher.zip(encoded),MACHINE);
    }

    static Path fixture(Path path, Map<String,byte[]> plain) throws Exception {
        Files.createDirectories(path);
        String json="{\"machineId\":\""+ID+"\",\"key\":\""+
            Base64.getEncoder().encodeToString(PACKAGE)+"\"}";
        Files.write(path.resolve("licene"),
            FontPatcher.encrypt(json.getBytes(StandardCharsets.UTF_8),MACHINE));
        Files.write(path.resolve("info.txt"),
            ("Only synthetic identity: "+ID+"\n").getBytes(StandardCharsets.US_ASCII));
        Files.write(path.resolve("core"),core(plain));
        return path;
    }

    static Properties receipt(Path path) throws Exception {
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(path.resolve(".font-ui-patch/receipt.properties"))) {
            p.load(in);
        }
        return p;
    }

    static byte[] replaceOne(byte[] src) {
        byte[] copy=src.clone();
        copy[copy.length-9]^=1;
        return copy;
    }

    public static void main(String[] args) throws Exception {
        Path originalJar=Paths.get(args[0]), payloadJar=Paths.get(args[1]), root=Paths.get(args[2]);
        Path legacyClass=Paths.get(args[3]);
        Map<String,byte[]> original=readJar(originalJar),payload=readJar(payloadJar);
        yes(payload.size()==7,"exactly seven font/path/category payload classes");
        Path target=fixture(root.resolve("valid"),original);
        byte[] before=Files.readAllBytes(target.resolve("core"));
        byte[] license=Files.readAllBytes(target.resolve("licene"));
        byte[] identity=Files.readAllBytes(target.resolve("info.txt"));
        Map<String,byte[]> beforeEntries=FontPatcher.unzip(FontPatcher.decrypt(before,MACHINE));
        FontPatcher patcher=new FontPatcher(target);
        yes(!patcher.inspect().installed,"original target compatible");
        yes(patcher.check().contains("可以安装"),"check is read-only");
        yes(!Files.exists(target.resolve(".font-ui-patch")),"check creates no backup");
        patcher.apply();
        byte[] installed=Files.readAllBytes(target.resolve("core"));
        yes(!Arrays.equals(before,installed),"core changed after apply");
        yes(patcher.inspect().installed,"installed result recognized");
        Map<String,byte[]> afterEntries=FontPatcher.unzip(FontPatcher.decrypt(installed,MACHINE));
        yes(afterEntries.size()==beforeEntries.size()+5,"only three font inner classes and two helpers added");
        boolean untouched=true;
        for(String name:beforeEntries.keySet()) {
            if (!name.equals(FontPatcher.ENTRY) && !name.equals(FontPatcher.COMMON) &&
                !Arrays.equals(beforeEntries.get(name),afterEntries.get(name))) untouched=false;
        }
        yes(untouched,"all 513 unrelated original core entries remain byte-identical");
        for(String name:FontPatcher.NAMES)
            yes(Arrays.equals(FontPatcher.decrypt(FontPatcher.decrypt(afterEntries.get(name),MACHINE),PACKAGE),
                payload.get(name)), "installed payload " + name);

        Properties p=receipt(target);
        Path backup=target.resolve(".font-ui-patch").resolve(p.getProperty("backup"));
        yes(Files.isRegularFile(backup),"backup exists");
        yes(Arrays.equals(before,Files.readAllBytes(backup)),"backup is exact original");
        yes(p.getProperty("before").equals(FontPatcher.sha(before)) &&
            p.getProperty("after").equals(FontPatcher.sha(installed)),"receipt hashes correct");
        patcher.apply();
        yes(Arrays.equals(installed,Files.readAllBytes(target.resolve("core"))),"repeated apply is no-op");

        Files.write(target.resolve("core"),replaceOne(installed));
        fails(new Action(){public void run() throws Exception{patcher.rollback();}},
              "rollback refuses external core changes");
        Files.write(target.resolve("core"),installed);
        byte[] safeBackup=Files.readAllBytes(backup);
        Files.write(backup,replaceOne(safeBackup));
        fails(new Action(){public void run() throws Exception{patcher.rollback();}},
              "rollback refuses corrupted backup");
        Files.write(backup,safeBackup);
        patcher.rollback();
        yes(Arrays.equals(before,Files.readAllBytes(target.resolve("core"))),"rollback exact byte restore");
        yes(Arrays.equals(license,Files.readAllBytes(target.resolve("licene"))),"license never modified");
        yes(Arrays.equals(identity,Files.readAllBytes(target.resolve("info.txt"))),"identity never modified");
        yes(!patcher.inspect().installed,"rolled back original is compatible");

        Map<String,byte[]> unknown=new LinkedHashMap<String,byte[]>(original);
        unknown.put(FontPatcher.ENTRY,replaceOne(original.get(FontPatcher.ENTRY)));
        Path unknownDir=fixture(root.resolve("unknown"),unknown);
        byte[] unknownCore=Files.readAllBytes(unknownDir.resolve("core"));
        fails(new Action(){public void run() throws Exception{new FontPatcher(unknownDir).apply();}},
              "unknown class version rejected");
        yes(Arrays.equals(unknownCore,Files.readAllBytes(unknownDir.resolve("core"))) &&
            !Files.exists(unknownDir.resolve(".font-ui-patch")),"unknown version causes no writes");

        Map<String,byte[]> incomplete=new LinkedHashMap<String,byte[]>(original);
        incomplete.put(FontPatcher.ENTRY,payload.get(FontPatcher.ENTRY));
        Path partial=fixture(root.resolve("partial"),incomplete);
        fails(new Action(){public void run() throws Exception{new FontPatcher(partial).check();}},
              "partial installed patch rejected");

        Path missing=fixture(root.resolve("missing-license"),original);
        Files.delete(missing.resolve("licene"));
        fails(new Action(){public void run() throws Exception{new FontPatcher(missing).check();}},
              "missing target license rejected");

        Map<String,byte[]> oldV2=new LinkedHashMap<String,byte[]>(original);
        oldV2.put(FontPatcher.ENTRY,Files.readAllBytes(legacyClass));
        Path previous=fixture(root.resolve("legacy-v2"),oldV2);
        byte[] previousCore=Files.readAllBytes(previous.resolve("core"));
        FontPatcher upgrade=new FontPatcher(previous);
        yes(!upgrade.inspect().installed,"legacy v2 font-only version compatible");
        upgrade.apply();
        yes(upgrade.inspect().installed,"legacy v2 upgraded to merged UI");
        upgrade.rollback();
        yes(Arrays.equals(previousCore,Files.readAllBytes(previous.resolve("core"))),
            "legacy v2 restored byte-for-byte");

        Map<String,byte[]> incompatible=new LinkedHashMap<String,byte[]>(original);
        String dependency="com/kenjoy/kenjoycnc/fonts/s.class";
        incompatible.put(dependency,replaceOne(original.get(dependency)));
        Path changedDependency=fixture(root.resolve("unknown-dependency"),incompatible);
        fails(new Action(){public void run() throws Exception{
            new FontPatcher(changedDependency).apply();
        }}, "unknown dependent class rejected");
        yes(!Files.exists(changedDependency.resolve(".font-ui-patch")),
            "incompatible dependency causes no writes");
        Map<String,byte[]> priorUi=new LinkedHashMap<String,byte[]>(original);
        try (ZipFile release = new ZipFile(args[4])) {
            try (ZipInputStream jar = new ZipInputStream(release.getInputStream(release.getEntry("font-patcher.jar")))) {
                ZipEntry entry;
                while ((entry=jar.getNextEntry())!=null) {
                    if (!entry.getName().startsWith("payload/")) continue;
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    byte[] buffer=new byte[8192];int n;
                    while ((n=jar.read(buffer))!=-1) out.write(buffer,0,n);
                    priorUi.put(entry.getName().substring("payload/".length()),out.toByteArray());
                }
            }
        }
        Path oldUiDir=fixture(root.resolve("font-ui-v1"),priorUi);
        byte[] oldUiCore=Files.readAllBytes(oldUiDir.resolve("core"));
        FontPatcher uiUpgrade=new FontPatcher(oldUiDir);
        yes(!uiUpgrade.inspect().installed,"complete font UI v1 accepted for upgrade");
        uiUpgrade.apply();
        yes(uiUpgrade.inspect().installed,"font UI v1 upgrades to combined font/path patch");
        uiUpgrade.apply();
        yes(uiUpgrade.inspect().installed,"repeat current-version install remains a no-op");
        uiUpgrade.rollback();
        yes(Arrays.equals(oldUiCore,Files.readAllBytes(oldUiDir.resolve("core"))),"font UI v1 restored byte-for-byte");
        priorUi.remove(FontPatcher.NAMES[1]);
        Path partialUi=fixture(root.resolve("partial-ui-v1"),priorUi);
        byte[] partialBefore=Files.readAllBytes(partialUi.resolve("core"));
        fails(new Action(){public void run() throws Exception{new FontPatcher(partialUi).apply();}},
              "partial font UI v1 refused for upgrade");
        yes(Arrays.equals(partialBefore,Files.readAllBytes(partialUi.resolve("core")))
                && !Files.exists(partialUi.resolve(".font-ui-patch")),"partial v1 refusal causes no writes");
        Map<String,byte[]> v2=new LinkedHashMap<String,byte[]>(original);
        try (ZipFile release = new ZipFile(args[5])) {
            try (ZipInputStream jar = new ZipInputStream(release.getInputStream(release.getEntry("font-patcher.jar")))) {
                ZipEntry entry;
                while ((entry=jar.getNextEntry())!=null) {
                    if (!entry.getName().startsWith("payload/")) continue;
                    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                    while((n=jar.read(buffer))!=-1)out.write(buffer,0,n);
                    v2.put(entry.getName().substring(8),out.toByteArray());
                }
            }
        }
        Path v2Dir=fixture(root.resolve("font-ui-v2"),v2);byte[] v2Before=Files.readAllBytes(v2Dir.resolve("core"));
        FontPatcher v2Upgrade=new FontPatcher(v2Dir);
        yes(!v2Upgrade.inspect().installed,"font UI v2 needs path enhancement");
        v2Upgrade.apply();yes(v2Upgrade.inspect().installed,"font UI v2 upgrades to combined font/path v3");
        v2Upgrade.rollback();yes(Arrays.equals(v2Before,Files.readAllBytes(v2Dir.resolve("core"))),"v3 rollback restores exact v2 core");
        Map<String,byte[]> missingPath=new LinkedHashMap<String,byte[]>(original);missingPath.putAll(payload);
        missingPath.remove(FontPatcher.PATH_CHOOSER);
        Path brokenPath=fixture(root.resolve("missing-path-helper"),missingPath);
        fails(new Action(){public void run() throws Exception{new FontPatcher(brokenPath).apply();}},"missing path helper rejected");
        Map<String,byte[]> unknownCommon=new LinkedHashMap<String,byte[]>(original);
        unknownCommon.put(FontPatcher.COMMON,replaceOne(original.get(FontPatcher.COMMON)));
        Path unknownCommonDir=fixture(root.resolve("unknown-common"),unknownCommon);
        fails(new Action(){public void run() throws Exception{new FontPatcher(unknownCommonDir).apply();}},"modified common chooser rejected");
        Map<String,byte[]> v3=new LinkedHashMap<String,byte[]>(original);
        try (ZipFile release=new ZipFile(args[6])) {
            try (ZipInputStream jar=new ZipInputStream(release.getInputStream(release.getEntry("font-patcher.jar")))) {
                ZipEntry entry;
                while((entry=jar.getNextEntry())!=null) {
                    if(!entry.getName().startsWith("payload/"))continue;
                    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                    while((n=jar.read(buffer))!=-1)out.write(buffer,0,n);
                    v3.put(entry.getName().substring(8),out.toByteArray());
                }
            }
        }
        Path v3Dir=fixture(root.resolve("font-ui-v3"),v3);byte[] v3Before=Files.readAllBytes(v3Dir.resolve("core"));
        FontPatcher v3Upgrade=new FontPatcher(v3Dir);
        yes(!v3Upgrade.inspect().installed,"v3 is eligible for folder categories");
        v3Upgrade.apply();yes(v3Upgrade.inspect().installed,"v3 upgrades to v4 categories");
        v3Upgrade.rollback();yes(Arrays.equals(v3Before,Files.readAllBytes(v3Dir.resolve("core"))),"category upgrade rollback restores v3 exactly");
        Map<String,byte[]> brokenFolder=new LinkedHashMap<String,byte[]>(original);brokenFolder.putAll(payload);brokenFolder.remove(FontPatcher.FOLDER_FILTER);
        Path brokenFolderDir=fixture(root.resolve("missing-folder-helper"),brokenFolder);
        fails(new Action(){public void run() throws Exception{new FontPatcher(brokenFolderDir).apply();}},"missing category helper rejected");
        System.out.println("Synthetic regression checks passed: " + checks);
    }
}

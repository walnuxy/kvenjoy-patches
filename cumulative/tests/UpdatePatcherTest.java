import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Every historical fixture is encrypted with synthetic target-local identity, never actual credentials. */
public final class UpdatePatcherTest {
    static final String ID="A1B2C3D4E5F60718A1B2C3D4E5F60718";
    static final byte[] MACHINE=ID.substring(0,16).getBytes(StandardCharsets.US_ASCII),KEY="TEST-PACKAGE-KEY".getBytes(StandardCharsets.US_ASCII);
    static int checks,cases;
    interface Action{void run()throws Exception;}
    static void yes(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    static void fails(Action work,String message)throws Exception{try{work.run();}catch(Exception expected){yes(true,message);return;}throw new AssertionError("Expected rejection: "+message);}
    static byte[] encrypted(Map<String,byte[]> plain)throws Exception{
        Map<String,byte[]> entries=new LinkedHashMap<String,byte[]>();for(String n:plain.keySet())entries.put(n,n.endsWith(".class")?FontPatcher.encrypt(FontPatcher.encrypt(plain.get(n),KEY),MACHINE):plain.get(n));return FontPatcher.encrypt(FontPatcher.zip(entries),MACHINE);
    }
    static Path fixture(Path root,String mode,Map<String,byte[]> plain)throws Exception{
        Files.createDirectories(root);if(mode.equals("standalone")){Files.createDirectories(root.resolve("app"));Files.write(root.resolve("app/app.jar"),FontPatcher.zip(plain));}
        else{Files.write(root.resolve("core"),encrypted(plain));Files.write(root.resolve("licene"),FontPatcher.encrypt(("{\"machineId\":\""+ID+"\",\"key\":\""+Base64.getEncoder().encodeToString(KEY)+"\"}").getBytes(StandardCharsets.UTF_8),MACHINE));Files.write(root.resolve("info.txt"),ID.getBytes(StandardCharsets.US_ASCII));}
        Files.createDirectories(root.resolve("gcodeFonts"));Files.write(root.resolve("gcodeFonts/personal.gfont"),new byte[]{1,3,5,7});Files.write(root.resolve("personal-note.kjnote"),new byte[]{9,8,7});return root;
    }
    static Map<String,String> snapshot(Path dir)throws Exception{
        Map<String,String> result=new TreeMap<String,String>();try(java.util.stream.Stream<Path> walk=Files.walk(dir)){for(Iterator<Path> i=walk.iterator();i.hasNext();){Path p=i.next();if(Files.isRegularFile(p)&&!p.startsWith(dir.resolve(UpdatePatcher.STATE)))result.put(dir.relativize(p).toString(),FontPatcher.sha(Files.readAllBytes(p)));}}return result;
    }
    static void remove(Path root)throws Exception{try(java.util.stream.Stream<Path> walk=Files.walk(root)){for(Path p:(Iterable<Path>)walk.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
    public static void main(String[] args)throws Exception{
        Path work=Paths.get(args[1]);Files.createDirectories(work);Path baseline=null;
        for(String line:Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8)){
            String[] f=line.split("\t");String label=f[0],mode=f[1];Path source=Paths.get(f[2]);if(label.equals("original"))baseline=source;
            Map<String,byte[]> plain=FontPatcher.unzip(Files.readAllBytes(source));Path root=fixture(work.resolve(label),mode,plain);Map<String,String> before=snapshot(root);
            UpdatePatcher tool=new UpdatePatcher(root);yes(tool.check().contains("0.020"),label+" recognized");UpdatePatcher.Data data=tool.inspect();Map<String,byte[]> encodedBefore=data.entries;
            tool.apply(false);data=tool.inspect();final UpdatePatcher.Data updated=data;yes(data.payload.keySet().stream().allMatch(n->{try{return Arrays.equals(updated.payload.get(n),n.endsWith(".class")?tool.decode(updated,updated.entries.get(n)):updated.entries.get(n));}catch(Exception e){throw new RuntimeException(e);}}),label+" latest payload");
            boolean untouched=true;for(String n:encodedBefore.keySet())if(!data.payload.containsKey(n)&&!Arrays.equals(encodedBefore.get(n),data.entries.get(n)))untouched=false;yes(untouched,label+" unrelated entries unchanged");
            Path record=root.resolve(UpdatePatcher.STATE+"/receipt.properties");byte[] receipt=Files.readAllBytes(record);Map<String,String> installed=snapshot(root);tool.apply(false);yes(Arrays.equals(receipt,Files.readAllBytes(record))&&installed.equals(snapshot(root)),label+" repeat apply preserves backup");
            tool.rollback();yes(before.equals(snapshot(root)),label+" exact rollback core/options/personal data");tool.rollback();yes(before.equals(snapshot(root)),label+" repeat rollback");cases++;remove(root);
        }
        Map<String,byte[]> original=FontPatcher.unzip(Files.readAllBytes(baseline));
        Path root=fixture(work.resolve("failures"),"vendor",original);byte[] oldOptions="-Xmx768m\r\n-Dcustom.option=keep\r\n-Dkvenjoy.processingDpi=144\r\n".getBytes(StandardCharsets.UTF_8);Files.write(root.resolve(UpdatePatcher.OPTIONS),oldOptions);Map<String,String> before=snapshot(root);UpdatePatcher tool=new UpdatePatcher(root);
        tool.beforeWrite=i->{if(i==1)throw new java.io.IOException("simulated options write failure");};fails(()->tool.apply(false),"core/options write failure propagated");yes(before.equals(snapshot(root)),"partial apply compensates exact bytes");tool.beforeWrite=i->{};
        tool.apply(false);String options=new String(Files.readAllBytes(root.resolve(UpdatePatcher.OPTIONS)),StandardCharsets.UTF_8);yes(options.contains("-Xmx768m")&&options.contains("custom.option=keep")&&options.contains("processingDpi=144"),"unrelated options and previous custom processing DPI retained");
        byte[] receipt=Files.readAllBytes(root.resolve(UpdatePatcher.STATE+"/receipt.properties"));tool.beforeWrite=i->{throw new java.io.IOException("repeat apply must not write");};tool.apply(false);yes(Arrays.equals(receipt,Files.readAllBytes(root.resolve(UpdatePatcher.STATE+"/receipt.properties"))),"repeat apply does not enter mutation branch");tool.beforeWrite=i->{};
        Files.write(root.resolve(UpdatePatcher.OPTIONS),"-Xmx999m".getBytes(StandardCharsets.UTF_8));fails(tool::rollback,"rollback refuses newer startup options");Files.write(root.resolve(UpdatePatcher.OPTIONS),options.getBytes(StandardCharsets.UTF_8));tool.rollback();yes(before.equals(snapshot(root)),"existing startup options restored byte for byte");
        Map<String,byte[]> modified=new LinkedHashMap<String,byte[]>(original);modified.put("com/kenjoy/kenjoycnc/KenjoyDraw.class",new byte[]{0,1,2});Files.write(root.resolve("core"),encrypted(modified));Map<String,String> unknown=snapshot(root);fails(()->tool.apply(false),"unknown main component rejected");yes(unknown.equals(snapshot(root)),"unknown version refusal leaves installation untouched");
        Files.write(root.resolve("core"),encrypted(original));tool.downloader=(url,size)->{throw new java.io.IOException("simulated offline");};Map<String,String> offline=snapshot(root);fails(()->tool.apply(true),"missing font download fails before commit");yes(offline.equals(snapshot(root)),"offline failure changes no target files");remove(root);
        Path fonts=fixture(work.resolve("fonts"),"vendor",original);Map<String,String> fontBefore=snapshot(fonts);UpdatePatcher fontTool=new UpdatePatcher(fonts);Map<String,byte[]> downloads=new HashMap<String,byte[]>();
        Map<String,Path> byHash=new HashMap<String,Path>();try(java.util.stream.Stream<Path> walk=Files.walk(Paths.get(args[2]))){for(Iterator<Path> i=walk.iterator();i.hasNext();){Path p=i.next();if(Files.isRegularFile(p)&&p.toString().endsWith(".gfont"))byHash.put(FontPatcher.sha(Files.readAllBytes(p)),p);}}
        String index=new String(FontPatcher.read(FontPatcher.resource("fonts/chinese-downloads.tsv")),StandardCharsets.UTF_8);for(String line:index.split("\n"))if(!line.trim().isEmpty()){String[] f=line.trim().split("\t");requireFixture(byHash.containsKey(f[0]),"Missing already downloaded reference font");downloads.put(f[2],Files.readAllBytes(byHash.get(f[0])));}
        fontTool.downloader=(url,size)->downloads.get(url);fontTool.progress=s->{};fontTool.apply(true);yes(Files.walk(fonts.resolve("gcodeFonts")).filter(p->p.toString().endsWith(".gfont")).count()==86,"cumulative install adds 85 fonts and preserves personal font");
        Map<String,String> fontAfter=snapshot(fonts);byte[] fontReceipt=Files.readAllBytes(fonts.resolve(UpdatePatcher.STATE+"/receipt.properties"));fontTool.apply(true);yes(fontAfter.equals(snapshot(fonts))&&Arrays.equals(fontReceipt,Files.readAllBytes(fonts.resolve(UpdatePatcher.STATE+"/receipt.properties"))),"complete bundle repeat install is idempotent");fontTool.rollback();yes(fontBefore.equals(snapshot(fonts)),"complete bundle rollback exactly restores fonts and core");remove(fonts);
        Files.write(work.resolve("COMPLETE.txt"),(cases+" historical cases; "+checks+" assertions; runtime="+System.getProperty("java.version")).getBytes(StandardCharsets.UTF_8));System.out.println("COMPLETE cases="+cases+" checks="+checks);
    }
    static void requireFixture(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

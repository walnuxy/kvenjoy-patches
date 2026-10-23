import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.swing.*;

/** Local font UI patcher. Never modifies the license, launcher or machine identity. */
public final class FontPatcher {
    static final String ENTRY = "com/kenjoy/kenjoycnc/fonts/r.class";
    static final String STATE = ".font-ui-patch";
    static final byte[] CONFUSE = "lindkkulovelliby".getBytes(StandardCharsets.US_ASCII);
    static final SecureRandom RANDOM = platformRandom();
    static SecureRandom platformRandom() {
        try { return SecureRandom.getInstance("Windows-PRNG"); }
        catch (NoSuchAlgorithmException e) { return new SecureRandom(); }
    }
    static final String[] FONTS = {ENTRY, ENTRY.replace("r.class", "r$1.class"),
        ENTRY.replace("r.class", "r$2.class"), ENTRY.replace("r.class", "r$3.class")};
    static final String COMMON = "com/kenjoy/kenjoycnc/L.class";
    static final String PATH_CHOOSER = "com/kenjoy/kenjoycnc/PathFileChooser.class";
    static final String FOLDER_FILTER = "com/kenjoy/kenjoycnc/fonts/FontFolderFilter.class";
    static final String[] NAMES = {FONTS[0], FONTS[1], FONTS[2], FONTS[3], COMMON, PATH_CHOOSER, FOLDER_FILTER};
    final Path dir;
    final Properties expected = new Properties();
    final Map<String,byte[]> replacements = new LinkedHashMap<String,byte[]>();
    interface Downloader { byte[] get(String source, int size) throws Exception; }
    interface Progress { void show(String message); }
    final String downloadIndex;
    final Downloader downloader;
    Progress progress = message -> System.out.println(message);

    FontPatcher(Path path) throws Exception {
        this(path, new String(read(resource("fonts/chinese-downloads.tsv")), StandardCharsets.UTF_8),
            (source,size) -> download(source,size,path.toAbsolutePath()));
    }
    FontPatcher(Path path, String index, Downloader fetch) throws Exception {
        dir = path.toRealPath();
        downloadIndex=index; downloader=fetch;
        try (InputStream in = resource("compatibility.properties")) { expected.load(in); }
        for (String name : NAMES) {
            byte[] data = read(resource("payload/" + name));
            require(sha(data).equals(expected.getProperty("payload." + name)), "补丁文件校验失败");
            replacements.put(name, data);
        }
    }
    static InputStream resource(String name) throws IOException {
        InputStream in = FontPatcher.class.getResourceAsStream("/" + name);
        if (in == null) throw new IOException("补丁资源缺失：" + name);
        return in;
    }
    static byte[] read(InputStream source) throws IOException {
        try (InputStream in = source; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
    static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
    static String sha(byte[] data) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) out.append(String.format("%02x", b & 255));
        return out.toString();
    }
    static final class PackageData {
        byte[] before, machine, pkg;
        Map<String,byte[]> entries;
        boolean installed;
    }
    PackageData inspect() throws Exception {
        require(Files.isRegularFile(dir.resolve("core")) && !Files.isSymbolicLink(dir.resolve("core")), "所选目录中没有普通 core 文件");
        require(Files.isRegularFile(dir.resolve("licene")) && !Files.isSymbolicLink(dir.resolve("licene")), "所选目录中没有现有 licene 文件，请先正常安装并授权软件");
        require(Files.isRegularFile(dir.resolve("info.txt")) && !Files.isSymbolicLink(dir.resolve("info.txt")), "缺少普通 info.txt，无法读取本机原有识别码");
        require(Files.size(dir.resolve("core")) <= 32L*1024*1024 && Files.size(dir.resolve("licene")) <= 65536 &&
            Files.size(dir.resolve("info.txt")) <= 262144, "目标文件大小异常，未修改文件");
        byte[] core = Files.readAllBytes(dir.resolve("core"));
        byte[] license = Files.readAllBytes(dir.resolve("licene"));
        Matcher ids = Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{32}(?![0-9a-f])")
            .matcher(new String(Files.readAllBytes(dir.resolve("info.txt")), StandardCharsets.ISO_8859_1));
        PackageData found = null;
        while (ids.find() && found == null) {
            String identity = ids.group();
            try {
                byte[] machine = identity.substring(0,16).getBytes(StandardCharsets.US_ASCII);
                String json = new String(decrypt(license, machine), StandardCharsets.UTF_8);
                require(identity.equalsIgnoreCase(jsonString(json, "machineId")), "本机数据不匹配");
                byte[] pkg = Base64.getDecoder().decode(jsonString(json,"key"));
                require(pkg.length == 16, "数据格式不兼容");
                Map<String,byte[]> entries = unzip(decrypt(core, machine));
                require(entries.containsKey(ENTRY), "core 缺少字体对话框");
                found = new PackageData(); found.before=core; found.machine=machine; found.pkg=pkg; found.entries=entries;
            } catch (Exception ignored) { /* Try only identities already recorded by this installation. */ }
        }
        require(found != null, "无法读取目标电脑的原有 core/授权数据。请确认选择正确目录且原软件能够正常运行；未修改文件。");
        for (String key : expected.stringPropertyNames()) {
            if (!key.startsWith("dependency.")) continue;
            String name = key.substring("dependency.".length());
            byte[] encrypted = found.entries.get(name);
            require(encrypted != null && expected.getProperty(key).equals(sha(decrypt(decrypt(encrypted, found.machine), found.pkg))),
                "软件版本不兼容：" + name + "；未修改文件");
        }
        String current = sha(decrypt(decrypt(found.entries.get(ENTRY), found.machine), found.pkg));
        String common = sha(decrypt(decrypt(found.entries.get(COMMON), found.machine), found.pkg));
        boolean pathsInstalled = common.equals(expected.getProperty("payload." + COMMON));
        if (pathsInstalled) {
            byte[] helper = found.entries.get(PATH_CHOOSER);
            require(helper != null && sha(decrypt(decrypt(helper, found.machine), found.pkg))
                .equals(expected.getProperty("payload." + PATH_CHOOSER)), "路径补丁不完整；未修改文件");
        } else {
            require(common.equals(expected.getProperty("original.common")) && !found.entries.containsKey(PATH_CHOOSER),
                "公共文件选择器版本未知；未修改文件");
        }
        if (current.equals(expected.getProperty("patched.r"))) {
            for (String name : FONTS) {
                byte[] encrypted = found.entries.get(name);
                require(encrypted != null &&
                    Arrays.equals(decrypt(decrypt(encrypted, found.machine), found.pkg), replacements.get(name)),
                    "检测到不完整或被改动的字体补丁；未修改文件");
            }
            byte[] folder = found.entries.get(FOLDER_FILTER);
            require(folder != null && sha(decrypt(decrypt(folder, found.machine), found.pkg))
                .equals(expected.getProperty("payload." + FOLDER_FILTER)), "字体分类补丁不完整；未修改文件");
            found.installed = pathsInstalled;
        } else if (current.equals(expected.getProperty("legacy-ui-v4." + ENTRY))
                || current.equals(expected.getProperty("legacy-ui-005." + ENTRY))) {
            String prefix = current.equals(expected.getProperty("legacy-ui-005." + ENTRY)) ? "legacy-ui-005." : "legacy-ui-v4.";
            for (String name : NAMES) {
                byte[] encrypted = found.entries.get(name);
                require(encrypted != null && sha(decrypt(decrypt(encrypted, found.machine), found.pkg))
                    .equals(expected.getProperty(prefix + name)),
                    "旧版字体分类补丁不完整或已被改动；未修改文件");
            }
            found.installed = false;
        } else if (current.equals(expected.getProperty("legacy-ui." + ENTRY))
                || current.equals(expected.getProperty("legacy-ui-v2." + ENTRY))) {
            String prefix = current.equals(expected.getProperty("legacy-ui." + ENTRY)) ? "legacy-ui." : "legacy-ui-v2.";
            for (String name : FONTS) {
                byte[] encrypted = found.entries.get(name);
                require(encrypted != null && sha(decrypt(decrypt(encrypted, found.machine), found.pkg))
                    .equals(expected.getProperty(prefix + name)),
                    "旧版字体补丁不完整或已被改动；未修改文件");
            }
            require(!found.entries.containsKey(FOLDER_FILTER), "旧版中存在未知分类组件；未修改文件");
            found.installed = false;
        } else {
            require(Arrays.asList(expected.getProperty("compatible.r", "").split(",")).contains(current),
                "字体对话框版本未知，不能覆盖；未修改文件");
            for (int i = 1; i < FONTS.length; i++)
                require(!found.entries.containsKey(NAMES[i]),
                    "检测到未知的字体内部类，不能安全覆盖；未修改文件");
            require(!found.entries.containsKey(FOLDER_FILTER), "原版中存在未知分类组件；未修改文件");
            found.installed = false;
        }
        return found;
    }
    static String jsonString(String json, String key) throws IOException {
        Matcher m = Pattern.compile("\""+key+"\"\\s*:\\s*\"([^\"\\\\]*)\"").matcher(json);
        require(m.find(), "现有授权数据格式不兼容"); return m.group(1);
    }
    static Map<String,byte[]> unzip(byte[] bytes) throws Exception {
        require(bytes.length>4 && bytes[0]=='P' && bytes[1]=='K',"core 内容不是兼容的 ZIP");
        Map<String,byte[]> entries=new LinkedHashMap<String,byte[]>();
        long total=0;
        try (ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e; byte[] buffer=new byte[8192];
            while ((e=in.getNextEntry())!=null) {
                require(!entries.containsKey(e.getName()),"core 存在重复条目");
                ByteArrayOutputStream out=new ByteArrayOutputStream(); int n;
                while ((n=in.read(buffer))!=-1) {
                    total+=n;
                    require(out.size()+n<=16*1024*1024 && total<=64L*1024*1024,
                        "core 内部大小异常，未修改文件");
                    out.write(buffer,0,n);
                }
                entries.put(e.getName(),out.toByteArray());
            }
        }
        return entries;
    }
    static byte[] zip(Map<String,byte[]> entries) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try (ZipOutputStream z=new ZipOutputStream(out)) {
            for (Map.Entry<String,byte[]> e:entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey())); z.write(e.getValue()); z.closeEntry();
            }
        }
        return out.toByteArray();
    }
    String check() throws Exception {
        PackageData data=inspect();
        return data.installed ? "已经安装本版字体与路径补丁。" : "版本兼容，可以安装字体与文件路径增强补丁。";
    }
    static final class FontChange {
        Path destination;
        Path relocateFrom;
        String resource, hash, previous, source;
        int size;
        byte[] before, prepared;
    }
    static byte[] download(String source, int size, Path temporaryRoot) throws Exception {
        require(new URL(source).getProtocol().equals("https"), "字库下载必须使用 HTTPS");
        // Vendor Java 8 blocks in its modified JCE JarVerifier during TLS setup.
        // Use Windows PowerShell/.NET HTTPS without changing that runtime.
        Path temp=temporaryRoot.resolve(".font-download-"
            +Long.toUnsignedString(RANDOM.nextLong(),16));
        Files.createDirectory(temp);
        Path output=temp.resolve("font.download"), log=temp.resolve("download.log");
        String script="$ErrorActionPreference='Stop';"
            +"[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;"
            +"$request=[Net.HttpWebRequest]::Create($env:KVFONT_URL);"
            +"$request.Timeout=20000;$request.ReadWriteTimeout=30000;$request.AllowAutoRedirect=$false;"
            +"$request.UserAgent='Kvenjoy-Font-Patcher/0.008';"
            +"$response=$request.GetResponse();$inputStream=$null;$outputStream=$null;"
            +"try {if([int]$response.StatusCode -ne 200){throw 'Unexpected HTTP response'};"
            +"$inputStream=$response.GetResponseStream();$outputStream=[IO.File]::Create($env:KVFONT_OUT);"
            +"$buffer=New-Object byte[] 16384;$total=0;$expected=[int]$env:KVFONT_SIZE;"
            +"while(($count=$inputStream.Read($buffer,0,$buffer.Length)) -gt 0){"
            +"$total+=$count;if($total -gt $expected){throw 'Download exceeds manifest size'};"
            +"$outputStream.Write($buffer,0,$count)};"
            +"if($total -ne $expected){throw 'Incomplete font download'}"
            +"} finally {if($outputStream){$outputStream.Dispose()};if($inputStream){$inputStream.Dispose()};$response.Dispose()}";
        Process process=null;
        try {
            String powershell=Paths.get(System.getenv("SystemRoot"),"System32","WindowsPowerShell","v1.0","powershell.exe").toString();
            ProcessBuilder builder=new ProcessBuilder(powershell,"-NoProfile","-NonInteractive",
                "-WindowStyle","Hidden","-Command",script);
            builder.environment().put("KVFONT_URL",source);
            builder.environment().put("KVFONT_OUT",output.toString());
            builder.environment().put("KVFONT_SIZE",Integer.toString(size));
            builder.redirectErrorStream(true).redirectOutput(log.toFile());
            process=builder.start();
            require(process.waitFor(120,java.util.concurrent.TimeUnit.SECONDS),"下载超时");
            require(process.exitValue()==0,"无法连接原始字库来源或下载中断，请检查网络后重试");
            require(Files.size(output)==size,"字库下载不完整");
            return Files.readAllBytes(output);
        } finally {
            if(process!=null && process.isAlive()) {process.destroyForcibly();process.waitFor();}
            Files.deleteIfExists(output);Files.deleteIfExists(log);Files.deleteIfExists(temp);
        }
    }
    List<FontChange> planFonts() throws Exception {
        List<FontChange> changes=new ArrayList<FontChange>();
        Path root=dir.resolve("gcodeFonts");
        Set<String> existing=new HashSet<String>();
        Map<String,Path> locations=new HashMap<String,Path>();
        if(Files.isDirectory(root)) try(java.util.stream.Stream<Path> walk=Files.walk(root)) {
            for(Iterator<Path> it=walk.iterator();it.hasNext();) {
                Path p=it.next();
                if(Files.isRegularFile(p) && p.toString().endsWith(".gfont")) {
                    String hash=sha(Files.readAllBytes(p));existing.add(hash);locations.put(hash,p);
                }
            }
        }
        try(BufferedReader in=new BufferedReader(new InputStreamReader(resource("fonts/index.tsv"),StandardCharsets.UTF_8))) {
            String line;
            while((line=in.readLine())!=null) {
                String[] fields=line.split("\t",-1);
                require(fields.length==3,"字库清单损坏");
                FontChange change=new FontChange();change.hash=fields[0];change.previous=fields[1];
                change.resource="fonts/gcodeFonts/"+fields[2];
                change.destination=root.resolve(fields[2]).normalize();
                require(change.destination.startsWith(root) && fields[2].endsWith(".gfont"),"字库路径无效");
                require(sha(read(resource(change.resource))).equals(change.hash),"字库资源校验失败："+fields[2]);
                if(Files.exists(change.destination)) {
                    change.before=Files.readAllBytes(change.destination);
                    String current=sha(change.before);
                    if(current.equals(change.hash))continue;
                    require(current.equals(change.previous),"同名字体内容不同，已保留原文件："+change.destination);
                } else if(existing.contains(change.hash)) continue;
                changes.add(change);
            }
        }
        for(String line:downloadIndex.split("\n")) {
            if(line.trim().isEmpty())continue;
            String[] fields=line.trim().split("\t",-1);
            require(fields.length==4,"中文下载清单损坏");
            FontChange change=new FontChange(); change.hash=fields[0];change.size=Integer.parseInt(fields[1]);
            change.source=fields[2];change.destination=root.resolve(fields[3]).normalize();
            require(change.hash.matches("[0-9a-f]{64}") && change.size>0 && change.size<=32*1024*1024,
                "中文下载清单校验值无效");
            require(change.destination.startsWith(root) && fields[3].startsWith("中文-手写完整（GB2312）/")
                && fields[3].endsWith(".gfont"),"中文分类路径无效");
            if(Files.exists(change.destination)) {
                require(sha(Files.readAllBytes(change.destination)).equals(change.hash),
                    "同名字体内容不同，已保留原文件："+change.destination);
                continue;
            }
            change.relocateFrom=locations.get(change.hash);
            changes.add(change);
        }
        return changes;
    }
    String applyBundle() throws Exception {
        inspect();
        List<FontChange> changes=planFonts(); // Check every font before changing core or fonts.
        int downloads=0;
        for(FontChange change:changes)if(change.source!=null && change.relocateFrom==null)downloads++;
        int current=0;
        for(FontChange change:changes) {
            if(change.source==null)change.prepared=read(resource(change.resource));
            else if(change.relocateFrom!=null)change.prepared=Files.readAllBytes(change.relocateFrom);
            else {
                progress.show("下载中文手写字库 "+(++current)+" / "+downloads+"："+change.destination.getFileName());
                try {change.prepared=downloader.get(change.source,change.size);}
                catch(Exception e) {throw new IOException("下载未完成："+change.destination.getFileName()
                    +"。请检查网络后重新安装；本次尚未修改 core 或字库。\n"+e.getMessage(),e);}
                require(change.prepared.length==change.size,"字库下载不完整："+change.destination.getFileName());
            }
            require(sha(change.prepared).equals(change.hash),"字库 SHA-256 校验失败，尚未安装："+change.destination.getFileName());
        }
        progress.show("字库校验完成，正在安装并分类……");
        String ui=apply();
        Path backup=dir.resolve(STATE).resolve("fonts-before-0.007");
        List<FontChange> done=new ArrayList<FontChange>();
        try {
            for(FontChange change:changes) {
                if(change.before!=null) {
                    require(Arrays.equals(change.before,Files.readAllBytes(change.destination)),"字体在安装期间发生变化");
                    Files.createDirectories(backup);
                    Path saved=backup.resolve(change.previous+".gfont.bak");
                    if(!Files.exists(saved))Files.write(saved,change.before,StandardOpenOption.CREATE_NEW);
                    require(sha(Files.readAllBytes(saved)).equals(change.previous),"字库备份校验失败");
                } else require(!Files.exists(change.destination),"字体在安装期间已创建");
                Files.createDirectories(change.destination.getParent());
                done.add(change);
                replace(change.destination,change.prepared);
                require(sha(Files.readAllBytes(change.destination)).equals(change.hash),"字库安装后校验失败");
                if(change.relocateFrom!=null) {
                    require(sha(Files.readAllBytes(change.relocateFrom)).equals(change.hash),"待分类字体在安装期间发生变化");
                    Files.delete(change.relocateFrom);
                }
            }
        } catch(Exception error) {
            for(FontChange change:done) {
                if(change.relocateFrom!=null && !Files.exists(change.relocateFrom))replace(change.relocateFrom,change.prepared);
                if(change.before==null)Files.deleteIfExists(change.destination);
                else replace(change.destination,change.before);
            }
            throw new IOException("字体界面已更新，但字库安装失败；本次字体改动已撤销。"+error.getMessage(),error);
        }
        return ui+"\n字库已就绪：本次新增、修复或分类 "+changes.size()+" 套。\n包含中文 18 套（其中完整手写 15 套）、英文 55 套、符号 8 套、其他语言 4 套。\n每套新增手写字库覆盖 GB2312 全部 6,763 个汉字。\n请选择“中文”分类和“中文-手写完整（GB2312）”文件夹，刷新字体列表。";
    }
    String applyWithProgress() throws Exception {
        final JDialog dialog=new JDialog((java.awt.Frame)null,"安装字库 0.008",true);
        final JLabel label=new JLabel("正在检查字库……");
        JProgressBar bar=new JProgressBar();bar.setIndeterminate(true);
        JPanel panel=new JPanel(new java.awt.BorderLayout(8,16));
        panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        panel.add(label,java.awt.BorderLayout.CENTER);panel.add(bar,java.awt.BorderLayout.SOUTH);
        dialog.setContentPane(panel);dialog.setSize(620,140);dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        Progress previous=progress;
        progress=message -> SwingUtilities.invokeLater(() -> label.setText(message));
        SwingWorker<String,Void> worker=new SwingWorker<String,Void>() {
            protected String doInBackground() throws Exception {return applyBundle();}
            protected void done(){dialog.dispose();}
        };
        try {
            SwingUtilities.invokeAndWait(() -> {worker.execute();dialog.setVisible(true);});
            try{return worker.get();}
            catch(java.util.concurrent.ExecutionException e){throw (Exception)e.getCause();}
        } finally {progress=previous;dialog.dispose();}
    }
    String apply() throws Exception {
        PackageData data=inspect();
        if (data.installed) return "已经安装本版，无需重复安装。";
        Map<String,byte[]> updated=new LinkedHashMap<String,byte[]>(data.entries);
        for (String name:NAMES) updated.put(name,encrypt(encrypt(replacements.get(name),data.pkg),data.machine));
        byte[] result=encrypt(zip(updated),data.machine);
        Map<String,byte[]> verify=unzip(decrypt(result,data.machine));
        require(verify.keySet().equals(updated.keySet()),"新文件条目校验失败");
        for (String name:verify.keySet()) {
            if (replacements.containsKey(name)) require(Arrays.equals(decrypt(decrypt(verify.get(name),data.machine),data.pkg),replacements.get(name)),"新字体类校验失败");
            else require(Arrays.equals(data.entries.get(name),verify.get(name)),"其他文件被意外修改");
        }
        require(Arrays.equals(data.before,Files.readAllBytes(dir.resolve("core"))),"core 已被其他程序修改，请退出软件后重试");
        Path state=dir.resolve(STATE);
        require(!Files.isSymbolicLink(state),"备份目录不能是符号链接");
        Files.createDirectories(state);
        String before=sha(data.before), after=sha(result);
        Path backup=state.resolve("core-"+before+".bak");
        require(!Files.isSymbolicLink(backup),"备份文件不能是符号链接");
        if (!Files.exists(backup)) Files.write(backup,data.before,StandardOpenOption.CREATE_NEW);
        require(sha(Files.readAllBytes(backup)).equals(before),"备份校验失败");
        Properties receipt=new Properties(); receipt.setProperty("before",before);receipt.setProperty("after",after);
        receipt.setProperty("backup",backup.getFileName().toString());receipt.setProperty("status","prepared");
        saveReceipt(state,receipt);
        replace(dir.resolve("core"),result);
        require(sha(Files.readAllBytes(dir.resolve("core"))).equals(after),"安装后校验失败，请用回滚恢复");
        receipt.setProperty("status","installed");saveReceipt(state,receipt);
        return "安装成功。请重新打开奎享雕刻，测试字体与载入文件路径输入。\n备份位置："+backup;
    }
    String rollback() throws Exception {
        Path state=dir.resolve(STATE), path=state.resolve("receipt.properties");
        require(!Files.isSymbolicLink(state) && !Files.isSymbolicLink(path),
            "回滚记录路径不安全，停止操作");
        require(Files.isRegularFile(path),"没有本工具的安装记录，不能自动回滚");
        Properties receipt=new Properties();try(InputStream in=Files.newInputStream(path)){receipt.load(in);}
        String before=receipt.getProperty("before"),after=receipt.getProperty("after");
        String current=sha(Files.readAllBytes(dir.resolve("core")));
        if (current.equals(before)) return "当前已是安装前的版本。";
        require(current.equals(after),"core 在安装后又发生变化，为保留其他修改，停止自动回滚");
        String filename=receipt.getProperty("backup","");
        require(filename.equals("core-"+before+".bak"),"备份记录不兼容");
        Path backupPath=state.resolve(filename);
        require(!Files.isSymbolicLink(backupPath),"备份不能是符号链接");
        byte[] backup=Files.readAllBytes(backupPath);
        require(sha(backup).equals(before),"备份校验失败");replace(dir.resolve("core"),backup);
        receipt.setProperty("status","rolled-back");saveReceipt(state,receipt);
        return "已恢复安装前的 core。请重新打开软件。";
    }
    static void saveReceipt(Path state,Properties p) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();p.store(out,"Font UI patch receipt - no credentials");
        replace(state.resolve("receipt.properties"),out.toByteArray());
    }
    static void replace(Path dest,byte[] bytes) throws Exception {
        // The modified vendor Java 8 may block forever in Files.createTempFile's
        // default SecureRandom. Generate a collision-resistant name ourselves.
        Path temp=null;
        try {
            for(int i=0;i<12;i++) {
                Path candidate=dest.resolveSibling(".font-patch-"
                    +Long.toUnsignedString(RANDOM.nextLong(),16)+".tmp");
                try {
                    temp=candidate;
                    try(OutputStream out=Files.newOutputStream(temp,
                        StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                        out.write(bytes);
                    }
                    break;
                }catch(FileAlreadyExistsException collision){temp=null;}
            }
            require(temp!=null,"无法创建安装临时文件");
            try {Files.move(temp,dest,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,dest,StandardCopyOption.REPLACE_EXISTING);}
        } finally {if(temp!=null)Files.deleteIfExists(temp);}
    }
    static int word(byte[] b,int o){return ((b[o]&255)<<24)|((b[o+1]&255)<<16)|((b[o+2]&255)<<8)|(b[o+3]&255);}
    static void put(byte[] b,int o,int v){for(int j=3;j>=0;j--){b[o+j]=(byte)v;v>>>=8;}}
    static int[] keys(byte[] key) throws Exception {
        require(key.length==16,"数据密钥长度不兼容");byte[] k=new byte[16];
        for(int i=0;i<16;i++)k[i]=(byte)(key[i]^CONFUSE[i]);
        return new int[]{word(k,0),word(k,4),word(k,8),word(k,12)};
    }
    static byte[] encrypt(byte[] plain,byte[] key) throws Exception {
        int[] k=keys(key);int pad=(8-(plain.length+10)%8)%8;
        byte[] raw=new byte[plain.length+10+pad];RANDOM.nextBytes(raw);raw[0]=(byte)(0x20|pad);
        System.arraycopy(plain,0,raw,pad+3,plain.length);Arrays.fill(raw,raw.length-7,raw.length,(byte)0);
        byte[] out=new byte[raw.length];int x0=0,x1=0,c0=0,c1=0;
        for(int i=0;i<raw.length;i+=8){int a=word(raw,i)^c0,b=word(raw,i+4)^c1,v0=a,v1=b,sum=0;
            for(int j=0;j<16;j++){sum+=0x9e3779b9;v0+=((v1<<4)+k[0])^(v1+sum)^((v1>>>5)+k[1]);v1+=((v0<<4)+k[2])^(v0+sum)^((v0>>>5)+k[3]);}
            c0=v0^x0;c1=v1^x1;put(out,i,c0);put(out,i+4,c1);x0=a;x1=b;}
        return out;
    }
    static byte[] decrypt(byte[] cipher,byte[] key) throws Exception {
        require(cipher.length>=16 && cipher.length%8==0,"文件长度不兼容");int[] k=keys(key);
        byte[] raw=new byte[cipher.length];int x0=0,x1=0,c0=0,c1=0;
        for(int i=0;i<cipher.length;i+=8){int a=word(cipher,i),b=word(cipher,i+4),v0=a^x0,v1=b^x1,sum=0x9e3779b9*16;
            for(int j=0;j<16;j++){v1-=((v0<<4)+k[2])^(v0+sum)^((v0>>>5)+k[3]);v0-=((v1<<4)+k[0])^(v1+sum)^((v1>>>5)+k[1]);sum-=0x9e3779b9;}
            put(raw,i,v0^c0);put(raw,i+4,v1^c1);x0=v0;x1=v1;c0=a;c1=b;}
        require((raw[0]&0xf8)==0x20,"文件校验失败");
        for(int i=raw.length-7;i<raw.length;i++)require(raw[i]==0,"文件校验失败");
        int start=(raw[0]&7)+3;require(start<=raw.length-7,"文件校验失败");
        return Arrays.copyOfRange(raw,start,raw.length-7);
    }
    public static void main(String[] args) {
        boolean gui=args.length==2 && args[0].equals("--gui");
        try {
            require(args.length==2,"用法：java -jar font-patcher.jar --gui/--check/--apply/--rollback 软件目录");
            FontPatcher tool=new FontPatcher(Paths.get(args[1]));
            if(gui){
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                String state;
                try { state=tool.check(); }
                catch (Exception e) {
                    state="安装前检查未通过：" + (e.getMessage()==null ? "未知错误" : e.getMessage())
                        + "\n如以前使用本工具安装过，仍可通过“回滚”恢复。";
                }
                Object[] options={"安装补丁和字库","回滚界面","只检查","取消"};
                int choice=JOptionPane.showOptionDialog(null,"目标目录："+tool.dir+"\n"+state+"\n\n安装或回滚前，请退出奎享雕刻。\n更新字体界面与公共文件路径输入，保留本机授权。\n自动导入并分类字库；首次补齐 15 套中文手写字体需联网下载约 51 MB。","字体补丁",JOptionPane.DEFAULT_OPTION,JOptionPane.INFORMATION_MESSAGE,null,options,options[2]);
                if(choice<0||choice==3)return;
                String result=choice==0?tool.applyWithProgress():choice==1?tool.rollback():state;
                JOptionPane.showMessageDialog(null,result,"字体补丁",JOptionPane.INFORMATION_MESSAGE);
            } else {
                String result;
                if(args[0].equals("--check"))result=tool.check();
                else if(args[0].equals("--apply"))result=tool.applyBundle();
                else if(args[0].equals("--rollback"))result=tool.rollback();
                else throw new IOException("未知操作");
                System.out.println(result);
            }
        }catch(Exception e){
            String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
            if(gui)JOptionPane.showMessageDialog(null,message,"字体补丁未完成",JOptionPane.ERROR_MESSAGE);
            else System.err.println(message);
            System.exit(1);
        }
    }
}

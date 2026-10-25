import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.swing.*;

/** Cumulative update: target-local repacking, presentation-only standalone update, transactional rollback. */
public final class UpdatePatcher {
    static final String STATE=".kvenjoy-update", OPTIONS="奎享雕刻.vmoptions";
    final Path dir;
    final Properties metadata=new Properties();
    interface Progress {void show(String message);}
    Progress progress=System.out::println;
    interface BeforeWrite {void run(int index) throws Exception;}
    BeforeWrite beforeWrite=index -> {};
    FontPatcher.Downloader downloader;
    UpdatePatcher(Path path)throws Exception{
        dir=path.toRealPath();
        try(InputStream in=FontPatcher.resource("update.properties")){metadata.load(in);}
    }
    static void require(boolean ok,String why)throws IOException{FontPatcher.require(ok,why);}
    static String sha(byte[] b)throws Exception{return b==null?"absent":FontPatcher.sha(b);}
    static byte[] bytes(Path p)throws Exception{
        require(!Files.isSymbolicLink(p),"不覆盖符号链接："+p.getFileName());
        return Files.exists(p)?Files.readAllBytes(p):null;
    }
    static final class Data {
        String mode; Path core; byte[] before,machine,key; Map<String,byte[]> entries,payload;
    }
    Data inspect()throws Exception{
        Data data=new Data();
        data.mode=Files.isRegularFile(dir.resolve("app/app.jar"))?"standalone":"vendor";
        data.core=dir.resolve(data.mode.equals("standalone")?"app/app.jar":"core");
        data.before=bytes(data.core);require(data.before!=null&&data.before.length<64*1024*1024,"未找到兼容的软件主文件");
        if(data.mode.equals("standalone")){
            data.entries=FontPatcher.unzip(data.before);
            require(data.entries.containsKey("com/kenjoy/standalone/StandaloneLauncher.class"),"未知独立版启动链");
        }else{
            byte[] license=bytes(dir.resolve("licene")),info=bytes(dir.resolve("info.txt"));
            require(license!=null&&info!=null&&license.length<65536&&info.length<262144,"请先确保目标原软件能正常运行，缺少原有本机数据");
            Matcher ids=Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{32}(?![0-9a-f])").matcher(new String(info,StandardCharsets.ISO_8859_1));
            while(ids.find()&&data.entries==null){
                try{
                    String id=ids.group();byte[] machine=id.substring(0,16).getBytes(StandardCharsets.US_ASCII);
                    String json=new String(FontPatcher.decrypt(license,machine),StandardCharsets.UTF_8);
                    require(id.equalsIgnoreCase(FontPatcher.jsonString(json,"machineId")),"本机数据不匹配");
                    byte[] key=Base64.getDecoder().decode(FontPatcher.jsonString(json,"key"));require(key.length==16,"本机数据格式不兼容");
                    Map<String,byte[]> found=FontPatcher.unzip(FontPatcher.decrypt(data.before,machine));
                    require(found.containsKey("com/kenjoy/kenjoycnc/KenjoyDraw.class"),"不是兼容的core");
                    data.entries=found;data.machine=machine;data.key=key;
                }catch(Exception incompatible){/* Only identities already present at this target are tried. */}
            }
            require(data.entries!=null,"无法读取目标已有软件；未修改授权文件或主文件");
        }
        String required="required."+data.mode+".";
        for(String key:metadata.stringPropertyNames())if(key.startsWith(required))require(data.entries.containsKey(key.substring(required.length())),"软件不完整："+key.substring(required.length()));
        for(Map.Entry<String,byte[]> e:data.entries.entrySet())if(e.getKey().endsWith(".class")){
            String accepted=metadata.getProperty("known."+data.mode+"."+e.getKey());
            // Extra third party entries are preserved, but never replace an unrecognized payload class.
            if(accepted!=null)require(Arrays.asList(accepted.split(",")).contains(sha(decode(data,e.getValue()))),"发现未知或修改过的组件，停止更新："+e.getKey());
        }
        data.payload=new LinkedHashMap<String,byte[]>();String prefix="payload."+data.mode+".";
        for(String key:metadata.stringPropertyNames())if(key.startsWith(prefix)){
            String name=key.substring(prefix.length());byte[] b=FontPatcher.read(FontPatcher.resource("updates/"+data.mode+"/"+name));
            require(sha(b).equals(metadata.getProperty(key)),"更新包校验失败："+name);data.payload.put(name,b);
            if(data.entries.containsKey(name)&&name.endsWith(".class"))require(metadata.containsKey("known."+data.mode+"."+name),"目标存在未知的同名组件："+name);
        }
        return data;
    }
    byte[] decode(Data data,byte[] b)throws Exception{return data.mode.equals("standalone")?b:FontPatcher.decrypt(FontPatcher.decrypt(b,data.machine),data.key);}
    byte[] core(Data data)throws Exception{
        Map<String,byte[]> updated=new LinkedHashMap<String,byte[]>(data.entries);boolean changed=false;
        for(Map.Entry<String,byte[]> e:data.payload.entrySet()){
            byte[] old=updated.get(e.getKey());boolean cls=e.getKey().endsWith(".class");
            if(old!=null&&Arrays.equals(cls?decode(data,old):old,e.getValue()))continue;
            updated.put(e.getKey(),cls&&data.mode.equals("vendor")?FontPatcher.encrypt(FontPatcher.encrypt(e.getValue(),data.key),data.machine):e.getValue());changed=true;
        }
        if(!changed)return data.before;
        byte[] packed=FontPatcher.zip(updated);byte[] result=data.mode.equals("vendor")?FontPatcher.encrypt(packed,data.machine):packed;
        Map<String,byte[]> verify=FontPatcher.unzip(data.mode.equals("vendor")?FontPatcher.decrypt(result,data.machine):result);
        require(verify.keySet().equals(updated.keySet()),"更新后条目校验失败");
        for(String name:updated.keySet())require(Arrays.equals(updated.get(name),verify.get(name)),"更新后内容校验失败："+name);
        return result;
    }
    static byte[] options(byte[] before,int dpi)throws Exception{
        StringBuilder out=new StringBuilder();String source=before==null?"":new String(before,StandardCharsets.UTF_8).replaceFirst("^\\uFEFF","");
        for(String line:source.split("\\r?\\n"))if(!line.isEmpty()&&!line.trim().matches("-D(sun.java2d.dpiaware|awt.useSystemAAFontSettings|swing.aatext|kvenjoy.processingDpi)=.*"))out.append(line).append("\r\n");
        out.append("-Dsun.java2d.dpiaware=true\r\n-Dawt.useSystemAAFontSettings=on\r\n-Dswing.aatext=true\r\n-Dkvenjoy.processingDpi=").append(dpi).append("\r\n");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
    static int processingDpi(byte[] before)throws Exception{
        if(before!=null){Matcher m=Pattern.compile("(?m)^\\s*-Dkvenjoy.processingDpi=(\\d+)\\s*$").matcher(new String(before,StandardCharsets.UTF_8));int result=0;while(m.find())result=Integer.parseInt(m.group(1));if(result>0)return result;}
        if(before!=null&&System.getProperty("java.specification.version","").equals("1.8")){
            Matcher aware=Pattern.compile("(?m)^\\s*-Dsun.java2d.dpiaware=(true|false)\\s*$").matcher(new String(before,StandardCharsets.UTF_8));boolean nativeDpi=false;while(aware.find())nativeDpi=Boolean.parseBoolean(aware.group(1));
            if(nativeDpi)return java.awt.Toolkit.getDefaultToolkit().getScreenResolution();
        }
        // The original unmanifested EXE uses virtualized 96 DPI; preserve that processing scale.
        return Integer.parseInt(System.getProperty("update.previousDpi","96"));
    }
    static final class Change {String relative;byte[] before,after;Change(String r,byte[] b,byte[] a){relative=r;before=b;after=a;}}
    Path target(String relative)throws Exception{Path p=dir.resolve(relative).normalize();require(p.startsWith(dir)&&!p.equals(dir),"无效更新路径");Path parent=p;while(parent!=null&&parent.startsWith(dir)){require(!Files.isSymbolicLink(parent),"更新路径包含符号链接");parent=parent.getParent();}return p;}
    void add(List<Change> changes,Path path,byte[] after)throws Exception{
        String relative=dir.relativize(path).toString();Path dest=target(relative);byte[] before=bytes(dest);
        if(!Arrays.equals(before,after))changes.add(new Change(relative,before,after));
    }
    List<Change> plan(Data data,boolean fonts)throws Exception{
        List<Change> changes=new ArrayList<Change>();add(changes,data.core,core(data));
        if(data.mode.equals("vendor")){byte[] old=bytes(dir.resolve(OPTIONS));add(changes,dir.resolve(OPTIONS),options(old,processingDpi(old)));}
        if(fonts){
            FontPatcher tool=downloader==null?new FontPatcher(dir):new FontPatcher(dir,new String(FontPatcher.read(FontPatcher.resource("fonts/chinese-downloads.tsv")),StandardCharsets.UTF_8),downloader);List<FontPatcher.FontChange> selected=tool.planFonts();int i=0;
            for(FontPatcher.FontChange c:selected){
                progress.show("准备字库 "+(++i)+" / "+selected.size()+"："+c.destination.getFileName());
                byte[] b=c.resource!=null?FontPatcher.read(FontPatcher.resource(c.resource)):c.relocateFrom!=null?bytes(c.relocateFrom):tool.downloader.get(c.source,c.size);
                require(b!=null&&sha(b).equals(c.hash),"字库下载/校验失败，尚未修改软件："+c.destination.getFileName());
                add(changes,c.destination,b);if(c.relocateFrom!=null&&!c.relocateFrom.equals(c.destination))add(changes,c.relocateFrom,null);
            }
        }
        return changes;
    }
    Path state()throws Exception{Path p=target(STATE);Files.createDirectories(p);return p;}
    void write(Path p,byte[] b)throws Exception{target(dir.relativize(p).toString());if(b==null)Files.deleteIfExists(p);else{Files.createDirectories(p.getParent());FontPatcher.replace(p,b);}}
    Properties receipt(Path p)throws Exception{Properties r=new Properties();try(InputStream in=Files.newInputStream(p)){r.load(in);}return r;}
    void save(Path p,Properties r)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();r.store(out,"Cumulative patch receipt; no credentials");write(p,out.toByteArray());}
    String check()throws Exception{Data data=inspect();return "已识别"+(data.mode.equals("vendor")?"原安装版":"0.019独立版")+"，可更新至累计补丁 "+metadata.getProperty("version")+"。";}
    String apply(boolean fonts)throws Exception{
        Path state=state();
        try(FileChannel lock=FileChannel.open(state.resolve("update.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock held=lock.tryLock()){
            require(held!=null,"另一更新或回滚正在进行");Path record=state.resolve("receipt.properties");byte[] oldReceipt=bytes(record);
            if(oldReceipt!=null)require(!"prepared".equals(receipt(record).getProperty("status")),"上次更新被中断，请先回滚恢复，再重新更新");
            Data data=inspect();List<Change> changes=plan(data,fonts);if(changes.isEmpty())return "已是最新补丁，无需重复安装。";
            Properties r=new Properties();r.setProperty("version",metadata.getProperty("version"));r.setProperty("status","prepared");r.setProperty("count",String.valueOf(changes.size()));
            Path transaction=state.resolve("txn-"+new UUID(FontPatcher.RANDOM.nextLong(),FontPatcher.RANDOM.nextLong()).toString());Files.createDirectory(transaction);r.setProperty("transaction",transaction.getFileName().toString());
            if(oldReceipt!=null){write(transaction.resolve("previous-receipt.properties"),oldReceipt);r.setProperty("previousReceiptSha",sha(oldReceipt));}
            for(int i=0;i<changes.size();i++){
                Change c=changes.get(i);r.setProperty(i+".path",c.relative);r.setProperty(i+".before",sha(c.before));r.setProperty(i+".after",sha(c.after));
                if(c.before!=null)write(transaction.resolve(i+".before"),c.before);
                require(sha(bytes(target(c.relative))).equals(sha(c.before)),"文件在更新期间发生变化："+c.relative);
            }
            save(record,r);int done=0;
            try{
                for(int i=0;i<changes.size();i++){Change c=changes.get(i);require(sha(bytes(target(c.relative))).equals(sha(c.before)),"文件在更新期间发生变化："+c.relative);beforeWrite.run(i);done=i+1;write(target(c.relative),c.after);require(sha(bytes(target(c.relative))).equals(sha(c.after)),"安装后校验失败："+c.relative);}
                r.setProperty("status","installed");save(record,r);
            }catch(Exception error){
                for(int i=done-1;i>=0;i--){Change c=changes.get(i);write(target(c.relative),c.before);}
                write(record,oldReceipt);throw error;
            }
            return "累计补丁 "+metadata.getProperty("version")+" 更新成功。\n请重新打开软件。备份位于："+transaction;
        }
    }
    String rollback()throws Exception{
        Path state=state();
        try(FileChannel lock=FileChannel.open(state.resolve("update.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock held=lock.tryLock()){
            require(held!=null,"另一更新或回滚正在进行");Path record=state.resolve("receipt.properties");require(Files.isRegularFile(record),"没有本补丁的回滚记录");byte[] originalReceipt=bytes(record);Properties r=receipt(record);
            String id=r.getProperty("transaction","");require(id.matches("txn-[a-f0-9-]{36}"),"回滚记录损坏");Path transaction=state.resolve(id);int count=Integer.parseInt(r.getProperty("count"));
            List<Change> changes=new ArrayList<Change>();List<byte[]> installed=new ArrayList<byte[]>();
            for(int i=0;i<count;i++){
                Path p=target(r.getProperty(i+".path"));String current=sha(bytes(p)),before=r.getProperty(i+".before"),after=r.getProperty(i+".after");
                require(current.equals(before)||current.equals(after),"安装后有新修改，停止回滚："+p.getFileName());
                byte[] backup=before.equals("absent")?null:bytes(transaction.resolve(i+".before"));require(sha(backup).equals(before),"备份校验失败");changes.add(new Change(r.getProperty(i+".path"),backup,null));installed.add(bytes(p));
            }
            byte[] prior=bytes(transaction.resolve("previous-receipt.properties"));
            require(sha(prior).equals(r.getProperty("previousReceiptSha",prior==null?"absent":"missing")),"上次收据备份校验失败");
            int attempted=changes.size();
            try{
                for(int i=changes.size()-1;i>=0;i--){beforeWrite.run(i);require(sha(bytes(target(changes.get(i).relative))).equals(sha(installed.get(i))),"文件在回滚期间发生变化："+changes.get(i).relative);attempted=i;write(target(changes.get(i).relative),changes.get(i).before);require(sha(bytes(target(changes.get(i).relative))).equals(sha(changes.get(i).before)),"回滚后校验失败："+changes.get(i).relative);}
                attempted=0;
                if(prior!=null)write(record,prior);else{r.setProperty("status","rolled-back");save(record,r);}
            }catch(Exception error){
                boolean restored=true;
                for(int i=attempted;i<changes.size();i++)try{write(target(changes.get(i).relative),installed.get(i));}catch(Exception compensation){error.addSuppressed(compensation);restored=false;}
                try{write(record,originalReceipt);}catch(Exception compensation){error.addSuppressed(compensation);restored=false;}
                if(!restored)try{r.setProperty("status","rollback-partial");save(record,r);}catch(Exception marker){error.addSuppressed(marker);}
                throw error;
            }
            return "已精确恢复本次更新前的软件、启动选项和本次改动的字库。";
        }
    }
    String guiApply()throws Exception{
        final JDialog dialog=new JDialog((java.awt.Frame)null,"正在更新 "+metadata.getProperty("version"),true);final JLabel label=new JLabel("正在检查并准备更新，请稍候……");label.setBorder(BorderFactory.createEmptyBorder(22,22,22,22));dialog.add(label);dialog.setSize(650,150);dialog.setLocationRelativeTo(null);dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        progress=s->SwingUtilities.invokeLater(()->label.setText(s));
        SwingWorker<String,Void> worker=new SwingWorker<String,Void>(){protected String doInBackground()throws Exception{return apply(true);}protected void done(){dialog.dispose();}};
        SwingUtilities.invokeAndWait(()->{worker.execute();dialog.setVisible(true);});
        try{return worker.get();}catch(java.util.concurrent.ExecutionException e){throw (Exception)e.getCause();}
    }
    public static void main(String[] args){
        boolean gui=args.length==2&&args[0].equals("--gui");
        try{
            require(args.length==2,"用法：java -jar update-patcher.jar --gui|--check|--apply|--rollback 软件目录");
            UpdatePatcher tool=new UpdatePatcher(Paths.get(args[1]));String result;
            if(gui){
                if(System.getProperty("java.specification.version","").equals("1.8")){
                    float scale=Math.max(1,java.awt.Toolkit.getDefaultToolkit().getScreenResolution()/96f);java.util.List<Object> keys=new ArrayList<Object>(UIManager.getDefaults().keySet());
                    for(Object key:keys){Object value=UIManager.get(key);if(value instanceof java.awt.Font)UIManager.put(key,new javax.swing.plaf.FontUIResource(((java.awt.Font)value).deriveFont(((java.awt.Font)value).getSize2D()*scale)));}
                }
                String state;try{state=tool.check();}catch(Exception e){state=e.getMessage()+"\n可使用本工具已有的回滚记录恢复。";}
                Object[] options={"一键更新","回滚本次更新","只检查","取消"};int choice=JOptionPane.showOptionDialog(null,"目录："+tool.dir+"\n"+state+"\n\n请先保存并退出软件。缺少的中文手写字库需要联网补齐。","奎享雕刻累计更新",JOptionPane.DEFAULT_OPTION,JOptionPane.INFORMATION_MESSAGE,null,options,options[0]);
                if(choice<0||choice==3)return;result=choice==0?tool.guiApply():choice==1?tool.rollback():tool.check();JOptionPane.showMessageDialog(null,result,"累计更新",JOptionPane.INFORMATION_MESSAGE);
            }else{if(args[0].equals("--check"))result=tool.check();else if(args[0].equals("--apply"))result=tool.apply(true);else if(args[0].equals("--rollback"))result=tool.rollback();else throw new IOException("未知操作");System.out.println(result);}
        }catch(Exception e){String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();if(gui)JOptionPane.showMessageDialog(null,message,"更新未完成",JOptionPane.ERROR_MESSAGE);else System.err.println(message);System.exit(1);}
    }
}

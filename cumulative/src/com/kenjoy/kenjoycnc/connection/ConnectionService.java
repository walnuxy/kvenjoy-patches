package com.kenjoy.kenjoycnc.connection;

import com.kenjoy.kenjoycnc.machine.g;
import com.kenjoy.kenjoycnc.model.Settings;
import com.google.gson.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;

/** One owner for discovery/connection. No job, motion or parameter-write entry points. */
public final class ConnectionService {
    private static ConnectionService instance;
    private static volatile boolean probeMode;
    private static volatile String probeFirmware="",probeError;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"machine-discovery");t.setDaemon(true);return t;});
    private final Callable<List<DevicePort>> discovery;
    private final ComboBoxModel baudModel;
    private final JFrame frame;private final JTextArea status=new JTextArea("未连接",2,1);
    private final JCheckBox automatic=new JCheckBox("自动连接");private final JButton search=new JButton("搜索并连接"),cancel=new JButton("取消搜索");
    private final JButton connect;private final Properties remembered=new Properties();
    private final Path memory=Paths.get(System.getProperty("user.home"),".KenjoyDraw","connection.properties");
    private volatile boolean busy,canceled,suppressed,closed;private volatile DevicePort connected;
    private String fingerprint="";private volatile long deadline;
    public static boolean probing(){return probeMode;}
    public static void transportFailed(String message){probeError=message;}
    public static String firmwareLine(String line){
        if(probeFirmware.equalsIgnoreCase("Grbl")){
            if(line.matches("Grbl [0-9]+\\.[0-9]+.*"))return line;
            if(line.matches("\\[VER:[0-9]+\\.[0-9]+[^\\]]*\\]"))return "Grbl "+line.substring(5,line.length()-1);
        }else if(probeFirmware.equals("EBB")){if(line.startsWith("EBB")&&line.contains("Version")||line.startsWith("Steamduino DrawCore"))return line;}
        else if(probeFirmware.equals("K_AT")){if(line.contains("K_AT")&&(line.startsWith("FIRMWARE_NAME:")||line.startsWith("echo:")||line.startsWith("K_AT")))return line;}
        else if(probeFirmware.equals("Marlin")){if(line.startsWith("FIRMWARE_NAME:Marlin")||line.startsWith("echo:Marlin")||line.startsWith("Marlin"))return line;}
        return null;
    }
    public static synchronized void install(Object app)throws Exception{if(instance==null)instance=new ConnectionService(app);}
    public static void startup(){ConnectionService s=instance;if(s!=null&&Settings.getInst().autoConnect)s.request(false);}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private ConnectionService(Object app)throws Exception{this(app,null);}
    ConnectionService(Object app,Callable<List<DevicePort>> discovery)throws Exception{
        this.discovery=discovery;
        frame=(JFrame)app.getClass().getMethod("f").invoke(app);Object device=field(app,"y");connect=(JButton)field(device,"c");baudModel=((JComboBox)field(device,"e")).getModel();
        try(InputStream in=Files.newInputStream(memory)){remembered.load(in);}catch(IOException ignored){}
        automatic.setSelected(Settings.getInst().autoConnect);automatic.setName("MachineAutoConnect");search.setName("MachineSearchConnect");cancel.setName("MachineCancelSearch");status.setName("MachineConnectionStatus");
        JPanel row=new JPanel(new BorderLayout(6,4));JPanel actions=new JPanel(new GridLayout(1,3,6,0));actions.add(automatic);actions.add(search);actions.add(cancel);row.add(actions,BorderLayout.NORTH);status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);status.setOpaque(false);row.add(status,BorderLayout.CENTER);
        // Keep existing serial controls and device layout: add a separate row above them.
        Container parent=connect.getParent();while(parent!=null&&!(parent.getLayout() instanceof BorderLayout))parent=parent.getParent();
        if(parent==null)throw new IllegalStateException("无法定位串口控制区域");BorderLayout layout=(BorderLayout)parent.getLayout();Component north=layout.getLayoutComponent(BorderLayout.NORTH);JPanel header=new JPanel(new BorderLayout());if(north!=null){parent.remove(north);header.add(north,BorderLayout.CENTER);}header.add(row,BorderLayout.NORTH);parent.add(header,BorderLayout.NORTH);
        for(ActionListener l:connect.getActionListeners())connect.removeActionListener(l);
        connect.addActionListener(e->{if(g.a().g().b())disconnect(true);else request(true);});
        automatic.addActionListener(e->{Settings.getInst().autoConnect=automatic.isSelected();Settings.save();if(automatic.isSelected()){suppressed=false;fingerprint="";request(false);}else{canceled=true;show("自动连接已关闭");}});
        search.addActionListener(e->request(true));cancel.addActionListener(e->{canceled=true;suppressed=true;show("正在取消…");});cancel.setEnabled(false);
        frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){closed=true;canceled=true;worker.shutdownNow();}});
        worker.scheduleWithFixedDelay(()->poll(),2,2,TimeUnit.SECONDS);
    }
    private void show(String message){SwingUtilities.invokeLater(()->{status.setText(message);status.setToolTipText(message);search.setEnabled(!busy&&!g.a().g().b());cancel.setEnabled(busy);connect.setEnabled(!busy);});}
    private synchronized void request(boolean manual){if(closed||busy)return;if(g.a().g().b()){show("设备已连接");return;}if(!manual&&(!Settings.getInst().autoConnect||suppressed))return;busy=true;canceled=false;deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);show("正在搜索 USB 串口…");worker.execute(()->discover(manual));}
    private void poll(){if(closed||busy)return;try{
        if(connected!=null){List<DevicePort> ports=ports();boolean present=false;for(DevicePort p:ports)if(p.id.equals(connected.id)&&p.port.equals(connected.port))present=true;if(!present||!g.a().g().b()){disconnect(false);}return;}
        if(!Settings.getInst().autoConnect||suppressed)return;
        List<DevicePort> ports=ports();String key=signature(ports);if(!key.equals(fingerprint)){fingerprint=key;request(false);}
    }catch(Exception e){show("搜索失败："+e.getMessage());}}
    private static String signature(List<DevicePort> ports){List<String> names=new ArrayList<>();for(DevicePort p:ports)names.add(p.id+"@"+p.port);Collections.sort(names);return names.toString();}
    private void check()throws Exception{if(closed||canceled||Thread.currentThread().isInterrupted())throw new CancellationException();if(System.nanoTime()>deadline)throw new TimeoutException("搜索连接超时");}
    private void discover(boolean manual){boolean opened=false;try{
        check();List<DevicePort> all=ports();check();fingerprint=signature(all);List<DevicePort> candidates=DevicePort.candidates(all);
        if(candidates.isEmpty())throw new IOException("未发现可用 USB 串口（已排除蓝牙与蜂窝模块）");
        candidates.sort(Comparator.comparing((DevicePort p)->!p.identity().equals(remembered.getProperty("device",""))).thenComparing(p->p.port));
        DevicePort choice;
        if(candidates.size()==1&&(manual||DevicePort.automatic(candidates,remembered.getProperty("device",""))!=null))choice=candidates.get(0);
        else choice=choose(candidates);
        check();probeFirmware=g.a().f();if(!Arrays.asList("Grbl","EBB","Marlin","K_AT").contains(probeFirmware))throw new IOException("当前固件不支持安全识别："+probeFirmware);
        int baud=choice.identity().equals(remembered.getProperty("device"))?Integer.parseInt(remembered.getProperty("baud","115200")):Integer.parseInt(String.valueOf(baudModel.getSelectedItem()));
        probeMode=true;probeError=null;show("正在验证固件："+choice.port);
        try{if(g.a().g().b())throw new IOException("设备已经打开，请先断开");opened=true;com.kenjoy.kenjoycnc.machine.a.c transport=(com.kenjoy.kenjoycnc.machine.a.c)g.a().g();transport.openForProbe(choice.port,baud);check();transport.probe(probeFirmware);
            long until=Math.min(deadline,System.nanoTime()+TimeUnit.SECONDS.toNanos(7));
            while(!transport.confirmed()){check();if(probeError!=null)throw new IOException(probeError);if(System.nanoTime()>until)throw new IOException("串口已打开，但未收到 "+probeFirmware+" 固件确认；请检查机型与波特率");Thread.sleep(50);}
            check();transport.accepted();connected=choice;opened=false;suppressed=false;
            remembered.setProperty("device",choice.identity());remembered.setProperty("baud",String.valueOf(baud));
            try{Files.createDirectories(memory.getParent());Path tmp=memory.resolveSibling("connection-"+Long.toHexString(System.nanoTime())+".tmp");try(OutputStream out=Files.newOutputStream(tmp,StandardOpenOption.CREATE_NEW)){remembered.store(out,"last verified USB device");}Files.move(tmp,memory,StandardCopyOption.REPLACE_EXISTING);}catch(IOException e){show("连接成功；设备记忆保存失败："+e.getMessage());return;}
            show("已验证连接："+choice.port+" · "+probeFirmware);
        }finally{if(opened)g.a().g().c();probeMode=false;}
    }catch(CancellationException e){suppressed=true;show("搜索已取消");}catch(Exception e){show("连接失败："+e.getMessage());}finally{busy=false;SwingUtilities.invokeLater(()->{search.setEnabled(!g.a().g().b());cancel.setEnabled(false);connect.setEnabled(true);});}}
    private DevicePort choose(List<DevicePort> candidates)throws Exception{
        final java.util.concurrent.atomic.AtomicReference<DevicePort> choice=new java.util.concurrent.atomic.AtomicReference<>();
        final CountDownLatch done=new CountDownLatch(1);final JDialog[] dialog=new JDialog[1];
        SwingUtilities.invokeAndWait(()->{JDialog d=new JDialog(frame,"选择机器串口",false);dialog[0]=d;JComboBox<DevicePort> list=new JComboBox<>(candidates.toArray(new DevicePort[0]));JPanel content=new JPanel(new BorderLayout(8,8));content.add(new JLabel("请选择机器；已优先排列上次成功的设备"),BorderLayout.NORTH);content.add(list,BorderLayout.CENTER);JPanel actions=new JPanel();JButton ok=new JButton("连接"),stop=new JButton("取消");actions.add(ok);actions.add(stop);content.add(actions,BorderLayout.SOUTH);ok.addActionListener(e->{choice.set((DevicePort)list.getSelectedItem());done.countDown();});stop.addActionListener(e->done.countDown());d.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){done.countDown();}});d.setContentPane(content);d.pack();d.setLocationRelativeTo(frame);d.setVisible(true);});
        show("请选择 USB 设备（20 秒内可取消）");
        try{while(!done.await(50,TimeUnit.MILLISECONDS))check();check();if(choice.get()==null)throw new CancellationException();deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);return choice.get();}finally{SwingUtilities.invokeLater(()->dialog[0].dispose());}
    }
    private void disconnect(boolean manual){suppressed=true;canceled=true;connected=null;worker.execute(()->{try{g.a().g().c();}finally{show(manual?"已手动断开；点击搜索可重新连接":"设备已断开；任务不会自动恢复，点击搜索重新连接");}});}
    private List<DevicePort> ports()throws Exception{return discovery==null?enumerate():discovery.call();}
    private List<DevicePort> enumerate()throws Exception{
        if(Boolean.getBoolean("kvenjoy.connection.disabled"))return Collections.emptyList();
        String script="$ErrorActionPreference='Stop';[Console]::OutputEncoding=[Text.UTF8Encoding]::new();@(Get-CimInstance Win32_PnPEntity -Filter \"Name LIKE '%(COM%'\" | Select-Object Name,PNPDeviceID)|ConvertTo-Json -Compress";
        String encoded=Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Process p=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-EncodedCommand",encoded).redirectErrorStream(true).start();
        ByteArrayOutputStream out=new ByteArrayOutputStream();Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] b=new byte[4096];for(int n;(n=in.read(b))!=-1;)if(out.size()<1048576)out.write(b,0,n);}catch(IOException ignored){}},"serial-discovery-output");reader.setDaemon(true);reader.start();
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);try{while(!p.waitFor(100,TimeUnit.MILLISECONDS)){if(closed||busy&&canceled)throw new CancellationException();if(System.nanoTime()>limit)throw new TimeoutException("Windows 设备查询超时");}reader.join(1000);if(p.exitValue()!=0)throw new IOException("Windows 设备查询失败");String json=new String(out.toByteArray(),StandardCharsets.UTF_8).trim();List<DevicePort> ports=new ArrayList<>();if(json.isEmpty())return ports;JsonElement root=new JsonParser().parse(json);JsonArray array;if(root.isJsonArray())array=root.getAsJsonArray();else{array=new JsonArray();array.add(root);}for(JsonElement element:array){JsonObject o=element.getAsJsonObject();String name=o.get("Name").getAsString(),id=o.get("PNPDeviceID").getAsString();java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\((COM[0-9]+)\\)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(name);if(m.find())ports.add(new DevicePort(m.group(1).toUpperCase(Locale.ROOT),name,id));}return ports;}finally{if(p.isAlive())p.destroyForcibly();}
    }
}

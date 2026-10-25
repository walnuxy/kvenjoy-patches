package com.kenjoy.kenjoycnc.connection;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.lang.reflect.*;
import javax.swing.*;
import java.awt.*;
import com.kenjoy.kenjoycnc.machine.g;
import com.kenjoy.kenjoycnc.model.Settings;

/** Exercise the production service with fake discovery and transport; no native port is opened. */
public final class ConnectionChecks {
    static int count;
    static void check(String label,boolean pass){if(!pass)throw new AssertionError(label);count++;System.out.println("CONNECTION_CHECK "+label);}
    static void field(Object o,String n,Object v)throws Exception{Class<?> c=o.getClass();while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
    static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void call(Object o,String n,boolean b)throws Exception{Method m=o.getClass().getDeclaredMethod(n,boolean.class);m.setAccessible(true);m.invoke(o,b);}
    static void idle(ConnectionService s)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while((Boolean)field(s,"busy")){if(System.nanoTime()>end)throw new AssertionError("worker timeout");Thread.sleep(20);}SwingUtilities.invokeAndWait(()->{});}
    public static final class App {final JFrame frame=new JFrame();final Device y=new Device();public App(){frame.add(y.panel);}public JFrame f(){return frame;}}
    public static final class Device {final JButton c=new JButton("连接");final JComboBox<String> e=new JComboBox<>(new String[]{"115200"});final JPanel panel=new JPanel(new BorderLayout());Device(){panel.add(c,BorderLayout.NORTH);}}
    static final class Fake extends com.kenjoy.kenjoycnc.machine.a.c {
        int opens,closes,legacyWrites;boolean open,fail,reply=true;long delay;String last;final com.kenjoy.kenjoycnc.machine.Re_c firmware;
        Fake(com.kenjoy.kenjoycnc.machine.Re_c f){super(f);firmware=f;}
        public boolean a(String port,int baud){opens++;last=port;if(fail)throw new IllegalStateException("Port busy");open=true;return true;}
        public void openForProbe(String port,int baud)throws Exception{super.openForProbe(port,baud);}
        public boolean b(){return open;}public void a(){open=false;closes++;}
        protected void a(byte[] b){legacyWrites++;}protected void a(byte b){legacyWrites++;}
        public void probe(String name)throws Exception{if(delay>0)Thread.sleep(delay);if(reply)field(firmware,"d",true);}
        public boolean confirmed(){return firmware.p();}
        public void accepted(){}
    }
    public static void main(String[] args)throws Exception{
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{e.printStackTrace();for(java.util.Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet()){System.err.println(entry.getKey().getName());for(StackTraceElement line:entry.getValue())System.err.println("  "+line);}System.exit(1);});
        com.kenjoy.kenjoycnc.o.c.c="kvenjoy";com.kenjoy.kenjoycnc.data.MachineData machine=new com.kenjoy.kenjoycnc.data.MachineData();machine.name="ConnectionChecks";machine.type=com.kenjoy.kenjoycnc.machine.MachineType.Grbl;com.kenjoy.kenjoycnc.o.c.e=machine;
        System.setProperty("kvenjoy.connection.disabled","true");Settings.getInst().autoConnect=false;Settings.getInst().updateStatus=false;
        DevicePort ch=new DevicePort("COM9","USB CH340","USB\\VID_1A86&PID_7523\\SERIAL1"),moved=new DevicePort("COM15","USB CH340","USB\\VID_1A86&PID_7523\\SERIAL1"),other=new DevicePort("COM7","USB CH340","USB\\VID_1A86&PID_7523\\SERIAL2");
        DevicePort bt=new DevicePort("COM3","Bluetooth","BTHENUM\\123"),cell=new DevicePort("COM8","Quectel","USB\\VID_2C7C&PID_0125\\X"),unknown=new DevicePort("COM6","USB Device","USB\\VID_9999\\X");
        check("excludes Bluetooth and Quectel",DevicePort.candidates(Arrays.asList(bt,cell,ch)).equals(Arrays.asList(ch)));
        check("single CH340 automatically selected",DevicePort.automatic(Arrays.asList(ch),"")==ch);
        check("identity survives COM number change",ch.identity().equals(moved.identity()));
        check("multiple candidates require choice even with remembered device",DevicePort.automatic(Arrays.asList(ch,other),ch.identity())==null);
        check("unknown USB requires choice",DevicePort.automatic(Arrays.asList(unknown),"")==null);
        Field firmwareName=ConnectionService.class.getDeclaredField("probeFirmware");firmwareName.setAccessible(true);firmwareName.set(null,"Grbl");
        check("generic ok is not firmware confirmation",ConnectionService.firmwareLine("ok")==null);
        check("wrong firmware is not confirmation",ConnectionService.firmwareLine("FIRMWARE_NAME:Marlin 2.0")==null);
        check("Grbl build info confirms identity",ConnectionService.firmwareLine("[VER:1.1h.20190825:]").startsWith("Grbl 1.1"));
        firmwareName.set(null,"EBB");check("EBB version confirms identity",ConnectionService.firmwareLine("EBB Firmware Version 2.6.0")!=null);
        com.kenjoy.kenjoycnc.machine.grbl.b firmware=new com.kenjoy.kenjoycnc.machine.grbl.b();Fake transport=new Fake(firmware);field(g.a(),"c",firmware);field(g.a(),"d",transport);field(g.a(),"e","Grbl");
        java.util.concurrent.atomic.AtomicReference<List<DevicePort>> ports=new java.util.concurrent.atomic.AtomicReference<>(Arrays.asList(ch));
        App[] app=new App[1];ConnectionService[] service=new ConnectionService[1];SwingUtilities.invokeAndWait(()->{try{app[0]=new App();service[0]=new ConnectionService(app[0],()->ports.get());}catch(Exception e){throw new RuntimeException(e);}});ConnectionService s=service[0];
        Field singleton=ConnectionService.class.getDeclaredField("instance");singleton.setAccessible(true);singleton.set(null,s);Settings.getInst().autoConnect=true;ConnectionService.startup();idle(s);check("startup verifies already inserted device",transport.opens==1&&field(s,"connected")==ch);
        check("safe open bypasses legacy reset and settings writes",transport.legacyWrites==0);
        call(s,"request",true);idle(s);check("connected port is not reopened",transport.opens==1);
        call(s,"disconnect",true);Thread.sleep(150);check("manual disconnect suppresses reconnect",(Boolean)field(s,"suppressed")&&!transport.open);
        ports.set(Arrays.asList(moved));call(s,"request",true);idle(s);check("manual retry follows new COM number",transport.last.equals("COM15"));
        call(s,"disconnect",true);Thread.sleep(150);transport.fail=true;call(s,"request",true);idle(s);check("busy port leaves disconnected",!transport.open&&field(s,"connected")==null);transport.fail=false;
        transport.reply=false;call(s,"request",true);Thread.sleep(150);field(s,"canceled",true);idle(s);check("cancel closes pending port",!transport.open&&field(s,"connected")==null);
        call(s,"request",true);Thread.sleep(150);field(s,"deadline",System.nanoTime()-1);idle(s);check("timeout closes unconfirmed port",!transport.open&&field(s,"connected")==null);
        ports.set(Arrays.asList(ch,other));int opensBeforeChoice=transport.opens;call(s,"request",true);Thread.sleep(200);SwingUtilities.invokeAndWait(()->{for(Window w:Window.getWindows())if(w instanceof JDialog&&((JDialog)w).getTitle().equals("选择机器串口"))w.dispatchEvent(new java.awt.event.WindowEvent(w,java.awt.event.WindowEvent.WINDOW_CLOSING));});idle(s);check("multiple-device choice cancellation opens nothing",transport.opens==opensBeforeChoice);
        ports.set(Arrays.asList(moved));transport.reply=true;call(s,"request",true);idle(s);ports.set(Collections.emptyList());Method poll=ConnectionService.class.getDeclaredMethod("poll");poll.setAccessible(true);poll.invoke(s);Thread.sleep(150);check("unplug clears connection and suppresses job resume",field(s,"connected")==null&&(Boolean)field(s,"suppressed"));
        Settings.getInst().autoConnect=true;field(s,"suppressed",false);ports.set(Arrays.asList(ch));poll.invoke(s);idle(s);check("hotplug discovers and connects",field(s,"connected")==ch);
        field(s,"closed",true);((ScheduledExecutorService)field(s,"worker")).shutdownNow();SwingUtilities.invokeAndWait(()->app[0].frame.dispose());transport.c();
        System.out.println("COMPLETE connectionChecks="+count);System.exit(0);
    }
}

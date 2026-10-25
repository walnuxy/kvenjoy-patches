package com.kenjoy.kenjoycnc.machine.a;
import jssc.*;
import java.nio.charset.StandardCharsets;
import com.kenjoy.kenjoycnc.connection.ConnectionService;

/** Preserve transport ABI. Discovery blocks legacy reset/settings writes until handshake completes. */
public class c extends a implements SerialPortEventListener {
    private volatile SerialPort port;private volatile boolean probing,safeSession,confirmed;private volatile long session;
    private final StringBuilder received=new StringBuilder();private final com.kenjoy.kenjoycnc.machine.Re_c firmware;
    private final java.util.concurrent.ExecutorService parser=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"firmware-reader");t.setDaemon(true);return t;});
    public c(com.kenjoy.kenjoycnc.machine.Re_c firmware){super(firmware);this.firmware=firmware;}
    /** Bypass legacy firmware.a(port,baud): EBB starts an asynchronous settings sender there. */
    public void openForProbe(String name,int baud)throws Exception{
        if(b())throw new IllegalStateException("串口已打开");
        for(String field:new String[]{"f","g","d"}){java.lang.reflect.Field f=com.kenjoy.kenjoycnc.machine.Re_c.class.getDeclaredField(field);f.setAccessible(true);f.set(firmware,field.equals("f")?name:field.equals("g")?Integer.valueOf(baud):Boolean.FALSE);}
        safeSession=true;confirmed=false;session++;synchronized(this){received.setLength(0);}this.a=a(name,baud);
    }
    @Override public boolean a(String name,int baud){
        probing=ConnectionService.probing();SerialPort p=new SerialPort(name);port=p;
        try{p.openPort();p.setParams(baud,8,1,0,!probing,!probing);p.addEventListener(this);return p.isOpened();}
        catch(Exception e){a();throw new IllegalStateException("无法打开 "+name+"："+e.getMessage(),e);}
    }
    @Override public void a(){session++;SerialPort p=port;port=null;probing=false;safeSession=false;if(p!=null)try{p.removeEventListener();if(p.isOpened())p.closePort();}catch(Exception ignored){} }
    @Override public boolean b(){SerialPort p=port;return p!=null&&p.isOpened();}
    @Override protected void a(byte[] bytes){if(probing)return;try{port.writeBytes(bytes);}catch(Exception e){throw new IllegalStateException(e);}}
    @Override protected void a(byte value){if(probing)return;try{port.writeByte(value);}catch(Exception e){throw new IllegalStateException(e);}}
    public void probe(String firmware)throws Exception{String command=firmware.equalsIgnoreCase("Grbl")?"$I\n":firmware.equals("EBB")?"V\r":"M115\n";port.writeBytes(command.getBytes(StandardCharsets.US_ASCII));}
    public boolean confirmed(){return confirmed;}
    public synchronized void accepted(){probing=false;received.setLength(0);}
    @Override public synchronized void serialEvent(SerialPortEvent event){
        SerialPort p=port;if(p==null)return;
        try{byte[] data=p.readBytes();if(data==null)return;
            if(!safeSession){b(data);return;}
            received.append(new String(data,StandardCharsets.US_ASCII));if(received.length()>16384){received.setLength(0);return;}
            int end;while((end=received.indexOf("\n"))>=0){String line=received.substring(0,end).trim();received.delete(0,end+1);String normalized=probing?ConnectionService.firmwareLine(line):line;if(normalized!=null){final long ticket=session;parser.execute(()->{if(ticket==session&&port==p){firmware.b(normalized);if(probing&&firmware.p())confirmed=true;}});}}
        }catch(Exception e){ConnectionService.transportFailed(e.getMessage());}
    }
}

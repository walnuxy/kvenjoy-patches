package com.kenjoy.kenjoycnc.connection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Real child-process streams; synthetic enumeration data. Never opens or probes a serial port. */
public final class DiscoveryChecks {
    static int checks;
    interface Action { void run() throws Exception; }
    static void yes(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void fails(Class<? extends Exception> type,Action action,String label)throws Exception{
        try{action.run();}catch(Exception expected){yes(type.isInstance(expected),label+" type="+expected.getClass().getSimpleName());return;}throw new AssertionError("Expected failure: "+label);
    }
    static List<String> command(String body){
        String script="[Console]::OutputEncoding=[Text.UTF8Encoding]::new();"+body;
        return Arrays.asList("powershell.exe","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-EncodedCommand",Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE)));
    }
    static final String PORT="[{\"Name\":\"USB-SERIAL CH340 (COM9)\",\"PNPDeviceID\":\"USB\\\\VID_1A86&PID_7523\\\\SYNTHETIC\"}]";
    static String output(String json){return "[Console]::Out.Write('"+json.replace("'","''")+"');";}
    public static void main(String[] args)throws Exception{
        String progress="[Console]::Error.WriteLine('#< CLIXML');[Console]::Error.WriteLine('<Objs><Obj S=\"progress\"><MS><S N=\"Activity\">Preparing modules for first use</S></MS></Obj></Objs>');";
        List<DevicePort> found=ConnectionService.enumerateCommand(command(progress+output(PORT)),()->false,6000);
        yes(found.size()==1&&found.get(0).port.equals("COM9"),"valid stdout survives PowerShell progress CLIXML on stderr");
        found=ConnectionService.enumerateCommand(command(output(PORT)+progress),()->false,6000);
        yes(found.size()==1,"stderr after JSON cannot contaminate stdout");
        found=ConnectionService.enumerateCommand(command("[Console]::Error.Write(('x'*200000));"+output(PORT)),()->false,6000);
        yes(found.size()==1,"large stderr is drained without deadlock");
        found=ConnectionService.enumerateCommand(command(output("[]")),()->false,6000);
        yes(found.isEmpty(),"empty array returns no ports");
        found=ConnectionService.enumerateCommand(command(""),()->false,6000);
        yes(found.isEmpty(),"empty stdout returns no ports");
        found=ConnectionService.enumerateCommand(command(output(PORT.substring(1,PORT.length()-1))),()->false,6000);
        yes(found.size()==1,"single-object JSON remains supported");
        fails(IOException.class,()->ConnectionService.enumerateCommand(command(output(PORT)+"[Console]::Error.WriteLine('query failed');exit 7;"),()->false,6000),"nonzero exit does not accept plausible stdout");
        fails(Exception.class,()->ConnectionService.enumerateCommand(command(output("not JSON")),()->false,6000),"malformed stdout remains an error");
        fails(TimeoutException.class,()->ConnectionService.enumerateCommand(command("Start-Sleep -Seconds 20;"),()->false,100),"query timeout retained");
        fails(CancellationException.class,()->ConnectionService.enumerateCommand(command("Start-Sleep -Seconds 20;"),()->true,6000),"query cancellation retained");
        System.out.println("COMPLETE discovery checks="+checks);
    }
}

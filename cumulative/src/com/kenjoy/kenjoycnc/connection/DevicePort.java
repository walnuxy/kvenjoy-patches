package com.kenjoy.kenjoycnc.connection;
import java.util.*;
/** USB identity is independent of the current COM number. */
public final class DevicePort {
    public final String port,name,id;
    public DevicePort(String port,String name,String id){this.port=port;this.name=name;this.id=id;}
    public boolean eligible(){String s=(name+" "+id).toUpperCase(Locale.ROOT);return id.toUpperCase(Locale.ROOT).startsWith("USB\\")&&!s.contains("BLUETOOTH")&&!s.contains("BTH")&&!s.contains("QUECTEL")&&!s.contains("VID_2C7C");}
    public boolean recognized(){String s=(name+" "+id).toUpperCase(Locale.ROOT);return s.contains("CH340")||s.contains("CH341")||s.contains("VID_1A86")||s.contains("VID_10C4")||s.contains("VID_0403")||s.contains("VID_2341")||s.contains("VID_04D8");}
    public String identity(){return id.toUpperCase(Locale.ROOT);}
    public String toString(){return name+" · "+port;}
    public static List<DevicePort> candidates(List<DevicePort> all){List<DevicePort> out=new ArrayList<>();for(DevicePort p:all)if(p.eligible())out.add(p);out.sort(Comparator.comparing(p->p.port));return out;}
    public static DevicePort automatic(List<DevicePort> all,String remembered){List<DevicePort> c=candidates(all);return c.size()==1&&(c.get(0).recognized()||c.get(0).identity().equals(remembered))?c.get(0):null;}
}

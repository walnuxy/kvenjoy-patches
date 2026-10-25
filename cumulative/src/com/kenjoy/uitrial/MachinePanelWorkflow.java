package com.kenjoy.uitrial;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;

/** Presents the existing machine actions with their real source and safer grouping. */
public final class MachinePanelWorkflow {
    private static final String KEY="modernization.machinePanel";
    private static final Object AUTO_ZERO_LOCK=new Object();
    private static final Map<Object,Object> AUTO_ZERO_WORKERS=new ConcurrentHashMap<Object,Object>();
    private static final Map<Object,Object> SENT_DEVICES=new ConcurrentHashMap<Object,Object>();
    private static final Map<Object,Object> ACTIVE_GENERATORS=new ConcurrentHashMap<Object,Object>();
    private static final Map<Object,TaskToken> CURRENT_TASKS=new ConcurrentHashMap<Object,TaskToken>();
    private static final Map<Object,TaskToken> WORKER_TASKS=new WeakHashMap<Object,TaskToken>();
    private static final ThreadLocal<Object> SENDING_WORKER=new ThreadLocal<Object>();
    private static final Set<Object> ATTEMPTED_DEVICES=Collections.newSetFromMap(new ConcurrentHashMap<Object,Boolean>());
    private static final Set<Object> CANCELED_WORKERS=Collections.newSetFromMap(new WeakHashMap<Object,Boolean>());
    private static final Set<Object> SENT_WORKERS=Collections.newSetFromMap(new WeakHashMap<Object,Boolean>());
    private static final class TaskToken {volatile boolean canceled;boolean sendAttempted;}
    private final Object app,device;
    private final JFrame frame;
    private final JPanel controls;
    private final JButton write,pause,stop,load,clear,preview,home,unlock,reset,sleep,origin,workOrigin,penUp,penDown;
    private final JLabel source;
    private final JComboBox modes;
    private final javax.swing.Timer timer;
    private Object frozenNote;
    private String frozenSource;
    private String activeSource;
    private boolean autoZeroWaiting;
    private boolean runningSeen;
    private boolean writeFailureLatch;
    private String handoffFailureMessage;
    private boolean modesEnabledBefore;
    private boolean modeLocked;
    private Object firmwareListener;
    private Object senderCompletionListener;

    private MachinePanelWorkflow(Object app)throws Exception{
        this.app=app;
        frame=(JFrame)app.getClass().getMethod("f").invoke(app);
        device=field(app,"y");
        modes=(JComboBox)field(app,"e");
        controls=(JPanel)field(device,"l");
        write=(JButton)field(device,"m");pause=(JButton)field(device,"n");stop=(JButton)field(device,"o");
        load=(JButton)field(device,"w");clear=(JButton)field(device,"D");preview=(JButton)field(device,"C");
        home=(JButton)field(device,"H");unlock=(JButton)field(device,"I");reset=(JButton)field(device,"K");sleep=(JButton)field(device,"x");
        origin=(JButton)field(device,"z");workOrigin=(JButton)field(device,"y");penUp=(JButton)field(device,"g");penDown=(JButton)field(device,"f");
        source=(JLabel)field(device,"E");
        timer=new javax.swing.Timer(250,e->refresh());
    }

    public static void install(Object app)throws Exception{
        JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);
        if(frame.getRootPane().getClientProperty(KEY)!=null)return;
        MachinePanelWorkflow flow=new MachinePanelWorkflow(app);
        flow.setup();
        frame.getRootPane().putClientProperty(KEY,flow);
    }
    public static void refresh(Object app)throws Exception{
        JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);
        Object flow=frame.getRootPane().getClientProperty(KEY);
        if(flow instanceof MachinePanelWorkflow)((MachinePanelWorkflow)flow).refresh();
    }
    public static boolean sourceLocked(Object app)throws Exception{
        JFrame frame=(JFrame)app.getClass().getMethod("f").invoke(app);
        Object flow=frame.getRootPane().getClientProperty(KEY);
        return flow instanceof MachinePanelWorkflow&&(((MachinePanelWorkflow)flow).frozenSource!=null||((MachinePanelWorkflow)flow).autoZeroWaiting||((MachinePanelWorkflow)flow).activeSource!=null);
    }
    public static void autoZeroHandoff(Object device,Object worker){synchronized(AUTO_ZERO_LOCK){if(taskCurrent(device,worker))AUTO_ZERO_WORKERS.put(device,worker);}}
    public static void cancelAutoZeroHandoff(Object device){cancelHandoff(device);}
    private static Object cancelHandoff(Object device){synchronized(AUTO_ZERO_LOCK){Object worker=AUTO_ZERO_WORKERS.remove(device);if(worker!=null)CANCELED_WORKERS.add(worker);return worker;}}
    private static boolean taskCurrent(Object device,Object worker){TaskToken token=WORKER_TASKS.get(worker);return token!=null&&!token.canceled&&CURRENT_TASKS.get(device)==token;}
    private static void clearTask(Object device){synchronized(AUTO_ZERO_LOCK){CURRENT_TASKS.remove(device);}}
    private static void beginTask(Object device){synchronized(AUTO_ZERO_LOCK){SENT_DEVICES.remove(device);ATTEMPTED_DEVICES.remove(device);ACTIVE_GENERATORS.remove(device);CURRENT_TASKS.put(device,new TaskToken());}}
    private static TaskToken cancelTask(Object device){synchronized(AUTO_ZERO_LOCK){TaskToken token=CURRENT_TASKS.get(device);if(token!=null)token.canceled=true;cancelHandoff(device);return token;}}
    public static void bindGenerator(Object device,Object worker){synchronized(AUTO_ZERO_LOCK){TaskToken token=CURRENT_TASKS.get(device);if(token!=null)WORKER_TASKS.put(worker,token);}}
    public static boolean allowAutoZeroSend(Object worker){
        synchronized(AUTO_ZERO_LOCK){
            if(CANCELED_WORKERS.contains(worker))return false;
            for(Map.Entry<Object,Object> entry:AUTO_ZERO_WORKERS.entrySet())if(entry.getValue()==worker){
                if(!taskCurrent(entry.getKey(),worker)){AUTO_ZERO_WORKERS.remove(entry.getKey(),worker);CANCELED_WORKERS.add(worker);return false;}
                if(AUTO_ZERO_WORKERS.remove(entry.getKey(),worker)){SENT_WORKERS.add(worker);SENT_DEVICES.put(entry.getKey(),worker);return true;}
            }
            return false;
        }
    }
    public static boolean generationStarted(Object device,Object worker){synchronized(AUTO_ZERO_LOCK){if(!taskCurrent(device,worker))return false;ACTIVE_GENERATORS.put(device,worker);SENDING_WORKER.set(worker);return true;}}
    public static boolean startAutoZero(Object sender,Object device,Object worker){
        synchronized(AUTO_ZERO_LOCK){
            if(!taskCurrent(device,worker))return false;
            WORKER_TASKS.get(worker).sendAttempted=true;
            AUTO_ZERO_WORKERS.put(device,worker);
            try{invokeSender(sender);return true;}
            catch(RuntimeException error){cancelHandoff(device);throw error;}
            catch(Error error){cancelHandoff(device);throw error;}
        }
    }
    public static void startGlobalSender(Object sender,Object device){
        synchronized(AUTO_ZERO_LOCK){
            Object worker=SENDING_WORKER.get();
            if(worker!=null&&!taskCurrent(device,worker))return;
            TaskToken token=CURRENT_TASKS.get(device);if(token!=null)token.sendAttempted=true;
            ATTEMPTED_DEVICES.add(device);
            invokeSender(sender);
        }
    }
    private static void invokeSender(Object sender){
        try{sender.getClass().getMethod("e").invoke(sender);}
        catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new IllegalStateException(cause);}
        catch(Exception error){throw new IllegalStateException("无法启动设备发送器",error);}
    }
    public static void dispatchAutoZeroCallback(Object worker,Object callback,Object event){
        synchronized(AUTO_ZERO_LOCK){
            if(!allowAutoZeroSend(worker))return;
            Object prior=SENDING_WORKER.get();SENDING_WORKER.set(worker);
            try{
                Method original=callback.getClass().getDeclaredMethod("modernizationOnEvent",Class.forName("flash.events.Event"));
                original.setAccessible(true);original.invoke(callback,event);
            }catch(InvocationTargetException error){Throwable cause=error.getCause();autoZeroCallbackFailed(worker,cause);if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new IllegalStateException(cause);}
            catch(Exception error){autoZeroCallbackFailed(worker,error);throw new IllegalStateException("无法启动自动设原点后的书写",error);}
            finally{if(prior==null)SENDING_WORKER.remove();else SENDING_WORKER.set(prior);}
        }
    }
    public static void senderAttempted(Object device){synchronized(AUTO_ZERO_LOCK){ATTEMPTED_DEVICES.add(device);}}
    private static void autoZeroCallbackFailed(Object worker,Throwable error){
        Object device=null;TaskToken token=null;boolean attempted=false;
        synchronized(AUTO_ZERO_LOCK){
            for(Map.Entry<Object,Object> entry:SENT_DEVICES.entrySet())if(entry.getValue()==worker){device=entry.getKey();break;}
            if(device==null)return;
            token=WORKER_TASKS.get(worker);
            attempted=ATTEMPTED_DEVICES.contains(device);
            if(!attempted){SENT_DEVICES.remove(device,worker);SENT_WORKERS.remove(worker);CANCELED_WORKERS.add(worker);}
        }
        final Object failedDevice=device;final TaskToken failedToken=token;final boolean senderWasAttempted=attempted;
        Runnable update=()->{for(Window window:Window.getWindows())if(window instanceof JFrame){
            Object flow=((JFrame)window).getRootPane().getClientProperty(KEY);
            if(flow instanceof MachinePanelWorkflow){MachinePanelWorkflow panel=(MachinePanelWorkflow)flow;if(panel.device!=failedDevice)continue;
                synchronized(AUTO_ZERO_LOCK){if(CURRENT_TASKS.get(failedDevice)!=failedToken)return;if(failedToken.canceled){panel.autoZeroWaiting=false;panel.writeFailureLatch=true;panel.refresh();return;}}
                if(panel.activeSource==null)return;
                panel.autoZeroWaiting=false;
                if(senderWasAttempted){panel.writeFailureLatch=true;panel.handoffFailureMessage="发送状态不明，请确认设备停机后重启软件："+error.getMessage();}
                else{panel.releaseSnapshot();panel.activeSource=null;clearTask(failedDevice);panel.unlockModes();panel.handoffFailureMessage="自动设原点后的书写未启动："+error.getMessage();}
                panel.refresh();return;
            }
        }};
        if(SwingUtilities.isEventDispatchThread())update.run();else SwingUtilities.invokeLater(update);
    }
    public static void generationFinished(Object device,Object worker,boolean autoZeroStarted){
        if(SENDING_WORKER.get()==worker)SENDING_WORKER.remove();
        if(!autoZeroStarted)synchronized(AUTO_ZERO_LOCK){if(AUTO_ZERO_WORKERS.get(device)==worker){CANCELED_WORKERS.add(worker);AUTO_ZERO_WORKERS.remove(device,worker);}}
        SwingUtilities.invokeLater(()->{
            if(!ACTIVE_GENERATORS.remove(device,worker))return;
            TaskToken token;synchronized(AUTO_ZERO_LOCK){token=WORKER_TASKS.get(worker);if(token==null||CURRENT_TASKS.get(device)!=token)return;}
            for(Window window:Window.getWindows())if(window instanceof JFrame){
                Object flow=((JFrame)window).getRootPane().getClientProperty(KEY);
                if(flow instanceof MachinePanelWorkflow){MachinePanelWorkflow panel=(MachinePanelWorkflow)flow;
                    if(panel.device==device){if(token.canceled){panel.failPreparation(token);return;}boolean pending,sent;synchronized(AUTO_ZERO_LOCK){pending=AUTO_ZERO_WORKERS.get(device)==worker&&!CANCELED_WORKERS.contains(worker);sent=SENT_WORKERS.contains(worker);}panel.autoZeroWaiting=autoZeroStarted&&pending;panel.releaseSnapshot();boolean running=panel.pause.isEnabled()||panel.stop.isEnabled();panel.runningSeen=running&&!pending;if((!autoZeroStarted||!pending&&!sent)&&!running&&!panel.writeFailureLatch)synchronized(AUTO_ZERO_LOCK){if(taskCurrent(device,worker)){panel.activeSource=null;clearTask(device);panel.unlockModes();}}panel.refresh();return;}
                }
            }
        });
    }

    private void setup(){
        load.setText("打开 G-code…");clear.setText("清除外部文件");preview.setText("刀路预览");
        workOrigin.setIcon(null);workOrigin.setText("工作原点");workOrigin.getAccessibleContext().setAccessibleName("回工作原点");
        workOrigin.setToolTipText("回工作原点；设备会运动。与机械回零不同");
        origin.setText("设置起写原点");origin.setToolTipText("按软件设置的 X/Y 偏移设置工作坐标原点");
        penUp.setToolTipText("按当前设备的抬笔配置执行");penDown.setToolTipText("按当前设备的落笔配置执行");
        sleep.setText("设备休眠…");sleep.setToolTipText("停止电机保持力；Grbl 设备需要复位后才能继续");
        home.setText("机械回零…");home.setToolTipText("移动到机械原点；需设备已正确配置回零和限位");
        unlock.setText("解除警报…");unlock.setToolTipText("解除报警锁定；请先排查报警原因");
        reset.setText("设备复位…");reset.setToolTipText("重启控制器；当前任务将中断");
        if(machineType().equals("Marlin")){
            home.setText("Z 轴回零…");home.setToolTipText("Marlin 发送 G28 Z；只对 Z 轴回零");
            unlock.setVisible(false);reset.setVisible(false);
            sleep.setText("释放电机…");sleep.setToolTipText("Marlin 发送 M18，释放步进电机保持力");
        }
        workOrigin.setName("MachineWorkOrigin");origin.setName("MachineSetOrigin");
        source.setName("MachineWriteSource");
        controls.setLayout(new DeviceLayout(controls,home,unlock,reset,sleep,origin,workOrigin,penUp,penDown));
        controls.setPreferredSize(new Dimension(0,365));
        for(JButton button:new JButton[]{sleep,home,unlock,reset})confirmBeforeAction(button);
        java.util.List<ActionListener> original=new ArrayList<ActionListener>();for(ActionListener listener:write.getActionListeners())if(listener==device){write.removeActionListener(listener);original.add(listener);}
        write.addActionListener(event->startWrite(event,original));
        for(JButton button:new JButton[]{load,clear,stop})button.addActionListener(e->SwingUtilities.invokeLater(this::refresh));
        installFirmwareFailures();
        installSenderCompletion();
        frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){timer.stop();releaseSnapshot();}});
        timer.start();refresh();
    }
    private void installFirmwareFailures(){
        try{
            Object manager=Class.forName("com.kenjoy.kenjoycnc.machine.g").getMethod("a").invoke(null);Object controller=manager.getClass().getMethod("e").invoke(manager);
            Class<?> listenerType=Class.forName("flash.events.IEventListener");
            firmwareListener=Proxy.newProxyInstance(listenerType.getClassLoader(),new Class<?>[]{listenerType},(proxy,method,args)->{
                if(method.getName().equals("onEvent")){TaskToken canceled=cancelTask(device);SwingUtilities.invokeLater(()->failPreparation(canceled));return null;}
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==args[0];
                return "MachinePanelWorkflow firmware listener";
            });
            Method add=controller.getClass().getMethod("addEventListener",String.class,listenerType);
            add.invoke(controller,"FirmwareEventError",firmwareListener);add.invoke(controller,"FirmwareEventDisconnected",firmwareListener);
        }catch(Exception error){throw new IllegalStateException("无法监听设备准备失败",error);}
    }
    private void installSenderCompletion(){
        try{
            Object manager=Class.forName("com.kenjoy.kenjoycnc.machine.g").getMethod("a").invoke(null);Object sender=manager.getClass().getMethod("i").invoke(manager);
            Class<?> listenerType=Class.forName("flash.events.IEventListener");
            senderCompletionListener=Proxy.newProxyInstance(listenerType.getClassLoader(),new Class<?>[]{listenerType},(proxy,method,args)->{
                if(method.getName().equals("onEvent")){Object expected;synchronized(AUTO_ZERO_LOCK){expected=SENT_DEVICES.get(device);}if(expected!=null)SwingUtilities.invokeLater(()->senderFinished(expected));return null;}
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==args[0];
                return "MachinePanelWorkflow sender completion listener";
            });
            sender.getClass().getMethod("addEventListener",String.class,listenerType).invoke(sender,"Complete",senderCompletionListener);
        }catch(Exception error){throw new IllegalStateException("无法监听书写完成事件",error);}
    }
    private void senderFinished(Object expectedWorker){
        boolean handedOff;synchronized(AUTO_ZERO_LOCK){if(!taskCurrent(device,expectedWorker))return;handedOff=SENT_DEVICES.remove(device,expectedWorker);if(handedOff)ATTEMPTED_DEVICES.remove(device);}
        if(!handedOff||activeSource==null||writeFailureLatch)return;
        autoZeroWaiting=false;releaseSnapshot();synchronized(AUTO_ZERO_LOCK){if(taskCurrent(device,expectedWorker)){activeSource=null;clearTask(device);unlockModes();}}refresh();
    }
    private void failPreparation(){
        failPreparation(cancelTask(device));
    }
    private void failPreparation(TaskToken token){
        if(token==null)return;
        synchronized(AUTO_ZERO_LOCK){if(CURRENT_TASKS.get(device)!=token)return;}
        if(!autoZeroWaiting&&activeSource==null&&frozenSource==null)return;
        autoZeroWaiting=false;
        boolean running=pause.isEnabled()||stop.isEnabled();
        boolean release=false;
        if(token!=null)synchronized(AUTO_ZERO_LOCK){
            if(CURRENT_TASKS.get(device)!=token)return;
            release=token.canceled&&!token.sendAttempted&&!ATTEMPTED_DEVICES.contains(device)&&!SENT_DEVICES.containsKey(device)&&!ACTIVE_GENERATORS.containsKey(device)&&!running;
            if(release)CURRENT_TASKS.remove(device,token);
        }
        if(release){activeSource=null;writeFailureLatch=false;runningSeen=false;releaseSnapshot();unlockModes();handoffFailureMessage="设备准备中断，书写未启动";}
        else{if(activeSource!=null)writeFailureLatch=true;if(running)runningSeen=true;}
        refreshDeviceState();refresh();
    }

    private void confirmBeforeAction(JButton button){
        java.util.List<ActionListener> listeners=new ArrayList<ActionListener>();
        for(ActionListener listener:button.getActionListeners())if(listener==device){button.removeActionListener(listener);listeners.add(listener);}
        button.addActionListener(event->{
            boolean marlin=machineType().equals("Marlin");
            String action=button==sleep?(marlin?"释放步进电机保持力":"让设备休眠并释放电机保持力"):
                button==home?(marlin?"仅让 Z 轴回零":"移动设备到机械原点"):button==unlock?"解除警报锁定": "复位设备控制器";
            String detail=button==sleep?(marlin?"将发送 M18。请确认笔和机构已安全停靠。":"请确认笔和机构已安全停靠。Grbl 休眠后通常需复位，原位置可能不再可靠。"):
                button==home?(marlin?"将发送 G28 Z。请确认 Z 轴回零、限位与运动行程配置正确。":"请确认回零、限位与运动行程配置正确。"):
                button==unlock?"请先排查警报原因；解锁不会修复故障。":"当前运行任务会中断；复位后请重新确认坐标。";
            if(JOptionPane.showConfirmDialog(frame,action+"？\n"+detail,"确认设备操作",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)return;
            for(ActionListener listener:listeners)listener.actionPerformed(event);
        });
    }

    private void startWrite(ActionEvent event,java.util.List<ActionListener> original){
        boolean vendorInvoked=false;
        try{
            if(sourceLocked(app)){notice("已有书写任务正在准备或执行，请等待任务结束。");return;}
            if(alarmed()){notice("设备处于报警状态，请先排查原因并重新确认坐标。");return;}
            File external=(File)field(device,"F");Object mode=modes.getSelectedItem();
            if(machineType().equals("K_AT")){
                int count=katQueueCount();if(count==0){notice("待写队列没有未完成的文件，请先添加书写文件。");return;}
                frozenSource="设备队列："+count+" 个待写文件";
            }else if(external!=null){
                if(!external.isFile()||external.length()==0){notice("外部 G-code 文件为空或不存在，请重新选择。");return;}
                frozenSource="外部 G-code："+external.getName();
            }else if(isNote(mode)){
                if(!Boolean.TRUE.equals(mode.getClass().getMethod("beginMachineJob").invoke(mode))){notice("当前笔记页没有可写入的刀路，请先完成排版。");return;}
                frozenNote=mode;frozenSource=String.valueOf(mode.getClass().getMethod("machineJobSource").invoke(mode));
            }else{
                Object generator=mode==null?null:mode.getClass().getMethod("getGcodeGenerator").invoke(mode);
                if(generator==null){notice("当前模式没有可写入的刀路，请先更新内容。");return;}
                frozenSource="当前画布："+String.valueOf(mode);
            }
            beginTask(device);
            activeSource=frozenSource;runningSeen=false;autoZeroWaiting=false;writeFailureLatch=false;handoffFailureMessage=null;modesEnabledBefore=modes.isEnabled();modeLocked=true;modes.setEnabled(false);write.setEnabled(false);refresh();
            boolean kat=machineType().equals("K_AT");JLabel status=(JLabel)field(device,"r");String priorStatus=status.getText();
            if(!kat)status.setText("正在检查书写来源");
            for(ActionListener listener:original){vendorInvoked=true;listener.actionPerformed(event);}
            if(kat){releaseSnapshot();if(!(pause.isEnabled()||stop.isEnabled())){activeSource=null;clearTask(device);unlockModes();}}
            else if("正在检查书写来源".equals(status.getText())){status.setText(priorStatus);releaseSnapshot();activeSource=null;clearTask(device);unlockModes();}
            SwingUtilities.invokeLater(this::refresh);
        }catch(Exception error){
            boolean possiblySubmitted=vendorInvoked;
            if(possiblySubmitted)try{if(!machineType().equals("K_AT")&&"正在检查书写来源".equals(((JLabel)field(device,"r")).getText()))possiblySubmitted=false;}catch(Exception stateError){error.addSuppressed(stateError);}
            if(!possiblySubmitted){releaseSnapshot();activeSource=null;clearTask(device);unlockModes();}
            else SwingUtilities.invokeLater(this::refresh);
            Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
            notice((possiblySubmitted?"设备处理发生错误；任务可能已经提交，请确认设备已停止后再重启软件。错误：":"无法开始书写：")+cause.getMessage());
        }
    }
    private void notice(String message){JOptionPane.showMessageDialog(frame,message,"书写",JOptionPane.WARNING_MESSAGE);}
    private boolean alarmed()throws Exception{if(!machineType().equals("Grbl"))return false;JLabel status=(JLabel)field(device,"J");String text=status.getText();return status.isVisible()&&text!=null&&text.toUpperCase(java.util.Locale.ROOT).contains("ALARM");}
    private boolean machineConnected()throws Exception{Object manager=Class.forName("com.kenjoy.kenjoycnc.machine.g").getMethod("a").invoke(null);Object controller=manager.getClass().getMethod("e").invoke(manager);return Boolean.TRUE.equals(controller.getClass().getMethod("p").invoke(controller));}
    private String machineType(){try{Field machineField=Class.forName("com.kenjoy.kenjoycnc.o.c").getDeclaredField("e");machineField.setAccessible(true);Object machine=machineField.get(null);Object type=field(machine,"type");return ((Enum)type).name();}catch(Exception error){throw new IllegalStateException("无法读取机器类型",error);}}
    private int katQueueCount()throws Exception{DefaultListModel queue=(DefaultListModel)field(device,"S");int count=0;for(int i=0;i<queue.size();i++){Object item=queue.get(i);Object status=field(item,"status");if(!((Enum)status).name().equals("finished"))count++;}return count;}
    private void releaseSnapshot(){if(frozenNote!=null)try{frozenNote.getClass().getMethod("endMachineJob").invoke(frozenNote);}catch(Exception e){e.printStackTrace();}frozenNote=null;frozenSource=null;runningSeen=false;if(!autoZeroWaiting&&activeSource==null)unlockModes();refreshDeviceState();}
    private void unlockModes(){if(modeLocked){modes.setEnabled(modesEnabledBefore);modeLocked=false;}}
    private void refreshDeviceState(){try{Method method=device.getClass().getDeclaredMethod("g");method.setAccessible(true);method.invoke(device);}catch(Exception error){System.err.println("MACHINE_STATE_REFRESH "+error);}}
    private void refresh(){
        try{
            Dimension preferred=controls.getPreferredSize();
            if(preferred.height!=NativeDpi.px(365)){controls.setPreferredSize(new Dimension(preferred.width,NativeDpi.px(365)));controls.revalidate();}
            boolean running=pause.isEnabled()||stop.isEnabled();
            boolean canceled;synchronized(AUTO_ZERO_LOCK){TaskToken token=CURRENT_TASKS.get(device);canceled=token!=null&&token.canceled;}
            boolean pendingHandoff=false;if(autoZeroWaiting)synchronized(AUTO_ZERO_LOCK){pendingHandoff=AUTO_ZERO_WORKERS.containsKey(device);}
            if(running&&!pendingHandoff)runningSeen=true;
            if(autoZeroWaiting&&running&&!pendingHandoff)autoZeroWaiting=false;
            if(autoZeroWaiting&&(!machineConnected()||alarmed()))failPreparation();
            if(autoZeroWaiting&&!running&&runningSeen&&!canceled){autoZeroWaiting=false;unlockModes();refreshDeviceState();}
            boolean handedOff;synchronized(AUTO_ZERO_LOCK){handedOff=SENT_DEVICES.containsKey(device);}
            if(activeSource!=null&&!running&&runningSeen&&!autoZeroWaiting&&!writeFailureLatch&&!handedOff&&!canceled){boolean released=false;synchronized(AUTO_ZERO_LOCK){TaskToken token=CURRENT_TASKS.get(device);if(token==null||!token.canceled){activeSource=null;runningSeen=false;clearTask(device);unlockModes();released=true;}}if(released)refreshDeviceState();}
            File external=(File)field(device,"F");
            String label;
            if(handoffFailureMessage!=null)label=handoffFailureMessage;
            else if(frozenSource!=null)label="正在生成刀路："+frozenSource;
            else if(autoZeroWaiting)label="正在等待自动设原点完成："+activeSource;
            else if(activeSource!=null)label="正在书写："+activeSource;
            else if(machineType().equals("K_AT"))label="书写来源：设备队列（"+katQueueCount()+" 个待写文件）";
            else if(external!=null)label="外部 G-code："+external.getName()+"（优先于画布）";
            else {
                Object mode=modes.getSelectedItem();
                label=isNote(mode)?"书写来源："+mode.getClass().getMethod("currentPageSource").invoke(mode):"书写来源：当前画布";
            }
            source.setVisible(true);source.setText(clip(label,26));source.setToolTipText(label);
            clear.setEnabled(external!=null&&!running&&!sourceLocked(app));
            load.setEnabled(!running&&!sourceLocked(app));
            if(sourceLocked(app)||alarmed())write.setEnabled(false);
        }catch(Exception error){System.err.println("MACHINE_PANEL_STATUS "+error);}
    }
    private static String clip(String text,int limit){return text.length()<=limit?text:text.substring(0,limit-1)+"…";}
    private static boolean isNote(Object mode){return mode!=null&&mode.getClass().getName().equals("com.kenjoy.kenjoycnc.processTabs.NoteTab");}
    private static Object field(Object owner,String name)throws Exception{for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}

    private static final class DeviceLayout implements LayoutManager {
        private final Map<Component,Rectangle> positions=new IdentityHashMap<Component,Rectangle>();
        DeviceLayout(JPanel panel,JButton home,JButton unlock,JButton reset,JButton sleep,JButton origin,JButton workOrigin,JButton penUp,JButton penDown){
            double priorScale=Boolean.TRUE.equals(panel.getClientProperty("modernization.nativeDpi"))?NativeDpi.scale():1;
            for(Component child:panel.getComponents()){Rectangle r=child.getBounds();positions.put(child,new Rectangle((int)Math.round(r.x/priorScale),(int)Math.round(r.y/priorScale),(int)Math.round(r.width/priorScale),(int)Math.round(r.height/priorScale)));}
            positions.put(workOrigin,new Rectangle(101,67,98,30));
            for(Component child:panel.getComponents())if(child instanceof JButton){String label=((JButton)child).getText();if("X-".equals(label))positions.put(child,new Rectangle(18,67,70,30));else if("X+".equals(label))positions.put(child,new Rectangle(212,67,70,30));}
            positions.put(origin,new Rectangle(18,182,264,29));
            positions.put(penUp,new Rectangle(18,221,127,29));positions.put(penDown,new Rectangle(155,221,127,29));
            positions.put(home,new Rectangle(18,280,127,29));positions.put(unlock,new Rectangle(155,280,127,29));
            positions.put(reset,new Rectangle(18,318,127,29));positions.put(sleep,new Rectangle(155,318,127,29));
            for(Component child:panel.getComponents())if(child instanceof JComboBox)positions.put(child,new Rectangle(94,151,185,28));
            for(Component child:panel.getComponents())if(child instanceof JLabel)positions.put(child,new Rectangle(18,154,76,22));
        }
        public void addLayoutComponent(String name,Component component){}public void removeLayoutComponent(Component component){positions.remove(component);}
        public Dimension preferredLayoutSize(Container parent){return new Dimension(NativeDpi.px(300),NativeDpi.px(365));}
        public Dimension minimumLayoutSize(Container parent){return new Dimension(NativeDpi.px(260),NativeDpi.px(365));}
        public void layoutContainer(Container parent){double scale=NativeDpi.scale();double stretch=Math.max(.8,parent.getWidth()/(300.0*scale));
            for(Component child:parent.getComponents()){Rectangle r=positions.get(child);if(r==null)continue;int x=(int)Math.round(r.x*scale*stretch),w=(int)Math.round(r.width*scale*stretch);child.setBounds(x,NativeDpi.px(r.y),w,NativeDpi.px(r.height));}
        }
    }
}

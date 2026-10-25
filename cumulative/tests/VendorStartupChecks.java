import java.awt.*;
import java.nio.file.*;
import javax.swing.*;

/** Actual vendor launcher + encrypted class loader, without development overlays. */
public final class VendorStartupChecks {
    private static boolean startupError(Component c) {
        if(c instanceof JLabel && ((JLabel)c).getText()!=null && ((JLabel)c).getText().matches("(?s).*error\\s*-4.*"))return true;
        if(c instanceof Container)for(Component child:((Container)c).getComponents())if(startupError(child))return true;
        return false;
    }
    public static void main(String[] args)throws Exception {
        final Path result=Paths.get(System.getProperty("startup.result"));
        Thread observer=new Thread(()->{
            try {
                for(int attempt=0;attempt<80;attempt++) {
                    Thread.sleep(500);final int[] state={0};
                    EventQueue.invokeAndWait(()->{
                        for(Window w:Window.getWindows())if(w.isShowing()) {
                            if(startupError(w))state[0]=-1;
                            if(w instanceof JFrame && Boolean.TRUE.equals(((JFrame)w).getRootPane().getClientProperty("modernization.preinstalled")))state[0]=1;
                        }
                    });
                    if(state[0]<0)throw new AssertionError("Vendor launcher displayed error -4");
                    if(state[0]>0) {
                        ClassLoader loader=UIManager.getLookAndFeel().getClass().getClassLoader();
                        Class<?> layout=loader.loadClass("com.kenjoy.kenjoycnc.note.NoteLayout");
                        if(!"".equals(layout.getMethod("notice").invoke(layout.newInstance())))throw new AssertionError("Unexpected layout notice");
                        loader.loadClass("com.kenjoy.kenjoycnc.processTabs.NoteTab").getMethod("clearMissingCharacters",boolean.class);
                        Files.write(result,"PASS original vendor launcher, encrypted loader, visible main window, NoteLayout.notice and cleanup API".getBytes("UTF-8"));
                        System.exit(0);
                    }
                }
                throw new AssertionError("Vendor startup did not reach a visible main window within 40 seconds");
            }catch(Throwable error){error.printStackTrace();try{Files.write(result,("FAIL "+error).getBytes("UTF-8"));}catch(Exception ignored){}System.exit(2);}
        },"vendor-startup-observer");observer.start();
        Class.forName("com.kenjoy.cnc.launcher.KenjoyDrawLauncher").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
    }
}

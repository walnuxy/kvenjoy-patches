/** Match the existing NoteLauncher setup for the bundled research Java 8; no application changes. */
public final class FocusedCheckLauncher {
    public static void main(String[] args) throws Exception {
        // Its default seed path blocks in modified NetworkInterface code. Use the installed Windows provider.
        java.security.Provider windows=java.security.Security.getProvider("SunMSCAPI");
        if(windows!=null){java.security.Security.removeProvider("SunMSCAPI");java.security.Security.insertProviderAt(windows,1);}
        String[] forwarded=java.util.Arrays.copyOfRange(args,1,args.length);
        Class.forName(args[0]).getMethod("main",String[].class).invoke(null,(Object)forwarded);
    }
}

import java.nio.file.*;
import java.util.*;
public final class RecoveryChecks {
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[1]).toAbsolutePath();Map<String,byte[]> original=FontPatcher.unzip(Files.readAllBytes(Paths.get(args[0])));UpdatePatcherTest.fixture(root,"vendor",original);Map<String,String> before=UpdatePatcherTest.snapshot(root);
        UpdatePatcher tool=new UpdatePatcher(root);tool.apply(false);Path record=root.resolve(UpdatePatcher.STATE+"/receipt.properties");Properties r=tool.receipt(record);r.setProperty("status","prepared");tool.save(record,r);
        // Power loss after one file was written: one target is before, another is after.
        int core=-1;for(int i=0;i<Integer.parseInt(r.getProperty("count"));i++)if(r.getProperty(i+".path").equals("core"))core=i;
        Path backup=root.resolve(UpdatePatcher.STATE).resolve(r.getProperty("transaction")).resolve(core+".before");byte[] prior=Files.readAllBytes(backup);Files.write(root.resolve("core"),prior);
        UpdatePatcherTest.fails(()->tool.apply(false),"interrupted transaction requires recovery before reapply");tool.rollback();UpdatePatcherTest.yes(before.equals(UpdatePatcherTest.snapshot(root)),"interrupted mixed before/after state rolls back exactly");
        tool.apply(false);r=tool.receipt(record);backup=root.resolve(UpdatePatcher.STATE).resolve(r.getProperty("transaction")).resolve("0.before");byte[] saved=Files.readAllBytes(backup);Files.write(backup,new byte[]{0,1,2});Map<String,String> installed=UpdatePatcherTest.snapshot(root);UpdatePatcherTest.fails(tool::rollback,"corrupted backup refused");UpdatePatcherTest.yes(installed.equals(UpdatePatcherTest.snapshot(root)),"backup failure commits no rollback writes");Files.write(backup,saved);tool.rollback();
        UpdatePatcherTest.yes(before.equals(UpdatePatcherTest.snapshot(root)),"valid backup restores exact state after refusal");
        try(java.nio.channels.FileChannel lock=java.nio.channels.FileChannel.open(root.resolve(UpdatePatcher.STATE+"/update.lock"),StandardOpenOption.WRITE);java.nio.channels.FileLock held=lock.lock()){
            UpdatePatcherTest.fails(()->tool.apply(false),"concurrent updater lock refused");
        }
        UpdatePatcherTest.remove(root);System.out.println("COMPLETE recoveryChecks="+UpdatePatcherTest.checks+" runtime="+System.getProperty("java.version"));
    }
}

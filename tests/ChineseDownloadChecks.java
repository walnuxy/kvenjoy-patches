import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit online integration test; uses only a synthetic installation. */
public final class ChineseDownloadChecks {
    public static void main(String[] args) throws Exception {
        Path target=FontPatcherTest.fixture(Paths.get(args[1]),FontPatcherTest.readJar(Paths.get(args[0])));
        FontPatcher tool=new FontPatcher(target);
        FontPatcherTest.yes(tool.planFonts().size()==85,"85 fonts planned on clean installation");
        tool.applyBundle();
        FontPatcherTest.yes(tool.inspect().installed && tool.planFonts().isEmpty(),"85 real fonts installed and hashes verified");
        String index=new String(FontPatcher.read(FontPatcher.resource("fonts/chinese-downloads.tsv")),StandardCharsets.UTF_8);
        FontPatcher offline=new FontPatcher(target,index,(url,size)->{throw new java.io.IOException("offline repeat attempted download");});
        byte[] core=Files.readAllBytes(target.resolve("core"));
        offline.applyBundle();
        FontPatcherTest.yes(Arrays.equals(core,Files.readAllBytes(target.resolve("core"))),"offline repeat preserves core");
        try(java.util.stream.Stream<Path> files=Files.list(target.resolve("gcodeFonts/中文-手写完整（GB2312）"))) {
            FontPatcherTest.yes(files.count()==15,"all 15 real handwriting fonts classified");
        }
        System.out.println("Online integration passed: "+FontPatcherTest.checks);
    }
}

"""Rebuild installer code using only the patch ZIP's non-machine-specific resources."""
import argparse
import io
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent
VERSION = "kvenjoy-font-patcher-20261022-v5"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jdk", type=Path, help="JDK 9+ root; otherwise JAVA_HOME/PATH")
    parser.add_argument("--reference-core", type=Path, help="optional original plaintext core JAR, kept outside this repo")
    parser.add_argument("--legacy-v2", type=Path, help="optional original font-cap v2 class, kept outside this repo")
    parser.add_argument("--test-java", type=Path, help="Java executable for tests, e.g. target app's bundled Java 8")
    args = parser.parse_args()
    jdk = args.jdk or (Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None)
    suffix = ".exe" if os.name == "nt" else ""
    javac = str(jdk / "bin" / ("javac" + suffix)) if jdk else shutil.which("javac")
    java = str(args.test_java) if args.test_java else (str(jdk / "bin" / ("java" + suffix)) if jdk else shutil.which("java"))
    if not javac:
        parser.error("Set JAVA_HOME or --jdk to JDK 9+ (end users do not need a JDK)")
    if bool(args.reference_core) != bool(args.legacy_v2):
        parser.error("Supply both --reference-core and --legacy-v2 to run the reference-dependent tests")
    with zipfile.ZipFile(ROOT / "dist" / (VERSION + ".zip")) as release:
        with zipfile.ZipFile(io.BytesIO(release.read("font-patcher.jar"))) as template:
            resources = {n: template.read(n) for n in template.namelist()
                         if n == "compatibility.properties" or n.startswith("payload/")}
    if len(resources) != 8:
        raise RuntimeError("Expected compatibility fingerprints and exactly seven payload classes")
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="compile-", dir=build) as tmp:
        classes = Path(tmp)
        subprocess.run([javac, "--release", "8", "-encoding", "UTF-8", "-proc:none",
                        "-d", str(classes), str(ROOT / "src/FontPatcher.java")], check=True)
        jar = build / "font-patcher.jar"
        with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as out:
            out.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nMain-Class: FontPatcher\r\n\r\n")
            for file in sorted(classes.glob("FontPatcher*.class")):
                out.writestr(file.name, file.read_bytes())
            for name, data in resources.items():
                out.writestr(name, data)
        if args.reference_core:
            testclasses = classes / "tests"
            testclasses.mkdir()
            subprocess.run([javac, "--release", "8", "-encoding", "UTF-8", "-cp", str(jar),
                            "-d", str(testclasses), str(ROOT / "tests/FontPatcherTest.java")], check=True)
            payload = classes / "payload.jar"
            with zipfile.ZipFile(payload, "w", zipfile.ZIP_DEFLATED) as out:
                for name, data in resources.items():
                    if name.startswith("payload/"):
                        out.writestr(name[len("payload/"):], data)
            subprocess.run([java, "-Xverify:all", "-cp", str(testclasses) + os.pathsep + str(jar),
                            "FontPatcherTest", str(args.reference_core.resolve()), str(payload),
                            str(classes / "fixtures"), str(args.legacy_v2.resolve()),
                            str(ROOT / "dist/kvenjoy-font-patcher-20261022-v1.zip"),
                            str(ROOT / "dist/kvenjoy-font-patcher-20261022-v2.zip"),
                            str(ROOT / "dist/kvenjoy-font-patcher-20261022-v3.zip"),
                            str(ROOT / "dist/kvenjoy-font-patcher-20261022-v4.zip")], check=True)
    print("Built", jar)
    print("Use the launcher scripts beside this JAR, or: java -jar font-patcher.jar --check <installation-folder>")


if __name__ == "__main__":
    main()

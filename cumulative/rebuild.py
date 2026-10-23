"""Rebuild installer code from this public bundle; reuse the verified, machine-independent payload."""
from pathlib import Path
import argparse, hashlib, json, os, shutil, subprocess, zipfile
HERE=Path(__file__).resolve().parent
def main():
    p=argparse.ArgumentParser();p.add_argument('--jdk',type=Path);args=p.parse_args()
    javac=str(args.jdk/'bin/javac.exe') if args.jdk else shutil.which('javac')
    if not javac:raise SystemExit('Provide --jdk or put a JDK 9+ on PATH. End users do not need a JDK.')
    source=HERE/'update-patcher.jar'
    if not source.exists():source=HERE/'cumulative/update-patcher.jar'
    sourceRoot=source.parent;out=sourceRoot/'build';classes=out/'classes';classes.mkdir(parents=True,exist_ok=True)
    subprocess.run([javac,'--release','8','-encoding','UTF-8','-d',str(classes),str(sourceRoot/'src/UpdatePatcher.java'),str(sourceRoot/'src/FontPatcher.java')],check=True)
    jar=out/'update-patcher.jar'
    with zipfile.ZipFile(source) as reference,zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
        names={p.name:p.read_bytes() for p in classes.glob('*.class')}
        expected={n for n in reference.namelist() if '/' not in n and n.endswith('.class')}
        assert set(names)==expected,'Compiled class set differs from release'
        for info in reference.infolist():z.writestr(info,names.get(info.filename,reference.read(info.filename)))
    print(json.dumps({'jar':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'size':jar.stat().st_size}))
if __name__=='__main__':main()

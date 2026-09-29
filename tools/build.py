#!/usr/bin/env python3
"""Build independent Android AARs and demo APKs with Android SDK + JDK, without Gradle."""
import argparse, os, shutil, subprocess, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'build'
def run(args):subprocess.run([str(x) for x in args],check=True,cwd=ROOT)
def zip_tree(out,folder):
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(folder.rglob('*')):
            if p.is_file():z.write(p,p.relative_to(folder).as_posix())
def jdk(name):
    home=os.environ.get('JAVA_HOME');path=Path(home)/'bin'/(name+('.exe' if os.name=='nt' else '')) if home else shutil.which(name)
    if not path or not Path(path).exists():raise SystemExit('Set JAVA_HOME to a JDK (17 or newer), missing '+name)
    return str(path)
def sdk_paths():
    sdk=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    build_tools=os.environ.get('ANDROID_BUILD_TOOLS')
    jar=os.environ.get('ANDROID_JAR')
    if sdk:
        if not build_tools:build_tools=str(Path(sdk)/'build-tools/36.0.0')
        if not jar:jar=str(Path(sdk)/'platforms/android-36/android.jar')
    if not build_tools or not jar:raise SystemExit('Set ANDROID_SDK_ROOT (platform 36/build-tools 36.0.0), or ANDROID_BUILD_TOOLS and ANDROID_JAR')
    if not Path(jar).is_file():raise SystemExit('Missing android.jar: '+jar)
    return Path(build_tools),Path(jar)
def compile_java(name,sources,classpath):
    dest=BUILD/'classes'/name
    if dest.exists():shutil.rmtree(dest)
    dest.mkdir(parents=True)
    run([jdk('javac'),'--release','8','-encoding','UTF-8','-classpath',os.pathsep.join(str(x) for x in classpath),'-d',dest,*sources])
    jar=BUILD/'jars'/(name+'.jar');jar.parent.mkdir(exist_ok=True);zip_tree(jar,dest);return jar

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--variant',choices=['asr','pen','combined','all'],default='all');args=parser.parse_args()
    bt,android=sdk_paths();BUILD.mkdir(exist_ok=True)
    def tool(name):return bt/(name+('.bat' if os.name=='nt' and name in ['d8','apksigner'] else '.exe' if os.name=='nt' else ''))
    variants=['asr','pen','combined'] if args.variant=='all' else [args.variant]
    need_asr=any(v!='pen' for v in variants);need_pen=any(v!='asr' for v in variants)
    libs={};runtime=None;kotlin=None
    if need_asr:
        run([os.sys.executable,ROOT/'tools/bootstrap.py','--verify-only'])
        runtime=ROOT/'.deps/sherpa-onnx-1.13.8.aar';kotlin=ROOT/'.deps/kotlin-stdlib-2.2.0.jar'
        unpack=BUILD/'sherpa';unpack.mkdir(exist_ok=True)
        with zipfile.ZipFile(runtime) as z:
            (unpack/'classes.jar').write_bytes(z.read('classes.jar'))
        sherpa=unpack/'classes.jar'
    artifact=BUILD/'artifacts';artifact.mkdir(exist_ok=True)
    for module in ['asr-core','asr-android','asr-ui-android','pen-android']:
        if module.startswith('asr') and not need_asr:continue
        if module.startswith('pen') and not need_pen:continue
        sources=sorted((ROOT/'modules'/module/'src/main/java').rglob('*.java'))
        deps=[] if module=='asr-core' else [android]
        if module=='asr-android':deps+=[libs['asr-core'],sherpa,kotlin]
        if module=='asr-ui-android':deps+=[libs['asr-core'],libs['asr-android']]
        libs[module]=compile_java(module,sources,deps)
        if module=='asr-core':shutil.copy2(libs[module],artifact/'asr-core.jar');continue
        with zipfile.ZipFile(artifact/(module+'.aar'),'w',zipfile.ZIP_DEFLATED) as z:
            z.write(libs[module],'classes.jar');z.write(ROOT/'modules'/module/'src/main/AndroidManifest.xml','AndroidManifest.xml');z.writestr('R.txt','')
            if module=='asr-android':z.writestr('proguard.txt','-keep class com.k2fsa.sherpa.onnx.** { *; }\n')
    if need_asr:
        with zipfile.ZipFile(artifact/'asr-models.aar','w',zipfile.ZIP_DEFLATED) as z:
            z.writestr('AndroidManifest.xml','<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="io.github.purryc.asr.models"/>')
            # Android AAR consumers expect classes.jar even for an asset-only library.
            empty=BUILD/'empty';empty.mkdir(exist_ok=True);zip_tree(BUILD/'empty.jar',empty);z.write(BUILD/'empty.jar','classes.jar');z.writestr('R.txt','')
            for p in (ROOT/'models/assets').rglob('*'):
                if p.is_file():z.write(p,'assets/'+p.relative_to(ROOT/'models/assets').as_posix())
        shutil.copy2(runtime,artifact/runtime.name);shutil.copy2(kotlin,artifact/kotlin.name)
    key=BUILD/'debug.keystore'
    if not key.exists():run([jdk('keytool'),'-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Android Debug,O=APP ASR MODULE,C=CA','-keyalg','RSA','-validity','10000'])
    for variant in variants:
        asr=variant!='pen';pen=variant!='asr';folder=BUILD/variant;folder.mkdir(exist_ok=True)
        deps=[android];dex=[];sources=list((ROOT/'examples'/variant/'src').rglob('*.java'))
        if asr:
            dex += [libs['asr-core'],libs['asr-android'],libs['asr-ui-android'],sherpa,kotlin]
            sources+=list((ROOT/'tests/android/io/github/purryc/asr').rglob('*.java'))
        if pen:
            dex += [libs['pen-android']];sources+=list((ROOT/'examples/shared').rglob('*.java'))
            if not asr:sources+=list((ROOT/'tests/android/io/github/purryc/pen').rglob('*.java'))
        host=compile_java('demo-'+variant,sources,deps+dex)
        dex_folder=folder/'dex'
        if dex_folder.exists():shutil.rmtree(dex_folder)
        dex_folder.mkdir();run([tool('d8'),'--min-api','26','--lib',android,'--output',dex_folder,host,*dex])
        unsigned=folder/'unsigned.apk';cmd=[tool('aapt'),'package','-f','-M',ROOT/'examples'/variant/'AndroidManifest.xml','-I',android]
        if asr:
            assets=folder/'assets'
            if assets.exists():shutil.rmtree(assets)
            shutil.copytree(ROOT/'models/assets',assets)
            shutil.copytree(ROOT/'tests/fixtures',assets/'qa',ignore=shutil.ignore_patterns('manifest.json'))
            shutil.copytree(ROOT/'third-party',assets/'licenses',ignore=shutil.ignore_patterns('*.md'))
            cmd+=['-A',assets]
        run(cmd+['-F',unsigned])
        with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
            for p in dex_folder.glob('*.dex'):z.write(p,p.name)
            if asr:
                with zipfile.ZipFile(runtime) as r:
                    for name in r.namelist():
                        if name.startswith('jni/arm64-v8a/') and name.endswith('.so'):z.writestr('lib/'+name[4:],r.read(name))
        aligned=folder/'aligned.apk';apk=artifact/('app-'+variant+'-debug.apk')
        run([tool('zipalign'),'-f','4',unsigned,aligned]);run([tool('apksigner'),'sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',apk,aligned]);run([tool('apksigner'),'verify',apk])
        print(f'{apk.name}: {apk.stat().st_size:,} bytes',flush=True)
    print('Artifacts: build/artifacts/')
if __name__=='__main__':main()

#!/usr/bin/env python3
"""Behavioral JVM tests plus dependency and APK separation assertions."""
import os,subprocess,sys,zipfile,unittest
from pathlib import Path
from build import ROOT,BUILD,jdk,run
class Boundaries(unittest.TestCase):
    def test_no_cross_imports(self):
        for module in ['asr-core','asr-android','asr-ui-android']:
            for p in (ROOT/'modules'/module).rglob('*.java'):
                text=p.read_text();self.assertNotIn('io.github.purryc.pen',text)
                if module=='asr-core':self.assertNotIn('import android.',text);self.assertNotIn('com.k2fsa',text)
                if module=='asr-android':self.assertNotIn('android.widget',text);self.assertNotIn('asr.ui',text)
        for p in (ROOT/'modules/pen-android').rglob('*'):
            if p.suffix in ['.java','.xml']:
                text=p.read_text();self.assertNotIn('io.github.purryc.asr',text);self.assertNotIn('RECORD_AUDIO',text);self.assertNotIn('com.k2fsa',text)
    def test_built_apks(self):
        for variant in ['asr','pen','combined']:
            apk=BUILD/'artifacts'/('app-'+variant+'-debug.apk')
            if not apk.exists():continue
            with zipfile.ZipFile(apk) as z:
                names=z.namelist();dex=b''.join(z.read(n) for n in names if n.endswith('.dex'))
                if variant=='pen':
                    self.assertFalse(any(n.startswith(('assets/sensevoice','assets/zipformer','lib/')) for n in names))
                    self.assertNotIn(b'Lio/github/purryc/asr/',dex)
                    self.assertNotIn('android.permission.RECORD_AUDIO',z.read('AndroidManifest.xml').decode('utf-16le',errors='ignore'))
                if variant=='asr':self.assertNotIn(b'Lio/github/purryc/pen/',dex)
                if variant!='pen':self.assertIn('assets/sensevoice/model.int8.onnx',names);self.assertIn('assets/zipformer/encoder.int8.onnx',names)
def main():
    folder=BUILD/'tests';folder.mkdir(parents=True,exist_ok=True)
    sources=list((ROOT/'modules/asr-core/src/main/java').rglob('*.java'))+list((ROOT/'tests/java').glob('*.java'))
    sources+=[ROOT/'modules/asr-android/src/main/java/io/github/purryc/asr/android/AsrConfig.java']
    run([jdk('javac'),'-encoding','UTF-8','-d',folder,*sources])
    for name in ['SpeechSessionTest','SpeechDraftsTest','AsrConfigTest']:run([jdk('java'),'-cp',folder,'io.github.purryc.asr.'+name])
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(Boundaries))
    sys.exit(0 if result.wasSuccessful() else 1)
if __name__=='__main__':main()

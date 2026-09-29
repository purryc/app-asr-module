#!/usr/bin/env python3
"""Install isolated demo apps and run synthetic device fixtures (no microphone recording)."""
import argparse,subprocess
from pathlib import Path
from build import ROOT,BUILD

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--adb',default='adb');p.add_argument('--serial');p.add_argument('--variant',choices=['asr','pen','both'],default='both');args=p.parse_args()
    adb=[args.adb]+(['-s',args.serial] if args.serial else ['-d'])
    variants=['asr','pen'] if args.variant=='both' else [args.variant]
    qa=ROOT/'qa';qa.mkdir(exist_ok=True)
    for variant in variants:
        package='io.github.purryc.demo.'+variant
        fixture='io.github.purryc.asr.android.SpeechFixtureTest' if variant=='asr' else 'io.github.purryc.pen.PenFixtureTest'
        subprocess.run(adb+['install','--no-incremental','-r',str(BUILD/'artifacts'/('app-'+variant+'-debug.apk'))],check=True)
        subprocess.run(adb+['shell','am','start','-W','-n',package+'/.MainActivity'],check=True)
        result=subprocess.run(adb+['shell','am','instrument','-w',package+'/'+fixture],capture_output=True,text=True,check=True)
        (qa/(variant+'-fixture-result.txt')).write_text(result.stdout+result.stderr)
        print(result.stdout)
        if 'INSTRUMENTATION_CODE: -1' not in result.stdout or 'failure=' in result.stdout:raise SystemExit('Device fixture failed: '+variant)
    # Return to the standalone ASR host; recognition starts only when user presses its button.
    package='io.github.purryc.demo.'+('asr' if 'asr' in variants else 'pen')
    subprocess.run(adb+['shell','am','start','-W','-n',package+'/.MainActivity'],check=True)
if __name__=='__main__':main()

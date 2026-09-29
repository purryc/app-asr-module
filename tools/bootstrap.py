#!/usr/bin/env python3
"""Fetch pinned public upstream dependencies; no credentials and no runtime downloads."""
import argparse, hashlib, json, shutil, subprocess, tarfile, urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
    return h.hexdigest()
def verified(path,expected): return path.is_file() and digest(path)==expected
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache',type=Path,default=ROOT/'.cache/downloads')
    parser.add_argument('--verify-only',action='store_true')
    args=parser.parse_args();args.cache.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((ROOT/'models/manifest.json').read_text())
    for artifact in manifest['artifacts']:
        if all(verified(ROOT/f['target'],f['sha256']) for f in artifact['files']):
            print(artifact['id']+': verified');continue
        if args.verify_only: raise SystemExit('Missing/mismatched dependency: '+artifact['id'])
        archive=args.cache/artifact['download']
        if not verified(archive,artifact['sha256']):
            part=archive.with_suffix(archive.suffix+'.part');print('Downloading '+artifact['id'],flush=True)
            if shutil.which('curl'): subprocess.run(['curl','--fail','--location','--retry','3','--output',str(part),artifact['url']],check=True)
            else:
                request=urllib.request.Request(artifact['url'],headers={'User-Agent':'app-asr-module-bootstrap'})
                with urllib.request.urlopen(request) as response,part.open('wb') as output: shutil.copyfileobj(response,output)
            if not verified(part,artifact['sha256']): raise SystemExit('Archive hash mismatch: '+artifact['id'])
            part.replace(archive)
        # Extract only exact manifest members, never archive paths/links supplied by a tar file.
        if artifact['kind']=='tar':
            wanted={f['source']:f for f in artifact['files']};found=set()
            with tarfile.open(archive,mode='r|*') as tar:
                for member in tar:
                    if member.name not in wanted: continue
                    if not member.isfile(): raise SystemExit('Expected regular file: '+member.name)
                    f=wanted[member.name];target=ROOT/f['target'];target.parent.mkdir(parents=True,exist_ok=True)
                    with tar.extractfile(member) as source,target.open('wb') as output: shutil.copyfileobj(source,output)
                    found.add(member.name)
            if found!=set(wanted): raise SystemExit('Archive is missing expected files')
        else:
            for f in artifact['files']:
                target=ROOT/f['target'];target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(archive,target)
        for f in artifact['files']:
            if not verified(ROOT/f['target'],f['sha256']): raise SystemExit('Extracted hash mismatch: '+f['target'])
        print(artifact['id']+': ready',flush=True)
if __name__=='__main__':main()

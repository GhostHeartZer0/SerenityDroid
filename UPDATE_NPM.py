import json
import subprocess
import os
import sys

def update_npm(directory):
    pkg_path = os.path.join(directory, 'package.json')
    if not os.path.exists(pkg_path):
        print(f"No package.json found in {directory}")
        return
    
    print(f"Updating npm packages in {directory}...")
    
    with open(pkg_path, 'r') as f:
        pkg = json.load(f)
    
    dependencies = pkg.get('dependencies', {})
    devDependencies = pkg.get('devDependencies', {})
    
    if dependencies:
        dep_list = [f"{name}@latest" for name in dependencies.keys()]
        print(f"  Installing latest dependencies: {', '.join(dependencies.keys())}")
        subprocess.run(['npm', 'install'] + dep_list + ['--save', '--legacy-peer-deps'], cwd=directory, shell=True)
        
    if devDependencies:
        dev_dep_list = [f"{name}@latest" for name in devDependencies.keys()]
        print(f"  Installing latest devDependencies: {', '.join(devDependencies.keys())}")
        subprocess.run(['npm', 'install'] + dev_dep_list + ['--save-dev', '--legacy-peer-deps'], cwd=directory, shell=True)

if __name__ == "__main__":
    if len(sys.argv) > 1:
        for arg in sys.argv[1:]:
            update_npm(arg)
    else:
        # Default paths
        paths = [
            'app/src/main/cpp/llama.cpp/tools/server/webui',
            'app/src/main/cpp/llama.cpp/tools/ui'
        ]
        for p in paths:
            update_npm(p)

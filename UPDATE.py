import subprocess
import sys
import os
import re
import json
from urllib.request import urlopen

def get_latest_version(package_name):
    try:
        url = f"https://pypi.org/pypi/{package_name}/json"
        with urlopen(url, timeout=5) as response:
            data = json.load(response)
            return data['info']['version']
    except Exception:
        return None

def update_requirements(file_path):
    if not os.path.exists(file_path):
        print(f"File not found: {file_path}")
        return

    print(f"Updating {file_path}...")
    with open(file_path, 'r') as f:
        lines = f.readlines()

    new_lines = []
    for line in lines:
        line = line.strip()
        if not line or line.startswith('#'):
            new_lines.append(line + '\n')
            continue

        # Match package name and version (e.g., package==1.2.3 or package>=1.2.3)
        match = re.match(r'^([a-zA-Z0-9_\-\[\]]+)([<>=!~]+[a-zA-Z0-9_\-\.\*]+)?(.*)$', line)
        if match:
            package_name = match.group(1)
            # Fetch latest version
            latest = get_latest_version(package_name)
            if latest:
                new_lines.append(f"{package_name}=={latest}\n")
                print(f"  {package_name}: updated to {latest}")
            else:
                new_lines.append(line + '\n')
                print(f"  {package_name}: could not find latest version, skipping")
        else:
            new_lines.append(line + '\n')

    with open(file_path, 'w') as f:
        f.writelines(new_lines)

if __name__ == "__main__":
    if len(sys.argv) > 1:
        for arg in sys.argv[1:]:
            update_requirements(arg)
    else:
        # Default to root requirements.txt if it exists
        update_requirements("requirements.txt")

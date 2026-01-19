import json

with open("network_map_CoCJ.json") as f:
    d = json.load(f)

dev5 = d.get("10.81.128.5", {})
indices = [536, 654]
for ni in dev5.get("interfaces", []):
    if ni.get("index") in indices:
        print(f"Index {ni.get('index')}: Name={ni.get('name')} Type={ni.get('type')}")

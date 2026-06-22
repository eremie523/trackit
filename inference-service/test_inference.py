import csv
import json
import urllib.request

ecg_values = []
ppg_values = []

with open('demo_data/normal_steady.csv', 'r') as f:
    reader = csv.DictReader(f)
    for row in reader:
        channel = row['channel']
        val = float(row['value'])
        if channel == 'ecg' and len(ecg_values) < 7680:
            ecg_values.append(val)
        elif channel == 'ppg' and len(ppg_values) < 1920:
            ppg_values.append(val)
            
        if len(ecg_values) == 7680 and len(ppg_values) == 1920:
            break

print(f"Loaded {len(ecg_values)} ECG and {len(ppg_values)} PPG values from demo_data/normal_steady.csv")

url = "http://localhost:8001/infer"
payload = {
    "ecg": ecg_values,
    "ppg": ppg_values
}

data = json.dumps(payload).encode('utf-8')
headers = {'Content-Type': 'application/json'}
req = urllib.request.Request(url, data=data, headers=headers)

try:
    with urllib.request.urlopen(req) as response:
        result = json.loads(response.read().decode('utf-8'))
        print("\nSuccess! Received response:")
        print(json.dumps(result, indent=2))
except urllib.error.HTTPError as e:
    print(f"\nHTTP Error {e.code}: {e.read().decode('utf-8')}")
except Exception as e:
    print(f"\nError: {e}")

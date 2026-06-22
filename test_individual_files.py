import csv
import json
import urllib.request
import os

def test_individual():
    demo_dir = os.path.join('inference-service', 'demo_data')
    files = ['normal_steady.csv', 'tachycardia_stress.csv', 'afib_episode.csv', 'bradycardia_critical.csv']
    
    for filename in files:
        filepath = os.path.join(demo_dir, filename)
        ecg_values = []
        ppg_values = []
        
        with open(filepath, 'r') as f:
            reader = csv.reader(f)
            header = next(reader)
            for row in reader:
                if not row:
                    continue
                channel = row[2].strip().lower()
                val = float(row[3])
                if channel == 'ecg':
                    ecg_values.append(val)
                elif channel == 'ppg':
                    ppg_values.append(val)
                    
        # Trim to 30s
        ecg_win = ecg_values[:7680]
        ppg_win = ppg_values[:1920]
        
        url = "http://localhost:8001/infer"
        payload = {
            "ecg": ecg_win,
            "ppg": ppg_win
        }
        
        data = json.dumps(payload).encode('utf-8')
        headers = {'Content-Type': 'application/json'}
        req = urllib.request.Request(url, data=data, headers=headers)
        
        try:
            with urllib.request.urlopen(req) as response:
                result = json.loads(response.read().decode('utf-8'))
                print(f"{filename}: Severity={result.get('severity')}, Rhythm={result.get('rhythmLabel')}, HR={result.get('heartRate'):.1f}, SpO2={result.get('spo2'):.1f}%")
        except Exception as e:
            print(f"{filename} error: {e}")

if __name__ == '__main__':
    test_individual()

import csv
import json
import urllib.request

def test_accuracy():
    # Read the alternating_conditions.csv
    ecg_values = []
    ppg_values = []
    
    with open('alternating_conditions.csv', 'r') as f:
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
                
    print(f"Loaded total ECG: {len(ecg_values)}, PPG: {len(ppg_values)}")
    
    # Let's run inference on 6 non-overlapping windows of 30 seconds
    # Window size: 7680 ECG, 1920 PPG
    # Offset of 30 seconds = 7680 ECG, 1920 PPG
    for seg in range(6):
        start_ecg = seg * 7680
        end_ecg = start_ecg + 7680
        start_ppg = seg * 1920
        end_ppg = start_ppg + 1920
        
        if end_ecg > len(ecg_values) or end_ppg > len(ppg_values):
            break
            
        ecg_win = ecg_values[start_ecg:end_ecg]
        ppg_win = ppg_values[start_ppg:end_ppg]
        
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
                print(f"Segment {seg+1} ({seg*30}-{(seg+1)*30}s): Severity={result.get('severity')}, Rhythm={result.get('rhythmLabel')}, HR={result.get('heartRate'):.1f}, SpO2={result.get('spo2'):.1f}%")
        except Exception as e:
            print(f"Segment {seg+1} error: {e}")

if __name__ == '__main__':
    test_accuracy()

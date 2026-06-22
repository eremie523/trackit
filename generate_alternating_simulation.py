import csv
import os

def generate_simulation():
    demo_dir = os.path.join('inference-service', 'demo_data')
    
    # Define segments to extract: (filename, start_sec, duration_sec)
    segments = [
        ('normal_steady.csv', 0, 30),
        ('tachycardia_stress.csv', 30, 45),
        ('normal_steady.csv', 0, 30),
        ('afib_episode.csv', 30, 60),
        ('bradycardia_critical.csv', 30, 45),
        ('normal_steady.csv', 0, 30)
    ]
    
    output_rows = []
    ecg_index = 0
    ppg_index = 0
    current_time_sec = 0.0
    
    for filename, start_sec, duration_sec in segments:
        filepath = os.path.join(demo_dir, filename)
        print(f"Reading {filepath} (from {start_sec}s for {duration_sec}s)...")
        
        # Load all samples
        ecg_samples = []
        ppg_samples = []
        
        with open(filepath, 'r') as f:
            reader = csv.reader(f)
            header = next(reader)
            for row in reader:
                if not row:
                    continue
                channel = row[2].strip().lower()
                val = float(row[3])
                
                if channel == 'ecg':
                    ecg_samples.append(val)
                elif channel == 'ppg':
                    ppg_samples.append(val)
                    
        # Slice to the requested section
        # ECG fs = 256, PPG fs = 64
        ecg_start_idx = start_sec * 256
        ecg_end_idx = ecg_start_idx + (duration_sec * 256)
        ppg_start_idx = start_sec * 64
        ppg_end_idx = ppg_start_idx + (duration_sec * 64)
        
        ecg_slice = ecg_samples[ecg_start_idx:ecg_end_idx]
        ppg_slice = ppg_samples[ppg_start_idx:ppg_end_idx]
        
        # Deliver interleaved samples sequentially
        cycles = min(len(ecg_slice) // 4, len(ppg_slice))
        
        for c in range(cycles):
            for offset in range(4):
                val = ecg_slice[c * 4 + offset]
                t = current_time_sec + (ecg_index * (1.0 / 256.0))
                output_rows.append([ecg_index, f"{t:.6f}", 'ecg', f"{val:.6f}"])
                ecg_index += 1
                
            val = ppg_slice[c]
            t = current_time_sec + (ppg_index * (1.0 / 64.0))
            output_rows.append([ppg_index, f"{t:.6f}", 'ppg', f"{val:.6f}"])
            ppg_index += 1
            
        segment_time = cycles * (1.0 / 64.0)
        current_time_sec += segment_time
        
    print(f"Writing {len(output_rows)} samples to alternating_conditions.csv...")
    with open('alternating_conditions.csv', 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['sample_index', 'time_sec', 'channel', 'value'])
        writer.writerows(output_rows)
    print("Done!")

if __name__ == '__main__':
    generate_simulation()

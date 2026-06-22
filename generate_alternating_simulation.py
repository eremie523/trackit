import csv

def generate_simulation():
    files = [
        'normal_steady.csv',
        'tachycardia_stress.csv',
        'normal_steady.csv',
        'afib_episode.csv',
        'bradycardia_critical.csv',
        'normal_steady.csv'
    ]
    
    output_rows = []
    
    ecg_index = 0
    ppg_index = 0
    
    current_time_sec = 0.0
    
    for f_idx, filename in enumerate(files):
        print(f"Reading {filename}...")
        
        # Load samples
        ecg_samples = []
        ppg_samples = []
        
        with open(filename, 'r') as f:
            reader = csv.reader(f)
            header = next(reader)
            for row in reader:
                if not row:
                    continue
                # sample_index,time_sec,channel,value
                s_idx = int(row[0])
                t_sec = float(row[1])
                channel = row[2].strip().lower()
                val = float(row[3])
                
                if channel == 'ecg':
                    ecg_samples.append(val)
                elif channel == 'ppg':
                    ppg_samples.append(val)
                    
        # Deliver interleaved samples sequentially
        # ECG is 256Hz, PPG is 64Hz.
        # This means 4 ECG samples for every 1 PPG sample.
        # Let's count how many complete cycles we have in this file segment.
        cycles = min(len(ecg_samples) // 4, len(ppg_samples))
        
        for c in range(cycles):
            # Write 4 ECG samples
            for offset in range(4):
                val = ecg_samples[c * 4 + offset]
                t = current_time_sec + (ecg_index * (1.0 / 256.0))
                output_rows.append([ecg_index, f"{t:.6f}", 'ecg', f"{val:.6f}"])
                ecg_index += 1
                
            # Write 1 PPG sample
            val = ppg_samples[c]
            t = current_time_sec + (ppg_index * (1.0 / 64.0))
            output_rows.append([ppg_index, f"{t:.6f}", 'ppg', f"{val:.6f}"])
            ppg_index += 1
            
        # Update current_time_sec to the max of the elapsed time of this segment
        segment_time = cycles * (1.0 / 64.0)
        current_time_sec += segment_time
        # Reset the sample index offsets or let them continue sequentially?
        # The frontend uses sampleIndex to map. The original files have independent sampleIndex per channel.
        # Wait, the original files had:
        # sampleIndex 0, 1, 2, 3... for ECG and 0, 1, 2... for PPG.
        # Let's keep ecg_index and ppg_index counting up sequentially across all segments!
        
    print(f"Writing {len(output_rows)} samples to alternating_conditions.csv...")
    with open('alternating_conditions.csv', 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['sample_index', 'time_sec', 'channel', 'value'])
        writer.writerows(output_rows)
    print("Done!")

if __name__ == '__main__':
    generate_simulation()

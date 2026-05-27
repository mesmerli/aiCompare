import os
import sys
import json

def process_records(file_path):
    print("Reading data from:", file_path)
    if not os.path.exists(file_path):
        print("Error: File not found")
        return []

    with open(file_path, 'r') as f:
        data = json.load(f)

    results = []
    for item in data:
        # Calculate clean score
        raw_score = item.get('score', 0)
        multiplier = 1.2
        final_score = raw_score * multiplier
        
        results.append({
            'id': item.get('id'),
            'score': final_score,
            'status': 'passed' if final_score >= 60 else 'failed'
        })
    return results

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python data_processor.py <filename>")
        sys.exit(1)
    
    file_arg = sys.argv[1]
    res = process_records(file_arg)
    print("Processed", len(res), "records.")

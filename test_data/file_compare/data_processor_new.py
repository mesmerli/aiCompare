import os
import sys
import json
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def process_records(file_path, multiplier=1.5):
    logger.info(f"Loading JSON data from: {file_path}")
    if not os.path.exists(file_path):
        logger.error("Specified file path does not exist")
        return []

    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except json.JSONDecodeError as err:
        logger.error(f"Failed to parse JSON: {err}")
        return []

    results = []
    for item in data:
        raw_score = item.get('score', 0)
        final_score = raw_score * multiplier
        
        # New classification flags
        grade = 'A' if final_score >= 90 else ('B' if final_score >= 70 else 'C')
        
        results.append({
            'uid': item.get('uuid'),
            'adjusted_score': final_score,
            'grade': grade,
            'is_qualified': final_score >= 70
        })
    return results

if __name__ == '__main__':
    if len(sys.argv) < 2:
        logger.warning("No input file provided. Exiting.")
        sys.exit(1)
    
    file_arg = sys.argv[1]
    res = process_records(file_arg)
    logger.info(f"Successfully processed {len(res)} items.")

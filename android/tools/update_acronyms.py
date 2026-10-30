import json
import os
import sys

# Paths
output_path = 'app/src/main/assets/TeamAcronyms.json'
data_files = [
    'international_teams.json',
    'league_teams.json',
    'domestic_teams.json',
    'women_teams.json'
]

def main():
    acronym_map = {}

    for file_path in data_files:
        if not os.path.exists(file_path):
            print(f"Warning: {file_path} not found, skipping.")
            continue
            
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                # The structure is { "list": [ ... ] }
                if 'list' in data:
                    for item in data['list']:
                        if 'teamName' in item and 'teamSName' in item:
                            acronym_map[item['teamName']] = item['teamSName']
        except json.JSONDecodeError as e:
            print(f"Warning: Failed to parse {file_path}: {e}, skipping.")
            continue
        except Exception as e:
            print(f"Warning: Error reading {file_path}: {e}, skipping.")
            continue

    print(f"Loaded {len(acronym_map)} acronyms from API data.")

    # Ensure directory exists
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(acronym_map, f, indent=2, sort_keys=True)
    
    print(f"Saved acronyms to {output_path}")

if __name__ == "__main__":
    main()

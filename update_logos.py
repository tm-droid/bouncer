import json
import re
import os
import sys

# Paths
team_logos_path = 'app/src/main/assets/TeamLogos.json'
data_files = [
    'international_teams.json',
    'league_teams.json',
    'domestic_teams.json',
    'women_teams.json'
]

# Manual mapping for team names that differ between TeamLogos.json and API data
NAME_MAPPING = {
    "Hong Kong": "Hong Kong, China",
    "Hong Kong U19": "Hong Kong, China U19",
    "Hong Kong A": "Hong Kong, China A",
    "Hong Kong Women": "Hong Kong, China Women",
    "KwaZulu-Natal Inland": "KwaZulu-Natal Inland - Tuskers",
    "North West": "North West - Eastvaal Renault Dragons",
    "Namibia U19": "Namibia  U19",  # Note: Double space in API data
}

def main():
    # Load TeamLogos.json
    try:
        with open(team_logos_path, 'r', encoding='utf-8') as f:
            team_logos = json.load(f)
    except FileNotFoundError:
        print(f"Error: {team_logos_path} not found.")
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"Error: Failed to parse {team_logos_path}: {e}")
        sys.exit(1)

    # Build mapping from downloaded data
    team_image_map = {}

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
                        if 'teamName' in item and 'imageId' in item:
                            team_image_map[item['teamName']] = item['imageId']
        except json.JSONDecodeError as e:
            print(f"Warning: Failed to parse {file_path}: {e}, skipping.")
            continue
        except Exception as e:
            print(f"Warning: Error reading {file_path}: {e}, skipping.")
            continue

    print(f"Loaded {len(team_image_map)} teams from API data.")

    if len(team_image_map) == 0:
        print("No team data loaded. Exiting without changes.")
        sys.exit(0)

    # Update TeamLogos.json
    updated_count = 0
    not_found_count = 0

    # Regex to find the ID in the URL
    # Matches .../i1/{id}/{filename}.jpg
    url_pattern = re.compile(r'(.*\/)([a-zA-Z0-9]+)(\/[^\/]+\.jpg)$')

    for team_name, url in team_logos.items():
        # Check direct match or mapped match
        api_team_name = team_name
        if team_name in NAME_MAPPING:
            api_team_name = NAME_MAPPING[team_name]
            
        if api_team_name in team_image_map:
            new_id = str(team_image_map[api_team_name])
            
            match = url_pattern.match(url)
            if match:
                prefix = match.group(1)
                old_id = match.group(2)
                suffix = match.group(3)
                
                # Only update if the ID is different
                new_url = f"{prefix}c{new_id}{suffix}"
                if new_url != url:
                    team_logos[team_name] = new_url
                    updated_count += 1
            else:
                print(f"Warning: Could not parse URL for {team_name}: {url}")
        else:
            print(f"Team not found in API data: {team_name}")
            not_found_count += 1

    print(f"Updated {updated_count} URLs.")
    print(f"Teams not found in API data: {not_found_count}")

    # Add new teams found in API data but missing from TeamLogos.json
    added_count = 0
    for team_name, image_id in team_image_map.items():
        if team_name not in team_logos:
            # Construct URL
            # Slugify: lowercase, replace non-alphanumeric with hyphens
            slug = re.sub(r'[^a-z0-9]+', '-', team_name.lower()).strip('-')
            new_url = f"https://static.cricbuzz.com/a/img/v1/720x540/i1/c{image_id}/{slug}.jpg"
            
            team_logos[team_name] = new_url
            added_count += 1
            
    print(f"Added {added_count} new teams.")

    # Save the updated file
    try:
        os.makedirs(os.path.dirname(team_logos_path), exist_ok=True)
        with open(team_logos_path, 'w', encoding='utf-8') as f:
            json.dump(team_logos, f, indent=2, ensure_ascii=False)
        print(f"Successfully saved changes to {team_logos_path}")

    except Exception as e:
        print(f"Error: Failed to save files: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()

import random
import requests
import json
import re
from bs4 import BeautifulSoup as bs
from markupsafe import escape
from flask import Flask, jsonify, request
from flask_cors import CORS
from datetime import datetime
import pytz

# Replace the CORS URL with your's
app = Flask(__name__)
app.json.sort_keys = False
cors = CORS(app, resources={
            r"/score/*": {"origins": [r'^https://.+sanweb.info$', r'^https://.+mskian.com$']}})

USER_AGENT_LIST = [
    'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36',
    'Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/111.0',
    'Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36',
]

def get_headers():
    """Generate headers with random user agent for each request"""
    return {
        'User-Agent': random.choice(USER_AGENT_LIST),
        'Cache-Control': 'no-cache'
    }


@app.route('/')
def hello():
    return jsonify({'Code': 200, 'message': 'Python - Free Cricket Score API - JSON'})


@app.route('/score', methods=['GET'])
def score():
    get_id = request.args.get('id')
    id = escape(get_id)
    if not id:
        return jsonify({'error': 'Match ID is required'}), 400
    
    try:
        data = get_match_data(id)
        if data:
            result = parse_match_data(data)
            return jsonify(result)
        else:
            return jsonify({'error': 'Match data not found'}), 404
    except Exception as e:
        print(f"Unexpected error: {e}")
        return jsonify({'error': 'Internal server error'}), 500

def extract_json_object(text, start_index):
    """Extract a JSON object starting at start_index handling nested braces"""
    brace_count = 0
    in_string = False
    escape = False
    
    for i in range(start_index, len(text)):
        char = text[i]
        
        if escape:
            escape = False
            continue
            
        if char == '\\':
            escape = True
            continue
            
        if char == '"':
            in_string = not in_string
            continue
            
        if not in_string:
            if char == '{':
                brace_count += 1
            elif char == '}':
                brace_count -= 1
                if brace_count == 0:
                    return text[start_index:i+1]
    return None

def get_match_data(match_id):
    try:
        url = f"https://www.cricbuzz.com/live-cricket-scores/{match_id}"
        r = requests.get(url, headers=get_headers(), timeout=10)
        if r.status_code != 200:
            return None
            
        parts = r.text.split('self.__next_f.push(')
        for part in parts:
            if 'miniscore' in part or 'matchHeader' in part:
                end_idx = part.rfind('])')
                if end_idx != -1:
                    json_str = part[:end_idx+1]
                    try:
                        data = json.loads(json_str)
                        if len(data) > 1 and isinstance(data[1], str):
                            inner_data = data[1]
                            
                            # Use regex to extract specific objects as they are more reliable
                            # than parsing the whole potentially malformed/streaming JSON
                            result_data = {}
                            found_any = False
                            
                            # Extract miniscore
                            miniscore_idx = inner_data.find('"miniscore":')
                            if miniscore_idx != -1:
                                start = inner_data.find('{', miniscore_idx)
                                if start != -1:
                                    json_text = extract_json_object(inner_data, start)
                                    if json_text:
                                        try:
                                            result_data['miniscore'] = json.loads(json_text)
                                            found_any = True
                                        except:
                                            pass

                            # Extract matchHeader
                            match_header_idx = inner_data.find('"matchHeader":')
                            if match_header_idx != -1:
                                start = inner_data.find('{', match_header_idx)
                                if start != -1:
                                    json_text = extract_json_object(inner_data, start)
                                    if json_text:
                                        try:
                                            result_data['matchHeader'] = json.loads(json_text)
                                            found_any = True
                                        except:
                                            pass
                            
                            if found_any:
                                return result_data
                                
                    except:
                        pass
        return None
    except Exception as e:
        print(f"Error fetching match data: {e}")
        return None


def parse_match_data(data):
    """Parse match data from JSON object"""
    if not data:
        return get_default_response(None)
    
    try:
        match_header = data.get('matchHeader', {})
        miniscore = data.get('miniscore', {})
        
        # Title
        team1 = match_header.get('team1', {}).get('name', '')
        team2 = match_header.get('team2', {}).get('name', '')
        desc = match_header.get('matchDescription', '')
        if team1 and team2:
            title = f"{team1} vs {team2}, {desc}"
        else:
            title = desc
        
        # Status
        status = miniscore.get('status', match_header.get('status', 'Match Stats will Update Soon'))
        
        # Live Score
        bat_team = miniscore.get('batTeam', {})
        if bat_team:
            score = bat_team.get('teamScore', '0')
            wickets = bat_team.get('teamWkts', '0')
            overs = miniscore.get('overs', '0')
            live_score = f"{score}/{wickets} ({overs})"
        else:
            live_score = "Data Not Found"
        
        # Run Rate
        crr = miniscore.get('currentRunRate', '0')
        run_rate = f"CRR: {crr}"
        
        # Batters
        striker = miniscore.get('batsmanStriker', {})
        non_striker = miniscore.get('batsmanNonStriker', {})
        
        batter_one = striker.get('batName', striker.get('name', 'Data Not Found'))
        batter_one_run = str(striker.get('runs', '0'))
        batter_one_ball = str(striker.get('balls', '0'))
        batter_one_sr = str(striker.get('strikeRate', '0'))
        
        batter_two = non_striker.get('batName', non_striker.get('name', 'Data Not Found'))
        batter_two_run = str(non_striker.get('runs', '0'))
        batter_two_ball = str(non_striker.get('balls', '0'))
        batter_two_sr = str(non_striker.get('strikeRate', '0'))
        
        # Bowlers
        bowler_striker = miniscore.get('bowlerStriker', {})
        bowler_non_striker = miniscore.get('bowlerNonStriker', {})
        
        bowler_one = bowler_striker.get('bowlName', bowler_striker.get('name', 'Data Not Found'))
        bowler_one_over = str(bowler_striker.get('overs', '0'))
        bowler_one_run = str(bowler_striker.get('runs', '0'))
        bowler_one_eco = str(bowler_striker.get('economy', '0'))
        bowler_one_wicket = str(bowler_striker.get('wickets', '0'))
        
        bowler_two = bowler_non_striker.get('bowlName', bowler_non_striker.get('name', 'Data Not Found'))
        bowler_two_over = str(bowler_non_striker.get('overs', '0'))
        bowler_two_run = str(bowler_non_striker.get('runs', '0'))
        bowler_two_eco = str(bowler_non_striker.get('economy', '0'))
        bowler_two_wicket = str(bowler_non_striker.get('wickets', '0'))
        
        return {
            'title': title,
            'update': status,
            'livescore': live_score,
            'runrate': run_rate,
            'batterone': batter_one,
            'batsmanonerun': batter_one_run,
            'batsmanoneball': '(' + batter_one_ball + ')',
            'batsmanonesr': batter_one_sr,
            'battertwo': batter_two,
            'batsmantworun': batter_two_run,
            'batsmantwoball': '(' + batter_two_ball + ')',
            'batsmantwosr': batter_two_sr,
            'bowlerone': bowler_one,
            "bowleroneover": bowler_one_over,
            "bowleronerun": bowler_one_run,
            "bowleronewickers": bowler_one_wicket,
            "bowleroneeconomy": bowler_one_eco,
            'bowlertwo': bowler_two,
            "bowlertwoover": bowler_two_over,
            "bowlertworun": bowler_two_run,
            "bowlertwowickers": bowler_two_wicket,
            "bowlertwoeconomy": bowler_two_eco
        }
    except Exception as e:
        print(f"Parsing error: {e}")
        return get_default_response(None)


def get_default_response(data):
    """Get minimal response when full data not available"""
    return {
        'title': 'Data Not Found',
        'update': 'Match Stats will Update Soon',
        'livescore': 'Data Not Found',
        'runrate': 'Match Stats will Update Soon',
        'batterone': 'Match Stats will Update Soon',
        'batsmanonerun': 'Match Stats will Update Soon',
        'batsmanoneball': 'Match Stats will Update Soon',
        'batsmanonesr': 'Match Stats will Update Soon',
        'battertwo': 'Match Stats will Update Soon',
        'batsmantworun': 'Match Stats will Update Soon',
        'batsmantwoball': 'Match Stats will Update Soon',
        'batsmantwosr': 'Match Stats will Update Soon',
        'bowlerone': 'Match Stats will Update Soon',
        "bowleroneover": 'Match Stats will Update Soon',
        "bowleronerun": 'Match Stats will Update Soon',
        "bowleronewickers": 'Match Stats will Update Soon',
        "bowleroneeconomy": 'Match Stats will Update Soon',
        'bowlertwo': 'Match Stats will Update Soon',
        "bowlertwoover": 'Match Stats will Update Soon',
        "bowlertworun": 'Match Stats will Update Soon',
        "bowlertwowickers": 'Match Stats will Update Soon',
        "bowlertwoeconomy": 'Match Stats will Update Soon'
    }


@app.route('/score/live', methods=['GET'])
def live():
    get_id = request.args.get('id')
    id = escape(get_id)
    if not id:
        return jsonify({
            "success": 'false',
            "livescore": {
                'title': 'Data not Found',
                'update': 'Data not Found',
                'current': 'Data not Found',
                'runrate': 'Data not Found',
                'batsman': 'Data not Found',
                'batsmanrun': 'Data not Found',
                'ballsfaced': 'Data not Found',
                'sr': 'Data not Found',
                'batsmantwo': 'Data not Found',
                'batsmantworun': 'Data not Found',
                'batsmantwoballfaced': 'Data not Found',
                'batsmantwosr': 'Data not Found',
                'bowler': 'Data not Found',
                "bowlerover": 'Data not Found',
                "bowlerruns": 'Data not Found',
                "bowlerwickets": 'Data not Found',
                "bowlereconomy": 'Data not Found',
                'bowlertwo': 'Data not Found',
                "bowlertwoover": 'Data not Found',
                "bowlertworuns": 'Data not Found',
                "bowlertwowickets": 'Data not Found',
                "bowlertwoeconomy": 'Data not Found'
            }
        })
    
    try:
        session_object = requests.Session()
        r = session_object.get(
            'https://www.cricbuzz.com/live-cricket-scores/' + id,
            headers=get_headers(),
            timeout=10
        )
        soup = bs(r.content, 'lxml')
        try:
            update = soup.find_all(
                "div", attrs={"class": "cb-col cb-col-100 cb-min-stts cb-text-complete"})[0].text.strip() if soup.find_all("div", attrs={"class": "cb-col cb-col-100 cb-min-stts cb-text-complete"}) else 'Match Stats will Update Soon'
            process = soup.find_all(
                "div", attrs={"class": "cb-text-inprogress"})[0].text.strip() if soup.find_all("div", attrs={"class": "cb-text-inprogress"}) else 'Match Stats will Update Soon'
            noresult = soup.find_all(
                "div", attrs={"class": "cb-col cb-col-100 cb-font-18 cb-toss-sts cb-text-abandon"})[0].text.strip() if soup.find_all("div", attrs={"class": "cb-col cb-col-100 cb-font-18 cb-toss-sts cb-text-abandon"}) else 'Match Stats will Update Soon'
            stumps = soup.find_all(
                "div", attrs={"class": "cb-text-stumps"})[0].text.strip() if soup.find_all("div", attrs={"class": "cb-text-stumps"}) else 'Match Stats will Update Soon'
            lunch = soup.find_all(
                "div", attrs={"class": "cb-text-lunch"})[0].text.strip() if soup.find_all("div", attrs={"class": "cb-text-lunch"}) else 'Match Stats will Update Soon'
            inningsbreak = soup.find_all(
                "div", attrs={"class": "cb-text-inningsbreak"})[0].text.strip() if soup.find_all("div", attrs={"class": "cb-text-inningsbreak"}) else 'Match Stats will Update Soon'
            tea = soup.find_all("div", attrs={"class": "cb-text-tea"})[0].text.strip() if soup.find(
                "div", attrs={"class": "cb-text-tea"}) else 'Match Stats will Update Soon'
            rain_break = soup.find_all("div", attrs={"class": "cb-text-rain"})[0].text.strip(
            ) if soup.find("div", attrs={"class": "cb-text-rain"}) else 'Match Stats will Update Soon'
            wet_outfield = soup.find_all("div", attrs={"class": "cb-text-wetoutfield"})[0].text.strip(
            ) if soup.find("div", attrs={"class": "cb-text-wetoutfield"}) else 'Match Stats will Update Soon'
            match_date_element = soup.find('span', itemprop='startDate')
            if match_date_element:
                match_time = match_date_element.get('content')
                new_dt = match_time.split('+')[0]
                utc_time = datetime.strptime(new_dt, "%Y-%m-%dT%H:%M:%S")
                utc_time_utc = utc_time.replace(tzinfo=pytz.UTC)
                target_timezone = pytz.timezone("Asia/Kolkata")
                local_time = utc_time_utc.astimezone(target_timezone)
                formatted_local_time = local_time.strftime(
                    "Date: %Y-%m-%d - Time: %I:%M:%S %p (Indian Local Time)")
                match_date = formatted_local_time
            else:
                match_date = 'Match Stats will Update Soon'
            live_score = soup.find(
                "span", attrs={"class": "cb-font-20 text-bold"}).text.strip() if soup.find("span", attrs={"class": "cb-font-20 text-bold"}) else 'Data Not Found'
            title = soup.find(
                "h1", attrs={"class": "cb-nav-hdr cb-font-18 line-ht24"}).text.strip().replace(", Commentary", "") if soup.find("h1", attrs={"class": "cb-nav-hdr cb-font-18 line-ht24"}) else 'Data Not Found'
            run_rate_elements = soup.find_all("span", attrs={"class": "cb-font-12 cb-text-gray"})
            run_rate = run_rate_elements[0].text.strip().replace("CRR:\u00a0", "") if len(run_rate_elements) > 0 else 'Data Not Found'
            
            col_50_elements = soup.find_all("div", attrs={"class": "cb-col cb-col-50"})
            batter_one = col_50_elements[1].text.strip() if len(col_50_elements) > 1 else 'Data Not Found'
            batter_two = col_50_elements[2].text.strip() if len(col_50_elements) > 2 else 'Data Not Found'
            bowler_one = col_50_elements[4].text.strip() if len(col_50_elements) > 4 else 'Data Not Found'
            bowler_two = col_50_elements[5].text.strip() if len(col_50_elements) > 5 else 'Data Not Found'
            
            ab_text_right_elements = soup.find_all("div", attrs={"class": "cb-col cb-col-10 ab text-right"})
            batter_one_run = ab_text_right_elements[0].text.strip() if len(ab_text_right_elements) > 0 else 'Data Not Found'
            batter_one_ball = ab_text_right_elements[1].text.strip() if len(ab_text_right_elements) > 1 else 'Data Not Found'
            batter_two_run = ab_text_right_elements[2].text.strip() if len(ab_text_right_elements) > 2 else 'Data Not Found'
            batter_two_ball = ab_text_right_elements[3].text.strip() if len(ab_text_right_elements) > 3 else 'Data Not Found'
            
            col_14_ab_elements = soup.find_all("div", attrs={"class": "cb-col cb-col-14 ab text-right"})
            batter_one_sr = col_14_ab_elements[0].text.strip() if len(col_14_ab_elements) > 0 else 'Data Not Found'
            batter_two_sr = col_14_ab_elements[1].text.strip() if len(col_14_ab_elements) > 1 else 'Data Not Found'
            
            text_right_elements = soup.find_all("div", attrs={"class": "cb-col cb-col-10 text-right"})
            bowler_one_over = text_right_elements[4].text.strip() if len(text_right_elements) > 4 else 'Data Not Found'
            bowler_one_run = text_right_elements[5].text.strip() if len(text_right_elements) > 5 else 'Data Not Found'
            bowler_two_over = text_right_elements[6].text.strip() if len(text_right_elements) > 6 else 'Data Not Found'
            bowler_two_run = text_right_elements[7].text.strip() if len(text_right_elements) > 7 else 'Data Not Found'
            
            col_14_text_right_elements = soup.find_all("div", attrs={"class": "cb-col cb-col-14 text-right"})
            bowler_one_eco = col_14_text_right_elements[2].text.strip() if len(col_14_text_right_elements) > 2 else 'Data Not Found'
            bowler_two_eco = col_14_text_right_elements[3].text.strip() if len(col_14_text_right_elements) > 3 else 'Data Not Found'
            
            col_8_text_right_elements = soup.find_all("div", attrs={"class": "cb-col cb-col-8 text-right"})
            bowler_one_wicket = col_8_text_right_elements[5].text.strip() if len(col_8_text_right_elements) > 5 else 'Data Not Found'
            bowler_two_wicket = col_8_text_right_elements[7].text.strip() if len(col_8_text_right_elements) > 7 else 'Data Not Found'
        except IndexError:
            update = soup.find_all(
                "div", attrs={"class": "cb-col cb-col-100 cb-min-stts cb-text-complete"})[0].text.strip() if soup.find_all("div", attrs={"class": "cb-col cb-col-100 cb-min-stts cb-text-complete"}) else 'Match Stats will Update Soon'
            process = soup.find_all(
                "div", attrs={"class": "cb-text-inprogress"})[0].text.strip() if soup.find_all("div", attrs={"class": "cb-text-inprogress"}) else 'Match Stats will Update Soon'
            noresult = soup.find_all(
                "div", attrs={"class": "cb-col cb-col-100 cb-font-18 cb-toss-sts cb-text-abandon"})[0].text.strip() if soup.find_all("div", attrs={"class": "cb-col cb-col-100 cb-font-18 cb-toss-sts cb-text-abandon"}) else 'Match Stats will Update Soon'
            stumps = soup.find_all(
                "div", attrs={"class": "cb-text-stumps"})[0].text.strip() if soup.find_all("div", attrs={"class": "cb-text-stumps"}) else 'Match Stats will Update Soon'
            lunch = soup.find_all(
                "div", attrs={"class": "cb-text-lunch"})[0].text.strip() if soup.find_all("div", attrs={"class": "cb-text-lunch"}) else 'Match Stats will Update Soon'
            inningsbreak = soup.find_all(
                "div", attrs={"class": "cb-text-inningsbreak"})[0].text.strip() if soup.find_all("div", attrs={"class": "cb-text-inningsbreak"}) else 'Match Stats will Update Soon'
            tea = soup.find_all("div", attrs={"class": "cb-text-tea"})[0].text.strip() if soup.find(
                "div", attrs={"class": "cb-text-tea"}) else 'Match Stats will Update Soon'
            rain_break = soup.find_all("div", attrs={"class": "cb-text-rain"})[0].text.strip(
            ) if soup.find("div", attrs={"class": "cb-text-rain"}) else 'Match Stats will Update Soon'
            wet_outfield = soup.find_all("div", attrs={"class": "cb-text-wetoutfield"})[0].text.strip(
            ) if soup.find("div", attrs={"class": "cb-text-wetoutfield"}) else 'Match Stats will Update Soon'
            match_date_element = soup.find('span', itemprop='startDate')
            if match_date_element:
                match_time = match_date_element.get('content')
                new_dt = match_time.split('+')[0]
                utc_time = datetime.strptime(new_dt, "%Y-%m-%dT%H:%M:%S")
                utc_time_utc = utc_time.replace(tzinfo=pytz.UTC)
                target_timezone = pytz.timezone("Asia/Kolkata")
                local_time = utc_time_utc.astimezone(target_timezone)
                formatted_local_time = local_time.strftime(
                    "Date: %Y-%m-%d - Time: %I:%M:%S %p (Indian Local Time)")
                match_date = formatted_local_time
            else:
                match_date = 'Match Stats will Update Soon'
            live_score = soup.find(
                "span", attrs={"class": "cb-font-20 text-bold"}).text.strip() if soup.find("span", attrs={"class": "cb-font-20 text-bold"}) else 'Data Not Found'
            title = soup.find(
                "h1", attrs={"class": "cb-nav-hdr cb-font-18 line-ht24"}).text.strip().replace(", Commentary", "") if soup.find("h1", attrs={"class": "cb-nav-hdr cb-font-18 line-ht24"}) else 'Data Not Found'
            run_rate = 'Match Stats will Update Soon'
            batter_one = 'Match Stats will Update Soon'
            batter_two = 'Match Stats will Update Soon'
            batter_one_run = 'Match Stats will Update Soon'
            batter_two_run = 'Match Stats will Update Soon'
            batter_one_ball = 'Match Stats will Update Soon'
            batter_two_ball = 'Match Stats will Update Soon'
            batter_one_sr = 'Match Stats will Update Soon'
            batter_two_sr = 'Match Stats will Update Soon'
            bowler_one = 'Match Stats will Update Soon'
            bowler_two = 'Match Stats will Update Soon'
            bowler_one_over = 'Match Stats will Update Soon'
            bowler_two_over = 'Match Stats will Update Soon'
            bowler_one_run = 'Match Stats will Update Soon'
            bowler_two_run = 'Match Stats will Update Soon'
            bowler_one_eco = 'Match Stats will Update Soon'
            bowler_two_eco = 'Match Stats will Update Soon'
            bowler_one_wicket = 'Match Stats will Update Soon'
            bowler_two_wicket = 'Match Stats will Update Soon'
        if (update != 'Match Stats will Update Soon'):
            status = update
        elif (process != 'Match Stats will Update Soon'):
            status = process
        elif (noresult != 'Match Stats will Update Soon'):
            status = noresult
        elif (stumps != 'Match Stats will Update Soon'):
            status = stumps
        elif (lunch != 'Match Stats will Update Soon'):
            status = lunch
        elif (inningsbreak != 'Match Stats will Update Soon'):
            status = inningsbreak
        elif (tea != 'Match Stats will Update Soon'):
            status = tea
        elif (rain_break != 'Match Stats will Update Soon'):
            status = rain_break
        elif (wet_outfield != 'Match Stats will Update Soon'):
            status = wet_outfield
        elif (match_date != 'Match Stats will Update Soon'):
            status = match_date
        else:
            status = 'Match Stats will Update Soon...'
        return jsonify({
            "success": 'true',
            "livescore": {
                'title': title,
                'update': status,
                'current': live_score,
                'runrate': 'CRR: ' + run_rate,
                'batsman': batter_one,
                'batsmanrun': batter_one_run,
                'ballsfaced': '(' + batter_one_ball + ')',
                'sr': batter_one_sr,
                'batsmantwo': batter_two,
                'batsmantworun': batter_two_run,
                'batsmantwoballfaced':  '(' + batter_two_ball + ')',
                'batsmantwosr': batter_two_sr,
                'bowler': bowler_one,
                "bowlerover": bowler_one_over,
                "bowlerruns": bowler_one_run,
                "bowlerwickets": bowler_one_wicket,
                "bowlereconomy": bowler_one_eco,
                'bowlertwo': bowler_two,
                "bowlertwoover": bowler_two_over,
                "bowlertworuns": bowler_two_run,
                "bowlertwowickets": bowler_two_wicket,
                "bowlertwoeconomy": bowler_two_eco
            }

        })
    except requests.RequestException as e:
        print(f"Request error: {e}")
        return jsonify({'success': 'false', 'error': 'Failed to fetch match data'}), 503
    except Exception as e:
        print(f"Unexpected error: {e}")
        return jsonify({'success': 'false', 'error': 'Internal server error'}), 500


@app.route('/matches', methods=['GET'])
def matches():
    try:
        session_object = requests.Session()
        r = session_object.get(
            'https://www.cricbuzz.com/live-cricket-scores/', 
            headers=get_headers(),
            timeout=10
        )
        
        matches_list = []
        processed_ids = set()
        
        # Method 1: Try parsing Next.js hydration data (JSON)
        try:
            # Manual extraction to handle nested parentheses correctly
            scripts = []
            start_marker = 'self.__next_f.push('
            start_idx = 0
            text_len = len(r.text)
            
            while True:
                idx = r.text.find(start_marker, start_idx)
                if idx == -1:
                    break
                    
                # Found a push call, now find the matching closing parenthesis
                open_paren_idx = idx + len(start_marker) - 1
                paren_count = 0
                in_string = False
                escape = False
                end_idx = -1
                
                for i in range(open_paren_idx, text_len):
                    char = r.text[i]
                    
                    if escape:
                        escape = False
                        continue
                        
                    if char == '\\':
                        escape = True
                        continue
                        
                    if char == '"':
                        in_string = not in_string
                        continue
                        
                    if not in_string:
                        if char == '(':
                            paren_count += 1
                        elif char == ')':
                            paren_count -= 1
                            if paren_count == 0:
                                end_idx = i
                                break
                
                if end_idx != -1:
                    # Extract content inside push(...)
                    content = r.text[open_paren_idx+1:end_idx]
                    scripts.append(content)
                    start_idx = end_idx + 1
                else:
                    start_idx = idx + 1

            for script_content in scripts:
                try:
                    # The content is usually a JS array like [1, "string"]
                    data_list = json.loads(script_content)
                    if isinstance(data_list, list) and len(data_list) > 1 and isinstance(data_list[1], str):
                        inner_json_str = data_list[1]
                        
                        # Clean up stream markers if present
                        # Remove stream ID prefix (e.g., "1f:", "4:", "22:")
                        colon_index = inner_json_str.find(':')
                        if colon_index != -1 and colon_index < 10:
                            inner_json_str = inner_json_str[colon_index + 1:]
                            
                        if inner_json_str.startswith('I'):
                            inner_json_str = inner_json_str[1:]
                            
                        try:
                            data = json.loads(inner_json_str)
                            
                            # Helper to find matches recursively
                            def find_matches_in_obj(obj):
                                matches = []
                                if isinstance(obj, dict):
                                    # Check if this object IS a match object
                                    if 'matchInfo' in obj:
                                        matches.append(obj)
                                    elif 'match' in obj and isinstance(obj['match'], dict) and 'matchInfo' in obj['match']:
                                        matches.append(obj['match'])
                                    
                                    # Recurse into all values
                                    for k, v in obj.items():
                                        if isinstance(v, (dict, list)):
                                            matches.extend(find_matches_in_obj(v))
                                elif isinstance(obj, list):
                                    for item in obj:
                                        matches.extend(find_matches_in_obj(item))
                                return matches

                            found_matches = find_matches_in_obj(data)
                            
                            if found_matches:
                                for match_item in found_matches:
                                    # Handle 'match' wrapper
                                    match = match_item.get('match', match_item)

                                    # Extract info
                                    m_info = match.get('matchInfo', {})
                                    m_id = str(m_info.get('matchId', ''))
                                    if not m_id or m_id in processed_ids: continue
                                    
                                    processed_ids.add(m_id)
                                    
                                    team1 = m_info.get('team1', {}).get('teamName', '')
                                    team2 = m_info.get('team2', {}).get('teamName', '')
                                    title = f"{team1} vs {team2}"
                                    status = m_info.get('status', '')
                                    state = m_info.get('state', '')
                                    
                                    # Score extraction
                                    score_text = ""
                                    match_score = match.get('matchScore', {})
                                    if match_score:
                                        t1_score = match_score.get('team1Score', {}).get('inngs1', {})
                                        t2_score = match_score.get('team2Score', {}).get('inngs1', {})
                                        
                                        scores = []
                                        if t1_score:
                                            s = f"{team1} {t1_score.get('runs', 0)}"
                                            if 'wickets' in t1_score:
                                                s += f"/{t1_score['wickets']}"
                                            if 'overs' in t1_score:
                                                s += f" ({t1_score['overs']})"
                                            scores.append(s)
                                            
                                        if t2_score:
                                            s = f"{team2} {t2_score.get('runs', 0)}"
                                            if 'wickets' in t2_score:
                                                s += f"/{t2_score['wickets']}"
                                            if 'overs' in t2_score:
                                                s += f" ({t2_score['overs']})"
                                            scores.append(s)
                                            
                                        score_text = " | ".join(scores)
                                    
                                    matches_list.append({
                                        'id': m_id,
                                        'title': title,
                                        'status': status or state,
                                        'score': score_text
                                    })
                        except Exception:
                            continue
                except Exception:
                    continue
        except Exception as e:
            print(f"JSON parsing error: {e}")

        # Method 2: Fallback to HTML parsing (Legacy)
        if not matches_list:
            soup = bs(r.content, 'lxml')
            links = soup.find_all('a', href=True)
            for link in links:
                href = link['href']
                if '/live-cricket-scores/' in href:
                    parts = href.split('/')
                    if len(parts) > 2:
                        match_id = parts[2]
                        if match_id in processed_ids:
                            continue
                        
                        if not match_id.isdigit():
                            continue

                        processed_ids.add(match_id)
                        
                        title = link.get('title', '').strip()
                        text = link.text.strip()
                        
                        status = "Unknown"
                        if ' - ' in title:
                            status = title.split(' - ')[-1]
                        elif ' - ' in text:
                            status = text.split(' - ')[-1]
                        
                        if not title:
                            title = text
                            
                        matches_list.append({
                            'id': match_id,
                            'title': title,
                            'status': status,
                            'score': '' # No score in legacy list parsing usually
                        })
        
        return jsonify({
            'matches': matches_list,
            'count': len(matches_list)
        })
        
    except Exception as e:
        print(f"Error: {e}")
        return jsonify({'error': 'Failed to fetch matches'}), 500


@app.errorhandler(404)
def invalid_route(e):
    return jsonify({
        'title': 'Data not Found',
        'update': 'Data not Found',
        'livescore': 'Data not Found',
        'runrate': 'Data not Found',
        'batterone': 'Data not Found',
        'batsmanonerun': 'Data not Found',
        'batsmanoneball': 'Data not Found',
        'batsmanonesr': 'Data not Found',
        'battertwo': 'Data not Found',
        'batsmantworun': 'Data not Found',
        'batsmantwoball': 'Data not Found',
        'batsmantwosr': 'Data not Found',
        'bowlerone': 'Data not Found',
        "bowleroneover": 'Data not Found',
        "bowleronerun": 'Data not Found',
        "bowleronewickers": 'Data not Found',
        "bowleroneeconomy": 'Data not Found',
        'bowlertwo': 'Data not Found',
        "bowlertwoover": 'Data not Found',
        "bowlertworun": 'Data not Found',
        "bowlertwowickers": 'Data not Found',
        "bowlertwoeconomy": 'Data not Found',

    })


@app.errorhandler(500)
def server_error(e):
    return jsonify({
        'title': 'Data not Found',
        'update': 'Data not Found',
        'livescore': 'Data not Found',
        'runrate': 'Data not Found',
        'batterone': 'Data not Found',
        'batsmanonerun': 'Data not Found',
        'batsmanoneball': 'Data not Found',
        'batsmanonesr': 'Data not Found',
        'battertwo': 'Data not Found',
        'batsmantworun': 'Data not Found',
        'batsmantwoball': 'Data not Found',
        'batsmantwosr': 'Data not Found',
        'bowlerone': 'Data not Found',
        "bowleroneover": 'Data not Found',
        "bowleronerun": 'Data not Found',
        "bowleronewickers": 'Data not Found',
        "bowleroneeconomy": 'Data not Found',
        'bowlertwo': 'Data not Found',
        "bowlertwoover": 'Data not Found',
        "bowlertworun": 'Data not Found',
        "bowlertwowickers": 'Data not Found',
        "bowleroneeconomy": 'Data not Found',

    })


if __name__ == '__main__':
    app.run()
    # app.run(
    #    host="0.0.0.0",
    #    port=int("5000")
    # )

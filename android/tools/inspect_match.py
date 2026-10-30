from bs4 import BeautifulSoup as bs

def inspect_match():
    with open('match_page.html', 'r') as f:
        content = f.read()
    
    soup = bs(content, 'lxml')
    
    print("Checking Title:")
    title = soup.find("h1", attrs={"class": "cb-nav-hdr cb-font-18 line-ht24"})
    print(f"Found: {title.text.strip() if title else 'None'}")
    
    print("\nChecking Live Score:")
    live_score = soup.find("span", attrs={"class": "cb-font-20 text-bold"})
    print(f"Found: {live_score.text.strip() if live_score else 'None'}")
    
    print("\nChecking Run Rate:")
    run_rate = soup.find_all("span", attrs={"class": "cb-font-12 cb-text-gray"})
    print(f"Found {len(run_rate)} elements")
    if run_rate:
        print(f"First: {run_rate[0].text.strip()}")

    print("\nChecking Batters (cb-col cb-col-50):")
    col_50 = soup.find_all("div", attrs={"class": "cb-col cb-col-50"})
    print(f"Found {len(col_50)} elements")
    for i, el in enumerate(col_50[:10]):
        print(f"{i}: {el.text.strip()}")

    print("\nChecking Batter Stats (cb-col cb-col-10 ab text-right):")
    ab_text_right = soup.find_all("div", attrs={"class": "cb-col cb-col-10 ab text-right"})
    print(f"Found {len(ab_text_right)} elements")
    
    print("\nChecking Bowler Stats (cb-col cb-col-10 text-right):")
    text_right = soup.find_all("div", attrs={"class": "cb-col cb-col-10 text-right"})
    print(f"Found {len(text_right)} elements")

if __name__ == "__main__":
    inspect_match()

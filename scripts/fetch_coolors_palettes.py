#!/usr/bin/env python3
"""
Fetch trending color palettes from Coolors.co

This script scrapes trending palettes from Coolors.co and outputs them
in a format ready to use in Android themes.

Requirements:
    pip install requests beautifulsoup4 selenium webdriver-manager

Usage:
    python fetch_coolors_palettes.py [--count N] [--format FORMAT]

    --count N       Number of palettes to fetch (default: 10)
    --format FORMAT Output format: 'android', 'json', 'hex' (default: 'android')
"""

import argparse
import json
import re
import sys
import time
from typing import List, Dict, Optional

try:
    import requests
    from bs4 import BeautifulSoup
except ImportError:
    print("Required packages not installed. Run:")
    print("  pip install requests beautifulsoup4")
    sys.exit(1)


def fetch_with_selenium(count: int = 10) -> List[List[str]]:
    """
    Fetch palettes using Selenium (for JavaScript-rendered content).
    """
    try:
        from selenium import webdriver
        from selenium.webdriver.chrome.service import Service
        from selenium.webdriver.chrome.options import Options
        from selenium.webdriver.common.by import By
        from selenium.webdriver.support.ui import WebDriverWait
        from selenium.webdriver.support import expected_conditions as EC
        from webdriver_manager.chrome import ChromeDriverManager
    except ImportError:
        print("Selenium not installed. Run:")
        print("  pip install selenium webdriver-manager")
        return []

    print("Starting Chrome browser (headless)...")

    options = Options()
    options.add_argument("--headless")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-gpu")
    options.add_argument("--window-size=1920,1080")
    options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

    try:
        service = Service(ChromeDriverManager().install())
        driver = webdriver.Chrome(service=service, options=options)
    except Exception as e:
        print(f"Failed to start Chrome: {e}")
        return []

    palettes = []

    try:
        print("Loading Coolors.co trending palettes...")
        driver.get("https://coolors.co/palettes/trending")

        # Wait for palettes to load
        time.sleep(3)

        # Scroll to load more palettes if needed
        for _ in range(min(count // 10 + 1, 5)):
            driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
            time.sleep(1)

        # Find palette elements
        # Coolors uses various selectors - try multiple patterns
        palette_selectors = [
            "div[class*='palette']",
            "a[href*='/palette/']",
            "[data-palette]",
            ".explore-palette",
            ".palette-card"
        ]

        palette_elements = []
        for selector in palette_selectors:
            elements = driver.find_elements(By.CSS_SELECTOR, selector)
            if elements:
                palette_elements = elements
                print(f"Found {len(elements)} elements with selector: {selector}")
                break

        # Extract colors from href or data attributes
        for element in palette_elements[:count]:
            try:
                # Try to get palette from href
                href = element.get_attribute("href") or ""
                if "/palette/" in href:
                    # Extract hex codes from URL like /palette/264653-2a9d8f-e9c46a-f4a261-e76f51
                    match = re.search(r'/palette/([a-fA-F0-9-]+)', href)
                    if match:
                        colors = match.group(1).split('-')
                        if len(colors) >= 3:
                            palettes.append([f"#{c.upper()}" for c in colors])
                            continue

                # Try data attribute
                palette_data = element.get_attribute("data-palette")
                if palette_data:
                    colors = palette_data.split('-')
                    if len(colors) >= 3:
                        palettes.append([f"#{c.upper()}" for c in colors])
                        continue

                # Try to find color swatches within the element
                color_elements = element.find_elements(By.CSS_SELECTOR, "[style*='background']")
                if color_elements:
                    colors = []
                    for ce in color_elements:
                        style = ce.get_attribute("style") or ""
                        # Extract hex color from background-color style
                        hex_match = re.search(r'#([a-fA-F0-9]{6})', style)
                        if hex_match:
                            colors.append(f"#{hex_match.group(1).upper()}")
                        else:
                            # Try RGB format
                            rgb_match = re.search(r'rgb\((\d+),\s*(\d+),\s*(\d+)\)', style)
                            if rgb_match:
                                r, g, b = map(int, rgb_match.groups())
                                colors.append(f"#{r:02X}{g:02X}{b:02X}")
                    if len(colors) >= 3:
                        palettes.append(colors)

            except Exception as e:
                print(f"Error extracting palette: {e}")
                continue

        # Alternative: try to get all links that look like palette URLs
        if not palettes:
            print("Trying alternative extraction method...")
            all_links = driver.find_elements(By.TAG_NAME, "a")
            for link in all_links:
                href = link.get_attribute("href") or ""
                if "/palette/" in href and "coolors.co" in href:
                    match = re.search(r'/palette/([a-fA-F0-9]{6}(?:-[a-fA-F0-9]{6})+)', href)
                    if match:
                        colors = match.group(1).split('-')
                        palette = [f"#{c.upper()}" for c in colors]
                        if palette not in palettes:
                            palettes.append(palette)
                            if len(palettes) >= count:
                                break

        print(f"Extracted {len(palettes)} palettes")

    except Exception as e:
        print(f"Error fetching palettes: {e}")
    finally:
        driver.quit()

    return palettes[:count]


def fetch_from_api() -> List[List[str]]:
    """
    Try to fetch from Coolors API endpoints (may not work without auth).
    """
    endpoints = [
        "https://coolors.co/api/palettes/trending",
        "https://coolors.co/api/explore/trending",
    ]

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept": "application/json",
        "Referer": "https://coolors.co/palettes/trending"
    }

    for endpoint in endpoints:
        try:
            response = requests.get(endpoint, headers=headers, timeout=10)
            if response.status_code == 200:
                data = response.json()
                print(f"Got data from {endpoint}")
                return data
        except Exception:
            continue

    return []


def parse_page_data(html: str) -> List[List[str]]:
    """
    Try to parse encoded page data from the HTML.
    """
    import base64

    palettes = []

    # Look for encoded data variables
    match = re.search(r'var\s+page_data_encoded\s*=\s*["\']([^"\']+)["\']', html)
    if match:
        try:
            decoded = base64.b64decode(match.group(1)).decode('utf-8')
            data = json.loads(decoded)

            # Navigate the data structure to find palettes
            if isinstance(data, dict):
                for key in ['palettes', 'items', 'data', 'results']:
                    if key in data:
                        items = data[key]
                        if isinstance(items, list):
                            for item in items:
                                if isinstance(item, dict) and 'colors' in item:
                                    palettes.append(item['colors'])
                                elif isinstance(item, str) and '-' in item:
                                    colors = [f"#{c.upper()}" for c in item.split('-')]
                                    palettes.append(colors)
        except Exception as e:
            print(f"Failed to parse encoded data: {e}")

    # Also look for palette URLs in the HTML
    palette_urls = re.findall(r'/palette/([a-fA-F0-9]{6}(?:-[a-fA-F0-9]{6})+)', html)
    for url_colors in palette_urls:
        colors = [f"#{c.upper()}" for c in url_colors.split('-')]
        if colors not in palettes:
            palettes.append(colors)

    return palettes


def format_android_xml(palettes: List[List[str]], theme_names: Optional[List[str]] = None) -> str:
    """
    Format palettes as Android XML color resources.
    """
    output = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>', '']

    default_names = [
        "Theme_A", "Theme_B", "Theme_C", "Theme_D", "Theme_E",
        "Theme_F", "Theme_G", "Theme_H", "Theme_I", "Theme_J"
    ]

    for i, palette in enumerate(palettes):
        name = theme_names[i] if theme_names and i < len(theme_names) else default_names[i % len(default_names)]
        name_lower = name.lower().replace(' ', '_')

        output.append(f'    <!-- Palette: {name} -->')

        color_roles = ['primary', 'secondary', 'tertiary', 'accent', 'highlight']
        for j, color in enumerate(palette[:5]):
            role = color_roles[j] if j < len(color_roles) else f'color_{j+1}'
            # Ensure proper format with alpha
            hex_color = color.upper().replace('#', '')
            if len(hex_color) == 6:
                hex_color = f"FF{hex_color}"
            output.append(f'    <color name="{name_lower}_{role}">#{hex_color}</color>')

        output.append('')

    output.append('</resources>')
    return '\n'.join(output)


def format_json(palettes: List[List[str]]) -> str:
    """
    Format palettes as JSON.
    """
    return json.dumps(palettes, indent=2)


def format_hex(palettes: List[List[str]]) -> str:
    """
    Format palettes as simple hex lists.
    """
    output = []
    for i, palette in enumerate(palettes):
        output.append(f"Palette {i+1}: {' '.join(palette)}")
    return '\n'.join(output)


def main():
    parser = argparse.ArgumentParser(description='Fetch trending palettes from Coolors.co')
    parser.add_argument('--count', type=int, default=10, help='Number of palettes to fetch')
    parser.add_argument('--format', choices=['android', 'json', 'hex'], default='hex',
                        help='Output format')
    parser.add_argument('--output', '-o', type=str, help='Output file (default: stdout)')
    parser.add_argument('--method', choices=['selenium', 'api', 'auto'], default='auto',
                        help='Fetch method to use')

    args = parser.parse_args()

    palettes = []

    # Try different methods
    if args.method in ['api', 'auto']:
        print("Trying API method...")
        palettes = fetch_from_api()

    if not palettes and args.method in ['auto']:
        print("Trying to parse page data...")
        try:
            response = requests.get(
                "https://coolors.co/palettes/trending",
                headers={"User-Agent": "Mozilla/5.0"},
                timeout=15
            )
            palettes = parse_page_data(response.text)
        except Exception as e:
            print(f"Failed to fetch page: {e}")

    if not palettes or args.method == 'selenium':
        print("Using Selenium for full page rendering...")
        palettes = fetch_with_selenium(args.count)

    if not palettes:
        print("\nNo palettes found. Here are some popular palettes as fallback:\n")
        # Fallback to some known popular palettes
        palettes = [
            ["#264653", "#2A9D8F", "#E9C46A", "#F4A261", "#E76F51"],
            ["#606C38", "#283618", "#FEFAE0", "#DDA15E", "#BC6C25"],
            ["#003049", "#D62828", "#F77F00", "#FCBF49", "#EAE2B7"],
            ["#0D1B2A", "#1B263B", "#415A77", "#778DA9", "#E0E1DD"],
            ["#FFBE0B", "#FB5607", "#FF006E", "#8338EC", "#3A86FF"],
            ["#2B2D42", "#8D99AE", "#EDF2F4", "#EF233C", "#D90429"],
            ["#F72585", "#7209B7", "#3A0CA3", "#4361EE", "#4CC9F0"],
            ["#0A0908", "#22333B", "#EAE0D5", "#C6AC8F", "#5E503F"],
            ["#1A535C", "#4ECDC4", "#F7FFF7", "#FF6B6B", "#FFE66D"],
            ["#582F0E", "#7F4F24", "#936639", "#A68A64", "#B6AD90"],
        ]

    # Format output
    if args.format == 'android':
        result = format_android_xml(palettes[:args.count])
    elif args.format == 'json':
        result = format_json(palettes[:args.count])
    else:
        result = format_hex(palettes[:args.count])

    # Output
    if args.output:
        with open(args.output, 'w', encoding='utf-8') as f:
            f.write(result)
        print(f"Output written to {args.output}")
    else:
        print("\n" + "="*60)
        print("PALETTES")
        print("="*60 + "\n")
        print(result)


if __name__ == '__main__':
    main()

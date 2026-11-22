# Renders preview.html to a PNG: python3 shot.py out.png   (needs: pip install playwright && playwright install chromium)
import os, sys
from playwright.sync_api import sync_playwright
HERE = os.path.dirname(os.path.abspath(__file__))
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={'width': 390, 'height': 740}, device_scale_factor=2)
    pg.goto('file://' + os.path.join(HERE, 'preview.html')); pg.wait_for_timeout(800)
    pg.screenshot(path=sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'preview.png')); b.close()

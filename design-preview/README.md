# KipQuiet design preview

Scripts that draw the main screen as a picture, used to agree the look before changing the app.

- `mockup-reference.png` – Dave's target design. The corner tiles should match its shapes and style.
- `gen.py` – all the layout numbers: tile shapes (`pick_shape`: rounded guitar-pick traced from the mock-up), tile colours (`TILES`), icons (`I_MED`, `I_BED`, ...), ring, cards, buttons. Positions are in dp on a 390-wide screen.
- `make_preview.py` – fills in sample values and writes `preview.html`. Run `python3 make_preview.py active` for the running-timer state.
- `shot.py` – turns `preview.html` into a PNG.

Needs Python 3 with `shapely` and `playwright`:

    pip install shapely playwright && playwright install chromium
    python3 make_preview.py && python3 shot.py preview.png

The Android app draws the same things in `app/src/main/java/com/catstorm/dndtimer/` (`TileView.java` tiles, `RingView.java` circle, `MainActivity.java` layout). Note: as of build 21 the app still has the older tile shape; the preview here is the newer agreed-in-progress design.

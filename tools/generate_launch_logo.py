"""Render the illustrated launch logo from the artwork masters. Requires Pillow and CairoSVG."""
from io import BytesIO
from pathlib import Path

import cairosvg
from PIL import Image, ImageOps

from family_palette import COPPER, COPPER_DARK, COPPER_LIGHT, IVORY, SEAM, STEEL_DEEP, TRIM, WELL

OUT = Path(__file__).resolve().parents[1] / "artwork/logo/launch"
ICON_POSE = "seated"  # "open" keeps the five flying blocks in the avatar.
# Each glyph has its own advance and closed counters. Coordinates use a common 80-unit cap height.
GLYPHS = {
    "R": (76, "M0 80V0H52L66 14V37L55 48H46L70 80H46L25 50H20V80Z "
               "M20 16V34H42L46 30V20L42 16Z"),
    "e": (59, "M0 67V35L12 23H43L53 33V55H17V62L21 66H51L44 80H13Z "
               "M17 44H37V38L35 36H21L17 40Z"),
    "M": (92, "M0 80V0H21L42 35L63 0H84V80H65V30L42 64L19 30V80Z"),
    "a": (60, "M2 35L13 23H42L52 33V80H14L0 68V54L11 44H35V39L31 36H2Z "
               "M17 59V65L21 68H35V56H21Z"),
    "t": (47, "M8 23V9L26 4V23H43L39 37H26V61L31 66H43L39 80H20L8 68V37H0V23Z"),
    "r": (47, "M0 80V23H17V31L26 23H45L40 39H29L18 49V80Z"),
}


def colour(value):
    return "#%02x%02x%02x" % value


def crystal_fragment():
    """The terminal stroke of the r arrives as a glass voxel, echoing the block mark."""
    from machine_palette import RGB

    top = colour(TRIM)
    front = colour(RGB["mint"])
    side = colour(STEEL_DEEP)
    return (f'<g stroke="{colour(SEAM)}" stroke-width="1.25" stroke-linejoin="round">'
            f'<path d="M33 14l8 -4 9 4 -9 5Z" fill="{top}"/>'
            f'<path d="M33 14l8 5v9l-8 -5Z" fill="{front}"/>'
            f'<path d="M41 19l9 -5v9l-9 5Z" fill="{side}"/></g>')


def draw_type():
    """Extruded plate lettering with a clipped metal highlight on each face."""
    defs, letters = [], []
    x = 0
    for index, letter in enumerate("ReMatter"):
        advance, path = GLYPHS[letter]
        face, edge = (COPPER_LIGHT, COPPER_DARK) if index < 2 else (IVORY, STEEL_DEEP)
        defs.append(f'<clipPath id="clip{index}"><path d="{path}" clip-rule="evenodd"/></clipPath>')
        layers = [f'<g transform="translate({x})">']
        for offset in range(6, -1, -1):
            layers.append(f'<path transform="translate({offset} {offset})" d="{path}" '
                          f'fill="{colour(edge)}" stroke="{colour(SEAM)}" stroke-width="3" '
                          'stroke-linejoin="round" fill-rule="evenodd"/>')
        layers.append(f'<path d="{path}" fill="{colour(face)}" fill-rule="evenodd"/>')
        layers.append(f'<g clip-path="url(#clip{index})">')
        # Long machined top bevel, including the lower-case shoulders and counters.
        layers.append(f'<path d="{path}" transform="translate(0 2)" fill="none" '
                      f'stroke="{colour(IVORY if index < 2 else TRIM)}" stroke-width="1.5"/>')
        if letter in ("R", "M"):
            # Two square inlays on the foot echo the seams between material cells.
            layers.append(f'<path d="M2 72h5v5H2z M9 72h3v3H9z" fill="{colour(edge)}"/>')
        if index == 0:
            # The copper seam breaks at the shoulder where the ingot separates.
            layers.append(f'<path d="M0 10H11V2H28V0H8V7H0Z" fill="{colour(COPPER)}"/>')
        layers.append('</g>')
        if index == len("ReMatter") - 1:
            layers.append(crystal_fragment())
        layers.append('</g>')
        letters.extend(layers)
        x += advance
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {x+21} 96" '
            f'width="{x+21}" height="96" role="img" aria-label="ReMatter">\n'
            '<defs>' + ''.join(defs) + '</defs>\n<g transform="translate(14 3) skewX(-7)">\n'
            + '\n'.join(letters) + '\n</g></svg>\n')


def fit(canvas, picture, box):
    x, y, w, h = box
    picture = ImageOps.contain(picture, (w, h), Image.Resampling.LANCZOS)
    canvas.alpha_composite(picture, (x + (w - picture.width) // 2, y + (h - picture.height) // 2))


def main():
    poses = {pose: Image.open(OUT / 'sources' / f'{pose}.png').convert('RGBA') for pose in ('open', 'seated')}
    svg = draw_type()
    (OUT / "wordmark.svg").write_text(svg, encoding="utf-8")
    word = Image.open(BytesIO(cairosvg.svg2png(bytestring=svg.encode(), output_width=2200))).convert("RGBA")
    wide = Image.new("RGBA", (1800, 430))
    fit(wide, poses['open'], (15, 4, 460, 422))
    fit(wide, word, (475, 57, 1312, 314))
    wide.save(OUT / "ReMatter.png", optimize=True)
    for pose, picture in poses.items():
        icon = Image.new("RGBA", (1024, 1024), WELL)
        fit(icon, picture, (66, 58, 892, 908))
        filename = "ReMatter-icon.png" if pose == ICON_POSE else "ReMatter-icon-alternate.png"
        icon.convert("RGB").save(OUT / filename, optimize=True)


if __name__ == "__main__":
    main()

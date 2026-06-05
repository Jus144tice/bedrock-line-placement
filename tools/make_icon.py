"""One-off generator for the mod icon. Draws a straight row of blocks sitting on a
locked guide line, with a single emerald "lead" block and a forward chevron — the
Bedrock line-placement motif (sibling to bedrock-crafting-controls' 3x3 grid icon).
Supersampled for clean edges."""
from PIL import Image, ImageDraw, ImageFilter

S = 4  # supersampling factor
N = 256  # final size
W = N * S

img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
d = ImageDraw.Draw(img)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sx(v):
    """Scale a value expressed in final (256px) units up to the supersampled canvas."""
    return v * S


# --- shared palette with the sibling mod (bedrock-crafting-controls) --------
top = (38, 42, 56)
bot = (24, 27, 38)
accent = (61, 199, 142)  # emerald highlight
accent_dark = (38, 150, 104)
neutral = (62, 69, 92)
neutral_hi = (78, 86, 112)

# --- background: vertical gradient inside a rounded square ------------------
bg = Image.new("RGBA", (W, W), (0, 0, 0, 0))
bgd = ImageDraw.Draw(bg)
for y in range(W):
    bgd.line([(0, y), (W, y)], fill=lerp(top, bot, y / W) + (255,))
mask = Image.new("L", (W, W), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, W - 1], radius=sx(52), fill=255)
img.paste(bg, (0, 0), mask)

# subtle border
d.rounded_rectangle(
    [sx(2), sx(2), W - sx(2), W - sx(2)], radius=sx(50), outline=(70, 78, 104, 255), width=sx(2)
)

# --- geometry (all in final 256px units, scaled by sx) ----------------------
cx_center = N / 2
cy = N / 2  # the locked line runs horizontally through the icon's centre

cell = 38  # block size
gap = 10  # gap between blocks
n_blocks = 4  # 3 neutral + 1 emerald lead
chev_gap = 10  # gap between the lead block and the chevron
chev_w = 24  # chevron horizontal reach
chev_half_h = cell / 2  # chevron vertical half-height (matches block height)
radius = 9  # block corner radius

row_w = n_blocks * cell + (n_blocks - 1) * gap
total_w = row_w + chev_gap + chev_w
start_x = cx_center - total_w / 2
y0 = cy - cell / 2
y1 = cy + cell / 2

lead_index = n_blocks - 1  # rightmost block is the lead

# --- the locked guide line (behind the blocks) ------------------------------
line_y = cy
line_half = 5  # half-thickness
line_x0 = 26
line_x1 = N - 26
line_layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
ImageDraw.Draw(line_layer).rounded_rectangle(
    [sx(line_x0), sx(line_y - line_half), sx(line_x1), sx(line_y + line_half)],
    radius=sx(line_half),
    fill=accent + (120,),
)
img.alpha_composite(line_layer)

# --- the row of blocks ------------------------------------------------------
for i in range(n_blocks):
    bx0 = start_x + i * (cell + gap)
    bx1 = bx0 + cell
    if i == lead_index:
        # glow behind the lead block
        glow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        ImageDraw.Draw(glow).rounded_rectangle(
            [sx(bx0 - 8), sx(y0 - 8), sx(bx1 + 8), sx(y1 + 8)],
            radius=sx(radius + 6),
            fill=accent + (95,),
        )
        glow = glow.filter(ImageFilter.GaussianBlur(sx(6)))
        img.alpha_composite(glow)
        # vertical gradient fill for the lead block
        cw = int(sx(cell))
        cellimg = Image.new("RGBA", (cw, cw), (0, 0, 0, 0))
        cd = ImageDraw.Draw(cellimg)
        for yy in range(cw):
            cd.line([(0, yy), (cw, yy)], fill=lerp(accent, accent_dark, yy / cw) + (255,))
        cmask = Image.new("L", (cw, cw), 0)
        ImageDraw.Draw(cmask).rounded_rectangle([0, 0, cw - 1, cw - 1], radius=sx(radius), fill=255)
        img.paste(cellimg, (int(sx(bx0)), int(sx(y0))), cmask)
    else:
        d.rounded_rectangle([sx(bx0), sx(y0), sx(bx1), sx(y1)], radius=sx(radius), fill=neutral + (255,))
        # top highlight strip for a little depth
        d.rounded_rectangle(
            [sx(bx0), sx(y0), sx(bx1), sx(y0 + cell * 0.5)],
            radius=sx(radius),
            fill=neutral_hi + (90,),
        )

# --- forward chevron (the locked direction) ---------------------------------
chx0 = start_x + row_w + chev_gap
chx1 = chx0 + chev_w
pts = [
    (sx(chx0), sx(cy - chev_half_h)),
    (sx(chx1), sx(cy)),
    (sx(chx0), sx(cy + chev_half_h)),
]
stroke = sx(11)
d.line(pts, fill=accent + (255,), width=int(stroke), joint="curve")
# round caps at the three vertices so the chevron looks clean
r = stroke / 2
for px, py in pts:
    d.ellipse([px - r, py - r, px + r, py + r], fill=accent + (255,))

# --- downscale --------------------------------------------------------------
out = img.resize((N, N), Image.LANCZOS)
out.save(r"C:\Users\jenny\bedrock-line-placement\src\main\resources\bedrocklineplacement.png")
print("wrote icon")

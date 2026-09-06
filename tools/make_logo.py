#!/usr/bin/env python3
"""Generate a clean AT mark for launcher, in-app, and notification icons."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path("/tmp/atcrm-android/app/src/main/res")
FONT = "/usr/share/fonts/truetype/macos/Inter-Bold.ttf"
ACCENT = (79, 107, 237, 255)  # #4F6BED
WHITE = (255, 255, 255, 255)


def load_font(px: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT, px)


def centered_at(size: int, fill, bg, pad_ratio: float) -> Image.Image:
    img = Image.new("RGBA", (size, size), bg)
    draw = ImageDraw.Draw(img)
    font = load_font(max(8, int(size * pad_ratio)))
    text = "AT"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (size - tw) / 2 - bbox[0]
    y = (size - th) / 2 - bbox[1] - size * 0.02
    draw.text((x, y), text, font=font, fill=fill)
    return img


def circle(img: Image.Image) -> Image.Image:
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, img.size[0] - 1, img.size[1] - 1), fill=255)
    out = img.copy()
    out.putalpha(mask)
    return out


def save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "PNG")
    print(path, img.size)


def main() -> None:
    # In-app mark: full-bleed brand square + white AT
    mark = centered_at(512, WHITE, ACCENT, 0.46)
    save(mark, ROOT / "drawable" / "atcrm_mark.png")

    # Adaptive foreground: transparent, AT in the inner ~50% (safe zone is 66%)
    fg = centered_at(1024, WHITE, (0, 0, 0, 0), 0.38)
    densities = {
        "mdpi": 1,
        "hdpi": 1.5,
        "xhdpi": 2,
        "xxhdpi": 3,
        "xxxhdpi": 4,
    }
    for name, scale in densities.items():
        fg_px = int(108 * scale)
        save(fg.resize((fg_px, fg_px), Image.Resampling.LANCZOS), ROOT / f"drawable-{name}" / "ic_launcher_foreground.png")

        icon_px = int(48 * scale)
        icon = centered_at(icon_px * 4, WHITE, ACCENT, 0.42).resize((icon_px, icon_px), Image.Resampling.LANCZOS)
        save(icon, ROOT / f"mipmap-{name}" / "ic_launcher.png")
        save(circle(icon), ROOT / f"mipmap-{name}" / "ic_launcher_round.png")


if __name__ == "__main__":
    main()

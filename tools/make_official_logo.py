#!/usr/bin/env python3
"""Build Android launcher + in-app assets from the official AT CRM logo."""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path("/tmp/atcrm-android/app/src/main/res")
CANDIDATES = [
    Path("/opt/cursor/artifacts/assets/atcrm-official-mark.png"),
    Path("/opt/cursor/artifacts/assets/atcrm-mark-transparent.png"),
    Path("/opt/cursor/artifacts/assets/atcrm-launcher-192.png"),
    Path("/opt/cursor/artifacts/atcrm-logo-icon.png"),
]
LOCKUP_SRC = Path("/opt/cursor/artifacts/assets/atcrm-official-lockup.png")
NAVY = (11, 18, 32, 255)
WHITE = (255, 255, 255, 255)


def knock_bg(img: Image.Image, white_thresh: int = 248, dark_thresh: int = 18) -> Image.Image:
    img = img.convert("RGBA")
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 8:
                continue
            if r >= white_thresh and g >= white_thresh and b >= white_thresh:
                px[x, y] = (r, g, b, 0)
            elif r <= dark_thresh and g <= dark_thresh and b <= dark_thresh:
                px[x, y] = (r, g, b, 0)
    return img


def content_bbox(img: Image.Image, pad: int) -> tuple[int, int, int, int]:
    alpha = img.split()[-1]
    box = alpha.getbbox()
    if not box:
        return (0, 0, img.size[0], img.size[1])
    l, t, r, b = box
    l = max(0, l - pad)
    t = max(0, t - pad)
    r = min(img.size[0], r + pad)
    b = min(img.size[1], b + pad)
    return (l, t, r, b)


def fit_square(src: Image.Image, size: int, bg, scale: float = 0.72) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), bg)
    inner = int(size * scale)
    fitted = src.copy()
    fitted.thumbnail((inner, inner), Image.Resampling.LANCZOS)
    x = (size - fitted.size[0]) // 2
    y = (size - fitted.size[1]) // 2
    canvas.alpha_composite(fitted, (x, y))
    return canvas


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


def to_white_silhouette(src: Image.Image, size: int) -> Image.Image:
    img = src.convert("RGBA")
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 20:
                px[x, y] = (255, 255, 255, 0)
            else:
                px[x, y] = (255, 255, 255, a)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    fitted = img.copy()
    fitted.thumbnail((size, size), Image.Resampling.LANCZOS)
    x = (size - fitted.size[0]) // 2
    y = (size - fitted.size[1]) // 2
    canvas.alpha_composite(fitted, (x, y))
    return canvas


def main() -> None:
    src_path = next((p for p in CANDIDATES if p.exists()), None)
    if src_path is None:
        raise SystemExit("No AT CRM mark found")
    print("mark source:", src_path)
    mark = knock_bg(Image.open(src_path))
    mark = mark.crop(content_bbox(mark, pad=24))

    if LOCKUP_SRC.exists():
        lockup = knock_bg(Image.open(LOCKUP_SRC))
        lockup = lockup.crop(content_bbox(lockup, pad=24))
        lockup_canvas = Image.new("RGBA", (1024, 1024), WHITE)
        fitted = lockup.copy()
        fitted.thumbnail((920, 920), Image.Resampling.LANCZOS)
        lockup_canvas.alpha_composite(fitted, ((1024 - fitted.size[0]) // 2, (1024 - fitted.size[1]) // 2))
        save(lockup_canvas, ROOT / "drawable" / "atcrm_lockup.png")

    save(fit_square(mark, 512, WHITE, 0.86), ROOT / "drawable" / "atcrm_mark.png")

    xml_fg = ROOT / "drawable" / "ic_launcher_foreground.xml"
    if xml_fg.exists():
        xml_fg.unlink()
        print("removed", xml_fg)

    densities = {
        "mdpi": 1,
        "hdpi": 1.5,
        "xhdpi": 2,
        "xxhdpi": 3,
        "xxxhdpi": 4,
    }
    for name, scale in densities.items():
        fg_px = int(108 * scale)
        # Adaptive foreground: transparent, mark in the inner safe zone.
        save(fit_square(mark, fg_px, (0, 0, 0, 0), 0.62), ROOT / f"drawable-{name}" / "ic_launcher_foreground.png")

        icon_px = int(48 * scale)
        icon = fit_square(mark, icon_px, NAVY, 0.72)
        save(icon, ROOT / f"mipmap-{name}" / "ic_launcher.png")
        save(circle(icon), ROOT / f"mipmap-{name}" / "ic_launcher_round.png")

    save(to_white_silhouette(mark, 96), ROOT / "drawable" / "ic_stat_notify.png")
    print("done")


if __name__ == "__main__":
    main()

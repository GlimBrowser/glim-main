#!/usr/bin/env python3
"""从 docs/icons.png 生成全平台应用图标。

用法：python3 tools/gen_icons.py
产物：
  Android  传统 mipmap（mdpi..xxxhdpi 方图 + 圆形）、自适应前景（xxxhdpi 432px）、背景色、anydpi-v26 XML
  iOS      AppIcon.appiconset 1024 常规/深色/着色三张
  桌面     icons/icon.icns、icons/icon.ico、icon-1024.png、窗口图标 icon.png
  Web      favicon-32 / favicon.ico / apple-touch-icon / icon-512
"""
from PIL import Image, ImageDraw
import json, os, pathlib, subprocess, tempfile

SRC = 'docs/icons.png'
BG = (18, 21, 30)
BG_HEX = '#12151E'


def main():
    src = Image.open(SRC).convert('RGB')

    # ---------- Android ----------
    densities = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
    for name, size in densities.items():
        base = f'app/androidApp/src/main/res/mipmap-{name}'
        os.makedirs(base, exist_ok=True)
        sq = src.resize((size, size), Image.LANCZOS)
        sq.save(f'{base}/ic_launcher.png', optimize=True)
        round_img = Image.new('RGB', (size, size), BG)
        mask = Image.new('L', (size * 4, size * 4), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
        round_img.paste(sq, (0, 0), mask.resize((size, size), Image.LANCZOS))
        round_img.save(f'{base}/ic_launcher_round.png', optimize=True)

    fg_dir = 'app/androidApp/src/main/res/drawable-xxxhdpi'
    os.makedirs(fg_dir, exist_ok=True)
    FG = 432
    fg = Image.new('RGBA', (FG, FG), (0, 0, 0, 0))
    inner = int(FG * 0.81)  # 内容落入自适应安全区
    art = src.resize((inner, inner), Image.LANCZOS).convert('RGBA')
    off = (FG - inner) // 2
    fg.paste(art, (off, off))
    fg.save(f'{fg_dir}/ic_launcher_foreground.png', optimize=True)

    os.makedirs('app/androidApp/src/main/res/values', exist_ok=True)
    pathlib.Path('app/androidApp/src/main/res/values/ic_launcher_background.xml').write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="ic_launcher_background">{BG_HEX}</color>\n</resources>\n'
    )
    for fname in ('ic_launcher', 'ic_launcher_round'):
        pathlib.Path(f'app/androidApp/src/main/res/mipmap-anydpi-v26/{fname}.xml').write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '</adaptive-icon>\n'
        )

    # ---------- iOS ----------
    ios = 'app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset'
    os.makedirs(ios, exist_ok=True)
    big = src.resize((1024, 1024), Image.LANCZOS)
    big.save(f'{ios}/app-icon-1024.png', optimize=True)
    src.point(lambda v: int(v * 0.72)).resize((1024, 1024), Image.LANCZOS).save(f'{ios}/app-icon-dark-1024.png')
    src.convert('L').convert('RGB').resize((1024, 1024), Image.LANCZOS).save(f'{ios}/app-icon-tinted-1024.png')
    contents = {
        'images': [
            {'filename': 'app-icon-1024.png', 'idiom': 'universal', 'platform': 'ios', 'size': '1024x1024'},
            {'appearances': [{'appearance': 'luminosity', 'value': 'dark'}], 'filename': 'app-icon-dark-1024.png',
             'idiom': 'universal', 'platform': 'ios', 'size': '1024x1024'},
            {'appearances': [{'appearance': 'luminosity', 'value': 'tinted'}], 'filename': 'app-icon-tinted-1024.png',
             'idiom': 'universal', 'platform': 'ios', 'size': '1024x1024'},
        ],
        'info': {'author': 'xcode', 'version': 1},
    }
    pathlib.Path(f'{ios}/Contents.json').write_text(json.dumps(contents, indent=2) + '\n')

    # ---------- 桌面 ----------
    os.makedirs('app/desktopApp/icons', exist_ok=True)
    src.resize((1024, 1024), Image.LANCZOS).save('app/desktopApp/icons/icon-1024.png', optimize=True)
    os.makedirs('app/desktopApp/src/main/resources/icons', exist_ok=True)
    src.resize((512, 512), Image.LANCZOS).save('app/desktopApp/src/main/resources/icon.png', optimize=True)
    src.resize((256, 256), Image.LANCZOS).save('app/desktopApp/src/main/resources/icons/icon.ico',
                                                sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    with tempfile.TemporaryDirectory() as tmp:
        iconset = os.path.join(tmp, 'icon.iconset')
        os.makedirs(iconset)
        for size in (16, 32, 64, 128, 256, 512):
            big.resize((size, size), Image.LANCZOS).save(f'{iconset}/icon_{size}x{size}.png')
            big.resize((size * 2, size * 2), Image.LANCZOS).save(f'{iconset}/icon_{size}x{size}@2x.png')
        subprocess.run(['iconutil', '-c', 'icns', iconset, '-o', 'app/desktopApp/icons/icon.icns'], check=False)
        if os.path.exists('app/desktopApp/icons/icon.icns'):
            import shutil
            shutil.copy('app/desktopApp/icons/icon.icns', 'app/desktopApp/src/main/resources/icons/icon.icns')

    # ---------- Web ----------
    web = pathlib.Path('app/webApp/src/webMain/resources')
    web.mkdir(parents=True, exist_ok=True)
    src.resize((32, 32), Image.LANCZOS).save(web / 'favicon-32.png')
    src.resize((180, 180), Image.LANCZOS).save(web / 'apple-touch-icon.png')
    src.resize((512, 512), Image.LANCZOS).save(web / 'icon-512.png')
    src.resize((512, 512), Image.LANCZOS).save(web / 'favicon.ico', sizes=[(16, 16), (32, 32), (48, 48)])
    print('icons generated from', SRC)


if __name__ == '__main__':
    main()

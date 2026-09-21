import os, struct, zlib, math

# 简单 PNG 写出器（无第三方依赖）
def write_png(path, width, height, pixel_fn):
    rows = []
    for y in range(height):
        row = b'\x00'  # filter type 0
        for x in range(width):
            row += bytes(pixel_fn(x, y))
        rows.append(row)
    raw = b''.join(rows)

    def chunk(typ, data):
        c = struct.pack('>I', len(data)) + typ + data
        c += struct.pack('>I', zlib.crc32(typ + data) & 0xffffffff)
        return c

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(raw))
    png += chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)

BASE = os.path.dirname(os.path.abspath(__file__))

BG = (255, 138, 60, 255)     # #FF8A3C
DOT = (255, 255, 255, 255)   # 白
HOLE = (255, 217, 179, 255)  # 高光

def icon_fn(size):
    cx, cy = size / 2.0, size / 2.0
    r = size * 0.24  # 白色圆半径
    rh = size * 0.13 # 内孔半径
    def fn(x, y):
        d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
        if d <= rh:
            return HOLE
        if d <= r:
            return DOT
        return BG
    return fn

sizes = {48: 'mipmap-mdpi', 72: 'mipmap-hdpi', 96: 'mipmap-xhdpi', 144: 'mipmap-xxhdpi', 192: 'mipmap-xxxhdpi'}
for size, folder in sizes.items():
    out = os.path.join(BASE, '..', 'app', 'src', 'main', 'res', folder)
    os.makedirs(out, exist_ok=True)
    write_png(os.path.join(out, 'ic_launcher.png'), size, size, icon_fn(size))
    print('generated', size, '->', os.path.join(folder, 'ic_launcher.png'))
print('done')

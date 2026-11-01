"""Export the game's original code-native pixel icons into a deterministic atlas.
This extends GameArt.java's existing editable cell sprites, not the generated backdrop.
Requires Pillow only for exporting PNGs; the game runtime does not require Python.
"""
from pathlib import Path
import re
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'src/main/java/fruit/client/shared/ui/GameArt.java').read_text(encoding='utf-8')
atlas = Image.new('RGBA', (9 * 32, 32))
colors = dict(r='#df6647', g='#6e934b', b='#6f4c32', h='#ffe7a4',
              y='#f4cf67', p='#8a64a6', d='#30453e', c='#78bdca', w='#e8f4dd')

for index, name in enumerate(('APPLE', 'BANANA', 'GRAPE', 'BOMB')):
    block = re.search(r'String\[\] ' + name + r'\s*=\s*\{(.*?)\};', source, re.S).group(1)
    rows = re.findall(r'"([.a-z]+)"', block)
    draw = ImageDraw.Draw(atlas)
    cells = [(x,y,c) for y,row in enumerate(rows) for x,c in enumerate(row) if c != '.']
    for x,y,c in cells:
        xx,yy=index*32+4+x*2,4+y*2
        draw.rectangle((xx-1,yy-1,xx+2,yy+2), fill='#3b4832')
    for x,y,c in cells:
        xx,yy=index*32+4+x*2,4+y*2
        color = colors[c]
        if c == 'h' and name == 'BOMB': color='#7b9692'
        if c == 'h' and name == 'GRAPE': color='#d9b9d9'
        draw.rectangle((xx,yy,xx+1,yy+1),fill=color)

icons = [
    ['.....cc.....','..c..cc..c..','...cccccc...','.c..cccc..c.','..cccccccc..','ccccwwwwcccc',
     'ccccwwwwcccc','..cccccccc..','.c..cccc..c.','...cccccc...','..c..cc..c..','.....cc.....'],
    ['.......yy...','......yyy...','.....yyy....','....yyy.....','...yyyyyyy..','..yyyyyyyy..',
     '......yyy...','.....yyy....','....yyy.....','...yyy......','..yyy.......','..yy........'],
    ['....bb......','...bbb......','bbbbbb......','bbbbbb..r.r.','bbbbbb...r..','bbbbbb..r.r.',
     'bbbbbb......','...bbb......','....bb......','............','............','............'],
    ['....bb......','...bbb...w..','bbbbbb..w.w.','bbbbbb..w.w.','bbbbbb..w.w.','bbbbbb..w.w.',
     'bbbbbb..w.w.','...bbb...w..','....bb......','............','............','............'],
    ['.....yy.....','.....yy.....','....yyyy....','yyyyyyyyyyyy','.yyyyyyyyyy.','..yyyyyyyy..',
     '...yyyyyy...','..yyyyyyyy..','..yyy..yyy..','.yyy....yyy.','.yy......yy.','............'],
]
for index,rows in enumerate(icons,4):
    draw=ImageDraw.Draw(atlas)
    for y,row in enumerate(rows):
        for x,c in enumerate(row):
            if c!='.':
                xx,yy=index*32+4+x*2,4+y*2
                draw.rectangle((xx,yy,xx+1,yy+1),fill=colors[c])

resources=ROOT/'src/main/resources/fruit'
atlas.save(resources/'sprites.png')
ui=Image.new('RGBA',(32,32))
d=ImageDraw.Draw(ui)
for inset,color in [(0,'#513d2b'),(1,'#947044'),(2,'#c49a5c'),(4,'#e8bf78'),(5,'#765632')]:
    d.rectangle((inset,inset,31-inset,31-inset),outline=color,width=1)
for x,y in ((3,3),(28,3),(3,28),(28,28)):
    d.rectangle((x,y,x+1,y+1),fill='#fff0b3')
ui.save(resources/'ui.png')
print('Exported sprites.png (9 cells) and ui.png (wood frame)')

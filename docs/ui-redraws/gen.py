#!/usr/bin/env python3
# Generates improved-redraw HTML mockups of the CRTK Workbench Board modes.
# Dark page-header card shell for Play/Solve/Relations/Draw; light old-rail for Analyze.
import os, html

OUT = os.path.dirname(os.path.abspath(__file__))

CSS = r"""
:root{
  --app:#15171a; --panel:#1c1f24; --card:#22262c; --card2:#262b32; --sel:#2a313b;
  --line:#30353d; --line2:#3a4048;
  --txt:#e6e8eb; --txt2:#9aa3ad; --txt3:#6b7480;
  --blue:#3b82f6; --blue2:#2f6fe0; --green:#22c55e; --amber:#f59e0b; --red:#ef4444;
  --yellow:#e0b400;
  --light-sq:#ecd6b4; --dark-sq:#b58863;
  --mono:"DejaVu Sans Mono",ui-monospace,monospace;
  --ui:"Inter","Segoe UI","DejaVu Sans",system-ui,sans-serif;
}
body.light{
  --app:#f4f5f6; --panel:#ffffff; --card:#fbfbfc; --card2:#f4f5f6; --sel:#e7eefc;
  --line:#e2e4e8; --line2:#d4d7dd;
  --txt:#1a1d21; --txt2:#5a626c; --txt3:#8b929b;
  --blue:#2563eb; --blue2:#1d4ed8;
}
*{box-sizing:border-box; margin:0; padding:0}
html,body{width:1600px; height:1000px}
body{background:var(--app); color:var(--txt); font-family:var(--ui); font-size:13px;
  -webkit-font-smoothing:antialiased; overflow:hidden}
.col{display:flex; flex-direction:column}
.row{display:flex; align-items:center}
.sp{flex:1}
.muted{color:var(--txt2)} .dim{color:var(--txt3)}
.mono{font-family:var(--mono)}

/* OS menubar */
.menubar{height:30px; background:var(--app); border-bottom:1px solid var(--line);
  display:flex; align-items:center; gap:16px; padding:0 12px; color:var(--txt2); font-size:12.5px}
.menubar .ttl{position:absolute; left:50%; transform:translateX(-50%); color:var(--txt); font-weight:600}
.menubar .pal{margin-left:auto; width:330px; height:18px; background:var(--card); border:1px solid var(--line);
  border-radius:5px; color:var(--txt3); font-size:11px; display:flex; align-items:center; padding:0 8px; gap:8px}
.dots{display:flex; gap:7px; margin-left:14px}
.dots i{width:11px; height:11px; border-radius:50%; display:inline-block}
.dots .r{background:#ff5f57}.dots .y{background:#febc2e}.dots .g{background:#28c840}

/* top nav */
.nav{height:42px; display:flex; align-items:center; gap:6px; padding:0 14px; border-bottom:1px solid var(--line);
  background:var(--app)}
.nav .t{padding:7px 12px; border-radius:7px; color:var(--txt2); font-weight:500; font-size:13px}
.nav .t.on{background:var(--card); color:var(--txt); box-shadow:inset 0 0 0 1px var(--line2)}
.statpill{display:inline-flex; align-items:center; gap:7px; height:26px; padding:0 12px; border-radius:14px;
  background:rgba(34,197,94,.12); color:var(--green); font-weight:600; font-size:12px; border:1px solid rgba(34,197,94,.35)}
.statpill .d{width:7px;height:7px;border-radius:50%;background:var(--green)}
.kebab{color:var(--txt3); padding:0 6px; font-size:18px}

/* mode bar + page header */
.modewrap{padding:14px 22px 0 22px}
.modebar{display:inline-flex; gap:4px; background:var(--panel); border:1px solid var(--line);
  border-radius:9px; padding:4px}
.modebar .m{padding:7px 15px; border-radius:6px; color:var(--txt2); font-weight:600; font-size:13px}
.modebar .m.on{background:var(--blue); color:#fff}
.pagehead{display:flex; align-items:flex-end; margin-top:14px}
.pagehead h1{font-size:23px; font-weight:700; letter-spacing:.2px}
.pagehead .sub{color:var(--txt2); font-size:13.5px; margin-top:5px}
.pagehead .acts{margin-left:auto; display:flex; gap:9px}

/* buttons */
.btn{height:34px; padding:0 15px; border-radius:8px; border:1px solid var(--line2); background:var(--card);
  color:var(--txt); font-weight:600; font-size:13px; display:inline-flex; align-items:center; gap:7px; cursor:default}
.btn.pri{background:var(--blue); border-color:var(--blue); color:#fff}
.btn.dng{background:rgba(239,68,68,.12); border-color:rgba(239,68,68,.4); color:#ff7a7a}
.btn.gho{background:transparent; border-color:var(--line2); color:var(--txt2)}
.btn.sm{height:30px; padding:0 11px; font-size:12.5px}
.btn.xs{height:27px; padding:0 9px; font-size:12px; font-weight:600}

/* main grid */
.main{display:grid; grid-template-columns:744px 1fr; gap:20px; padding:16px 22px 22px 22px; height:calc(1000px - 30px - 42px - 86px)}

/* board card */
.boardcard{background:var(--panel); border:1px solid var(--line); border-radius:12px; padding:16px; display:flex; flex-direction:column}
.bc-head{display:flex; align-items:center; margin-bottom:12px}
.bc-head .t{font-size:15px; font-weight:700}
.bc-head .s{color:var(--txt2); font-size:12px; margin-top:2px}
.bc-head .tag{margin-left:auto; display:flex; gap:7px}
.tagpill{height:24px; padding:0 11px; border-radius:12px; background:var(--card); border:1px solid var(--line2);
  color:var(--txt2); font-size:12px; font-weight:600; display:inline-flex; align-items:center; gap:6px}
.tagpill.live{color:var(--green); border-color:rgba(34,197,94,.4)}
.tagpill.live .d{width:6px;height:6px;border-radius:50%;background:var(--green)}

.boardwrap{display:flex; gap:12px; align-items:stretch}
.evalbar{width:18px; border-radius:5px; overflow:hidden; position:relative; border:1px solid var(--line2);
  display:flex; flex-direction:column}
.evalbar .b{background:#111; } .evalbar .w{background:#f3f3f3}
.evalbar .num{position:absolute; bottom:4px; left:50%; transform:translateX(-50%); font-size:9px; font-weight:700;
  color:#111; font-family:var(--mono)}
.board{position:relative; width:576px; height:576px; border:1px solid #00000055; border-radius:4px; overflow:hidden}
.sq{position:absolute; width:72px; height:72px; display:flex; align-items:center; justify-content:center}
.sq.l{background:var(--light-sq)} .sq.d{background:var(--dark-sq)}
.pc{font-size:54px; line-height:1; font-family:"DejaVu Sans",sans-serif}
.pc.w{color:#f6f3ee; -webkit-text-stroke:1.6px #3a3a3a; text-shadow:0 1px 1px #0003}
.pc.b{color:#262626; -webkit-text-stroke:1px #000}
.lab{position:absolute; font-size:10px; font-weight:700; opacity:.55}
.lab.f{right:4px; bottom:2px} .lab.r{left:4px; top:1px}
.ovl{position:absolute; inset:0; pointer-events:none}
.boardfoot{margin-top:13px; display:flex; align-items:center; background:var(--card); border:1px solid var(--line);
  border-radius:9px; padding:11px 14px; gap:12px}
.boardfoot .who{font-weight:700; font-size:13.5px}
.boardfoot .det{color:var(--txt2); font-size:12px}
.boardfoot .ctl{margin-left:auto; display:flex; gap:8px; align-items:center}
.clock{font-family:var(--mono); font-weight:700; font-size:18px}
.clock.run{color:var(--green)}

/* rail */
.rail{overflow:hidden; display:flex; flex-direction:column; gap:14px}
.railhead{font-size:13px; font-weight:800; letter-spacing:1.2px; color:var(--txt2); text-transform:uppercase}
.card{background:var(--card); border:1px solid var(--line); border-radius:12px; padding:15px}
.card.tight{padding:13px}
.ctitle{font-size:12px; font-weight:800; letter-spacing:.8px; text-transform:uppercase; color:var(--txt)}
.csub{color:var(--txt2); font-size:11.5px; margin-top:3px; margin-bottom:12px}
.grid2{display:grid; grid-template-columns:1fr 1fr; gap:11px}
.grid3{display:grid; grid-template-columns:1fr 1fr 1fr; gap:11px}
.cardgrid{display:grid; gap:14px}

.metric{background:var(--card2); border:1px solid var(--line); border-radius:10px; padding:11px 12px}
.metric .l{font-size:10px; font-weight:800; letter-spacing:.6px; text-transform:uppercase; color:var(--txt3)}
.metric .v{font-size:22px; font-weight:800; margin-top:3px}
.metric .v.amber{color:var(--amber)} .metric .v.green{color:var(--green)} .metric .v.blue{color:var(--blue)}
.metric .s{font-size:10.5px; color:var(--txt3); margin-top:2px}

.chip{display:inline-flex; align-items:center; height:24px; padding:0 11px; border-radius:13px; font-size:11.5px;
  font-weight:600; background:rgba(59,130,246,.14); color:#9cc0ff; border:1px solid rgba(59,130,246,.3)}
.chips{display:flex; flex-wrap:wrap; gap:7px}

.seg{display:inline-flex; background:var(--card2); border:1px solid var(--line2); border-radius:9px; padding:3px; gap:3px}
.seg .o{padding:6px 13px; border-radius:6px; color:var(--txt2); font-weight:600; font-size:12.5px}
.seg .o.on{background:var(--blue); color:#fff}
.seg.lg .o{padding:8px 15px}

.frow{display:flex; align-items:center; gap:10px; margin-top:11px}
.frow .k{color:var(--txt2); font-size:12.5px; min-width:96px}
.frow .v{margin-left:auto; display:flex; gap:7px}

.tog{width:38px; height:22px; border-radius:12px; background:var(--line2); position:relative; flex:0 0 auto}
.tog.on{background:var(--blue)} .tog i{position:absolute; top:2px; width:18px; height:18px; border-radius:50%;
  background:#fff; left:2px} .tog.on i{left:18px}

.input{height:40px; border:1px solid var(--line2); background:var(--app); border-radius:9px; color:var(--txt);
  display:flex; align-items:center; padding:0 12px; font-size:13px; width:100%}
.input.mono{font-family:var(--mono); font-size:12px; color:var(--txt2)}
.note{background:var(--card2); border:1px solid var(--line); border-left:3px solid var(--blue); border-radius:8px;
  padding:11px 13px; color:var(--txt2); font-size:12.5px; margin-top:11px}
.note.ok{border-left-color:var(--green); color:#bfe8cd; background:rgba(34,197,94,.07)}

/* ramp (difficulty) */
.ramp{display:flex; gap:2px; align-items:stretch; margin-top:4px}
.ramp .step{flex:1; text-align:center; padding:11px 4px; background:var(--card2); border:1px solid var(--line2);
  color:var(--txt2); font-weight:700; font-size:12px; position:relative}
.ramp .step:first-child{border-radius:9px 0 0 9px} .ramp .step:last-child{border-radius:0 9px 9px 0}
.ramp .step small{display:block; font-size:10px; font-weight:600; color:var(--txt3); margin-top:2px}
.ramp .step.on{background:var(--blue); border-color:var(--blue); color:#fff}
.ramp .step.on small{color:#dbe8ff}

/* spec list */
.spec{display:flex; flex-direction:column; gap:9px; margin-top:2px}
.spec .r{display:flex; align-items:center; gap:10px; font-size:13px}
.spec .r .k{color:var(--txt3); width:70px; font-size:11px; text-transform:uppercase; letter-spacing:.5px; font-weight:700}
.spec .r .v{font-weight:600}
.badge{display:inline-flex; align-items:center; height:22px; padding:0 9px; border-radius:6px; font-size:11.5px;
  font-weight:700; gap:6px}
.badge.ok{background:rgba(34,197,94,.14); color:#74e39b} .badge.ok .d{width:6px;height:6px;border-radius:50%;background:var(--green)}
.badge.neutral{background:var(--card2); color:var(--txt2)}

/* move history */
.mh{margin-top:4px}
.mh .hr{display:grid; grid-template-columns:34px 1fr 1fr 70px; padding:6px 4px; font-size:11px; color:var(--txt3);
  font-weight:700; text-transform:uppercase; letter-spacing:.4px}
.mh .mr{display:grid; grid-template-columns:34px 1fr 1fr 70px; padding:9px 4px; border-radius:7px; align-items:center}
.mh .mr:nth-child(even){background:var(--card2)}
.mh .mr .n{color:var(--txt3)} .mh .mr .san{font-family:var(--mono); font-size:13px}
.mh .bk{justify-self:end} .bktag{height:20px; padding:0 8px; border-radius:10px; background:rgba(59,130,246,.16);
  color:#9cc0ff; font-size:10.5px; font-weight:700}

/* channel list */
.chan{display:flex; flex-direction:column; gap:2px; margin-top:4px}
.chan .c{display:flex; align-items:center; gap:10px; padding:8px 6px; border-radius:7px}
.chan .c:hover{background:var(--card2)}
.chan .sw{width:13px; height:13px; border-radius:3px; flex:0 0 auto}
.chan .nm{font-family:var(--mono); font-size:12px}
.chan .ct{margin-left:auto; min-width:30px; text-align:center; height:22px; padding:0 8px; border-radius:11px;
  background:var(--card2); border:1px solid var(--line2); font-weight:700; font-size:12px}
.chan .cbx{width:15px;height:15px;border-radius:4px;border:1.5px solid var(--line2)}
.chan .cbx.on{background:var(--blue); border-color:var(--blue)}
.foldrow{display:flex; align-items:center; gap:8px; padding:9px 6px; color:var(--txt3); font-size:12px; cursor:default}

.legend .lg{display:flex; align-items:center; gap:11px; margin-top:10px; font-size:12.5px}
.legend .lg .sw{width:22px; height:8px; border-radius:4px}
.legend .lg .sw.dot{width:12px;height:12px;border-radius:50%}

/* tools palette (draw) */
.tools{display:grid; grid-template-columns:repeat(5,1fr); gap:8px; margin-top:2px}
.tools .tl{padding:11px 6px; text-align:center; border-radius:9px; background:var(--card2); border:1px solid var(--line2);
  color:var(--txt2); font-weight:700; font-size:12px}
.tools .tl.on{background:var(--blue); border-color:var(--blue); color:#fff}
.swatches{display:flex; gap:9px; align-items:center; margin-top:4px}
.swatches .s{width:24px; height:24px; border-radius:50%; border:2px solid transparent}
.swatches .s.on{border-color:#fff; box-shadow:0 0 0 2px var(--blue)}
.layer{display:flex; align-items:center; gap:10px; padding:9px 8px; border-radius:8px; background:var(--card2);
  border:1px solid var(--line); margin-top:8px; font-size:12.5px}
.layer .sw{width:13px;height:13px;border-radius:3px} .layer .x{margin-left:auto; color:var(--txt3); font-weight:700}
.kbd{display:inline-flex; align-items:center; height:22px; padding:0 8px; border-radius:6px; background:var(--card2);
  border:1px solid var(--line2); font-family:var(--mono); font-size:11px; color:var(--txt2)}
.sc{display:flex; align-items:center; gap:10px; margin-top:9px; font-size:12.5px; color:var(--txt2)}

/* ===== light old-rail Analyze ===== */
.ed-tabs{height:36px; display:flex; align-items:stretch; background:var(--app); border-bottom:1px solid var(--line)}
.ed-tabs .et{display:flex; align-items:center; gap:9px; padding:0 14px; color:var(--txt2); font-size:12.5px;
  border-right:1px solid var(--line)}
.ed-tabs .et.on{background:var(--panel); color:var(--txt); border-bottom:2px solid var(--blue)}
.ed-tabs .et .x{color:var(--txt3); font-size:13px}
.ed-tabs .plus{padding:0 12px; color:var(--txt3); align-self:center}
.amain{display:grid; grid-template-columns:1fr 420px; gap:0; height:calc(1000px - 30px - 36px)}
.aboard{padding:26px 24px; display:flex; gap:14px}
.arail{border-left:1px solid var(--line); background:var(--panel); padding:16px; overflow:hidden; display:flex;
  flex-direction:column; gap:13px}
.asec{}
.asec .h{font-size:11px; font-weight:800; letter-spacing:.7px; text-transform:uppercase; color:var(--txt3); margin-bottom:8px}
.iconrow{display:flex; gap:6px; flex-wrap:wrap}
.icbtn{width:34px; height:32px; border-radius:7px; border:1px solid var(--line2); background:var(--card);
  display:flex; align-items:center; justify-content:center; color:var(--txt2); font-size:14px}
.atabs{display:flex; gap:3px; flex-wrap:wrap; border-bottom:1px solid var(--line); padding-bottom:0}
.atabs .at{padding:7px 9px; font-size:12px; color:var(--txt2); font-weight:600; border-bottom:2px solid transparent}
.atabs .at.on{color:var(--txt); border-bottom-color:var(--blue)}
.empty{flex:1; border:1px dashed var(--line2); border-radius:10px; display:flex; flex-direction:column;
  align-items:center; justify-content:center; gap:9px; color:var(--txt3); min-height:150px; text-align:center; padding:18px}
.empty .t{font-weight:700; color:var(--txt2); font-size:14px}
.fencard{background:var(--card); border:1px solid var(--line); border-radius:10px; padding:12px}
.fencard .f{font-family:var(--mono); font-size:12px; color:var(--txt); word-break:break-all; line-height:1.5}
.fencard .meta{color:var(--txt2); font-size:11.5px; margin-top:7px; display:flex; align-items:center; gap:6px}
"""

# ---- chess rendering ----
GLYPH = {'k':'♚','q':'♛','r':'♜','b':'♝','n':'♞','p':'♟'}
FILES = 'abcdefgh'

def sq_xy(sq):
    f = FILES.index(sq[0]); r = int(sq[1]) - 1
    return f*72+36, (7-r)*72+36

def board_html(fen, arrows=None, circles=None, rings=None, highlights=None):
    arrows = arrows or []; circles = circles or []; rings = rings or []; highlights = highlights or []
    rows = fen.split()[0].split('/')
    cells = []
    hl = set(highlights)
    for ri, row in enumerate(rows):          # ri 0 = rank8 (top)
        rank = 8 - ri
        f = 0
        for ch in row:
            if ch.isdigit():
                for _ in range(int(ch)):
                    cells.append((f, rank, None)); f += 1
            else:
                cells.append((f, rank, ch)); f += 1
    sqhtml = []
    for f, rank, ch in cells:
        x = f*72; y = (8-rank)*72
        dark = (f + (rank-1)) % 2 == 0
        cls = 'd' if dark else 'l'
        sqid = FILES[f] + str(rank)
        bg = ''
        if sqid in hl: bg = 'background:#f4e07a !important;'
        lab = ''
        if rank == 1: lab += f'<span class="lab f">{FILES[f]}</span>'
        if f == 0: lab += f'<span class="lab r">{rank}</span>'
        pc = ''
        if ch:
            col = 'w' if ch.isupper() else 'b'
            pc = f'<span class="pc {col}">{GLYPH[ch.lower()]}</span>'
        sqhtml.append(f'<div class="sq {cls}" style="left:{x}px;top:{y}px;{bg}">{lab}{pc}</div>')
    # overlay
    defs = []; ov = []
    seen_colors = set()
    for a in arrows:
        c = a['color']
        if c not in seen_colors:
            seen_colors.add(c)
            mid = 'm'+c.strip('#')
            defs.append(f'<marker id="{mid}" markerWidth="3.2" markerHeight="3.2" refX="1.7" refY="1.6" orient="auto"><path d="M0,0 L3.2,1.6 L0,3.2 L1.1,1.6 Z" fill="{c}"/></marker>')
    for a in arrows:
        x1,y1 = sq_xy(a['from']); x2,y2 = sq_xy(a['to'])
        c = a['color']; mid='m'+c.strip('#')
        import math
        dx,dy=x2-x1,y2-y1; L=math.hypot(dx,dy); ux,uy=dx/L,dy/L
        x2b,y2b = x2-ux*22, y2-uy*22
        op = a.get('op',0.82); w=a.get('w',11)
        ov.append(f'<line x1="{x1}" y1="{y1}" x2="{x2b:.1f}" y2="{y2b:.1f}" stroke="{c}" stroke-width="{w}" stroke-linecap="round" opacity="{op}" marker-end="url(#{mid})"/>')
    for cc in circles:
        x,y = sq_xy(cc['sq'])
        ov.append(f'<circle cx="{x}" cy="{y}" r="{cc.get("r",30)}" fill="none" stroke="{cc["color"]}" stroke-width="{cc.get("w",6)}" opacity="{cc.get("op",0.85)}"/>')
    for rg in rings:
        x,y = sq_xy(rg['sq'])
        ov.append(f'<circle cx="{x}" cy="{y}" r="33" fill="none" stroke="{rg.get("color","#f4d03f")}" stroke-width="4" opacity="0.95"/>')
    svg = f'<svg class="ovl" viewBox="0 0 576 576"><defs>{"".join(defs)}</defs>{"".join(ov)}</svg>'
    return f'<div class="board">{"".join(sqhtml)}{svg}</div>'

def evalbar(white_frac=0.52, num='+0.3'):
    bf = (1-white_frac)*100; wf = white_frac*100
    return f'<div class="evalbar"><div class="b" style="height:{bf:.0f}%"></div><div class="w" style="flex:1"></div><div class="num">{num}</div></div>'

# ---- page scaffold ----
def shell(mode, title, sub, acts, boardcard, rail, light=False):
    modes = ['Analyze','Play','Solve','Relations','Draw']
    mb = ''.join(f'<div class="m {"on" if m==mode else ""}">{m}</div>' for m in modes)
    navs = ['Dashboard','Board','Run','Datasets','Publish','Engine Lab','Logs']
    nv = ''.join(f'<div class="t {"on" if n=="Board" else ""}">{n}</div>' for n in navs)
    acthtml = ''.join(acts)
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head>
<body class="{'light' if light else ''}">
<div class="menubar"><span class="dots"><i class="r"></i><i class="y"></i><i class="g"></i></span>
  <span>File</span><span>Edit</span><span>View</span><span>Go</span><span>Run</span><span>Terminal</span><span>Help</span>
  <span class="ttl">ChessRTK Workbench</span>
  <span class="pal"><span>⌘K</span><span>Command Palette</span></span></div>
<div class="nav">{nv}<span class="sp"></span>
  <span class="statpill"><span class="d"></span>Engine Ready</span><span class="kebab">⋮</span></div>
<div class="modewrap"><div class="modebar">{mb}</div>
  <div class="pagehead"><div><h1>{title}</h1><div class="sub">{sub}</div></div>
  <div class="acts">{acthtml}</div></div></div>
<div class="main">{boardcard}<div class="rail">{rail}</div></div>
</body></html>"""

def boardcard(t, s, tags, board, foot):
    return f"""<div class="boardcard"><div class="bc-head"><div><div class="t">{t}</div><div class="s">{s}</div></div>
  <div class="tag">{tags}</div></div>
  <div class="boardwrap">{board}</div>{foot}</div>"""

def btn(label, k='', extra=''):
    return f'<button class="btn {k}">{label}</button>'

# ================= PLAY =================
def play():
    board = evalbar(0.5,'0.0') + board_html(
        'rnbqkb1r/pppp1ppp/4pn2/8/2PP4/8/PP2PPPP/RNBQKBNR',
        arrows=[{'from':'b1','to':'c3','color':'#3b82f6','op':0.85}])
    foot = """<div class="boardfoot"><div><div class="who">You — White</div><div class="det">Your move</div></div>
      <div class="ctl"><button class="btn xs gho">Takeback</button><button class="btn xs gho">Hint</button>
      <button class="btn xs dng">Resign</button><button class="btn xs gho">Draw</button>
      <span class="clock run">9:58</span></div></div>"""
    bc = boardcard('Bot · Maximum', 'Black · NNUE HalfKP · classical α-β',
        '<span class="tagpill">2800</span><span class="tagpill">⏱ 10:00</span>', board, foot)
    rail = f"""
    <div class="railhead">Bot match</div>
    <div class="card"><div class="ctitle">Opponent</div><div class="csub">Ordered strength · personality</div>
      <div class="ramp">
        <div class="step">Rookie<small>800</small></div><div class="step">Casual<small>1200</small></div>
        <div class="step">Club<small>1600</small></div><div class="step">Expert<small>2000</small></div>
        <div class="step">Master<small>2400</small></div><div class="step on">Maximum<small>2800</small></div></div>
      <div class="note">Maximum — strongest available configuration (~2800). Full search, no handicap.</div>
      <div class="frow"><div class="k">Style</div><div class="v"><span class="seg"><span class="o on">Balanced</span><span class="o">Aggressive</span><span class="o">Solid</span></span></div></div>
      <div class="frow"><div class="k">Think time</div><div class="v"><span class="seg"><span class="o on">1s</span><span class="o">3s</span><span class="o">10s</span></span></div></div>
      <div class="frow"><div class="k">Opening book</div><div class="v"><span class="tog on"><i></i></span></div></div>
    </div>
    <div class="grid2">
      <div class="card tight"><div class="ctitle">Game controls</div><div class="csub">Sides &amp; start</div>
        <div class="frow"><div class="k">You play</div><div class="v"><span class="seg"><span class="o on">White</span><span class="o">Black</span><span class="o">Rnd</span></span></div></div>
        <div class="frow"><div class="k">Start from</div><div class="v"><span class="seg"><span class="o on">Std</span><span class="o">Cur</span><span class="o">FEN</span></span></div></div>
        <div class="frow"><div class="k">Time</div><div class="v"><span class="seg"><span class="o on">10+0</span><span class="o">None</span></span></div></div>
        <div class="frow"><div class="k">Show eval</div><div class="v"><span class="tog"><i></i></span></div></div>
      </div>
      <div class="card tight"><div class="ctitle">Bot profile</div><div class="csub">Active engine config</div>
        <div class="spec">
          <div class="r"><span class="k">Engine</span><span class="v">NNUE HalfKP</span></div>
          <div class="r"><span class="k">Mode</span><span class="v">classical α-β</span></div>
          <div class="r"><span class="k">Book</span><span class="v">on</span></div>
          <div class="r"><span class="k">Status</span><span class="badge ok"><span class="d"></span>Ready</span></div>
        </div>
        <div class="note" style="margin-top:14px">Tip: <b>F</b> flips the board, <b>Ctrl+Enter</b> requests a hint.</div>
      </div>
    </div>
    <div class="card"><div class="ctitle">Move history</div><div class="csub">Current game line</div>
      <div class="mh"><div class="hr"><div>#</div><div>White</div><div>Black</div><div></div></div>
        <div class="mr"><div class="n">1</div><div class="san">d4</div><div class="san">Nf6</div><div class="bk"><span class="bktag">book</span></div></div>
        <div class="mr"><div class="n">2</div><div class="san">c4</div><div class="san">e6</div><div class="bk"><span class="bktag">book</span></div></div>
      </div></div>
    <div class="note ok">After the game: automatic blunder review and engine-line export are enabled.</div>
    """
    acts = [btn('New game','pri'), btn('Copy PGN'), btn('Analyze')]
    return shell('Play','Board / Play','Against bot · White to move · Move 3 · Material even', acts,
                 bc, rail)

# ================= SOLVE =================
def solve():
    board = evalbar(0.6,'+1.4') + board_html(
        'r2q1rk1/ppp2ppp/2n1bn2/3p4/1B2P3/1N3N2/PPP2PPP/R2Q1RK1',
        rings=[{'sq':'e6'}])
    foot = """<div class="boardfoot"><div class="row" style="gap:10px; flex:1">
      <div class="who" style="white-space:nowrap">Your move</div>
      <div class="input" style="height:32px; flex:1; max-width:230px">type SAN or play on board…</div>
      <button class="btn xs pri">Submit</button></div>
      <div class="ctl"><button class="btn xs gho">Hint</button><button class="btn xs gho">Reveal</button><button class="btn xs gho">Skip</button></div></div>"""
    bc = boardcard('Puzzle 1 / 12', 'Find the strongest move for White',
        '<span class="tagpill">8%</span>', board, foot)
    rail = f"""
    <div class="railhead">Puzzle trainer</div>
    <div class="grid3">
      <div class="metric"><div class="l">Rating</div><div class="v amber">1720</div><div class="s">estimated</div></div>
      <div class="metric"><div class="l">Streak</div><div class="v">0</div><div class="s">today</div></div>
      <div class="metric"><div class="l">Accuracy</div><div class="v dim">—</div><div class="s">not tried</div></div>
    </div>
    <div class="card"><div class="ctitle">Themes</div>
      <div class="chips" style="margin-top:10px"><span class="chip">fork</span><span class="chip">king safety</span><span class="chip">development</span></div></div>
    <div class="card"><div class="ctitle">Queue</div><div class="csub">Source &amp; repetition policy</div>
      <div class="frow"><div class="k">Source</div><div class="v"><span class="seg"><span class="o on">Chessweb set</span><span class="o">Custom</span></span></div></div>
      <div class="frow"><div class="k">Mode</div><div class="v"><span class="seg"><span class="o on">Explore</span><span class="o">Strict</span></span></div></div>
      <div class="frow"><div class="k">Skip repeats</div><div class="v"><span class="tog on"><i></i></span></div></div>
    </div>
    <div class="card"><div class="ctitle">Attempt line</div><div class="csub">Read-only · navigation only (entry is on the board)</div>
      <div class="input mono" style="margin-top:2px">1. — candidate line appears here</div>
      <div class="row" style="gap:8px; margin-top:11px"><button class="btn sm gho">‹ Prev</button><button class="btn sm gho">Next ›</button><button class="btn sm gho">Random</button><button class="btn sm gho">Restart</button></div>
    </div>
    <div class="grid2">
      <div class="card tight"><div class="ctitle">PGN source</div>
        <div class="spec" style="margin-top:10px">
          <div class="r"><span class="k">Event</span><span class="v">Training</span></div>
          <div class="r"><span class="k">FEN</span><span class="v mono" style="font-size:11px">r2q1rk1/…</span></div>
          <div class="r"><span class="k">Side</span><span class="v">White</span></div>
        </div></div>
      <div class="card tight"><div class="ctitle">Solution</div><div class="csub">Hidden until reveal</div>
        <div class="empty" style="min-height:74px; border-style:solid; background:var(--card2)"><div class="t" style="color:var(--txt3)">Solution hidden</div></div>
        <div class="frow"><div class="k" style="min-width:0">Hint depth</div><div class="v" style="flex:1; margin-left:10px">
          <div style="flex:1; height:6px; border-radius:3px; background:var(--line2); position:relative"><span style="position:absolute; left:0; width:8%; height:6px; border-radius:3px; background:var(--amber)"></span><span style="position:absolute; left:8%; top:-3px; width:12px; height:12px; border-radius:50%; background:var(--amber)"></span></div>
          <span class="dim" style="font-size:11px; margin-left:8px">0 / 3</span></div></div>
      </div>
    </div>
    """
    acts = [btn('Load puzzle','pri'), btn('Import file'), btn('Sample')]
    return shell('Solve','Board / Puzzles','Training queue · White to move · Tactic · Puzzle 1 of 12', acts,
                 bc, rail)

# ================= RELATIONS =================
def relations():
    A_YEL='#e0b400'; A_GRN='#22c55e'; A_BLU='#3b82f6'
    arrows=[]
    # us pawns attack diagonals (yellow) - white pawns from rank2 to rank3 diagonals (sample)
    for f in 'bdfg':
        arrows.append({'from':f+'2','to':chr(ord(f)+1)+'3','color':A_YEL,'w':9,'op':0.8})
    # them attacks (green) black pawns
    for f in 'bdfg':
        arrows.append({'from':f+'7','to':chr(ord(f)+1)+'6','color':A_GRN,'w':9,'op':0.8})
    # knight attacks (yellow) b8->c6 etc
    arrows.append({'from':'b8','to':'c6','color':A_YEL,'w':9,'op':0.85})
    arrows.append({'from':'g8','to':'f6','color':A_YEL,'w':9,'op':0.85})
    arrows.append({'from':'b1','to':'c3','color':A_GRN,'w':9,'op':0.85})
    arrows.append({'from':'g1','to':'f3','color':A_GRN,'w':9,'op':0.85})
    # bishop ray (blue) long diagonal
    arrows.append({'from':'c8','to':'h3','color':A_BLU,'w':8,'op':0.7})
    arrows.append({'from':'f1','to':'a6','color':A_BLU,'w':8,'op':0.7})
    board = board_html('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR', arrows=arrows)
    foot = """<div class="boardfoot"><div><div class="who">Selected edge</div>
      <div class="det">b8 ♞ → c6 · knight_attack · feature #21953</div></div>
      <div class="ctl"><button class="btn xs gho">Set as focus</button></div></div>"""
    bc = boardcard('Relations overlay', 'Typed edges read by the network input channels',
        '<span class="tagpill">12 edges</span><span class="tagpill live"><span class="d"></span>live</span>', board, foot)
    rail = f"""
    <div class="railhead">Relations explorer</div>
    <div class="card"><div class="ctitle">Dataset / source</div><div class="csub">Adapter follows the board position</div>
      <div class="row" style="gap:9px"><div class="input mono" style="flex:1">rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1</div><button class="btn sm gho">Copy</button></div>
      <div class="frow"><div class="k">Mode</div><div class="v"><span class="seg"><span class="o on">All</span><span class="o">Selected</span><span class="o">Changed</span></span></div></div>
    </div>
    <div class="grid2" style="align-items:start">
      <div class="card tight"><div class="ctitle">Channels</div><div class="csub">Toggle typed edge groups</div>
        <div class="chan">
          <div class="c"><span class="cbx on"></span><span class="sw" style="background:{A_YEL}"></span><span class="nm">us_attacks_them</span><span class="ct">6</span></div>
          <div class="c"><span class="cbx on"></span><span class="sw" style="background:{A_GRN}"></span><span class="nm">them_attacks_us</span><span class="ct">6</span></div>
          <div class="c"><span class="cbx on"></span><span class="sw" style="background:{A_YEL}"></span><span class="nm">knight_attack</span><span class="ct">12</span></div>
          <div class="c"><span class="cbx on"></span><span class="sw" style="background:{A_BLU}"></span><span class="nm">bishop_ray</span><span class="ct">2</span></div>
        </div>
        <div class="foldrow">▸ 5 inactive channels (0 edges)</div>
        <div class="row" style="gap:7px; margin-top:8px"><button class="btn xs gho">All</button><button class="btn xs gho">None</button><button class="btn xs gho">Invert</button></div>
      </div>
      <div class="col" style="gap:14px">
        <div class="card tight legend"><div class="ctitle">Legend</div>
          <div class="lg"><span class="sw" style="background:{A_YEL}"></span>Opponent piece attacks</div>
          <div class="lg"><span class="sw" style="background:{A_GRN}"></span>Own piece attacks</div>
          <div class="lg"><span class="sw" style="background:{A_BLU}"></span>Sliding ray visible</div>
          <div class="lg"><span class="sw" style="background:linear-gradient(90deg,#fff6,#fff)"></span>Opacity = confidence</div>
        </div>
        <div class="card tight"><div class="ctitle">Selected edge</div>
          <div class="spec" style="margin-top:10px">
            <div class="r"><span class="k">From</span><span class="v">b8 · ♞</span></div>
            <div class="r"><span class="k">To</span><span class="v">c6</span></div>
            <div class="r"><span class="k">Channel</span><span class="v mono" style="font-size:11.5px">knight_attack</span></div>
            <div class="r"><span class="k">Feature</span><span class="v mono">#21953</span></div>
          </div>
          <div class="row" style="gap:7px; margin-top:11px"><button class="btn xs gho">Center</button><button class="btn xs gho">Copy edge</button></div>
        </div>
      </div>
    </div>
    <div class="card"><div class="ctitle">Overlay controls</div><div class="csub">Density &amp; readability</div>
      <div class="grid2" style="margin-top:10px">
        <div><div class="muted" style="font-size:11px">Opacity</div><div style="height:6px; border-radius:3px; background:var(--line2); margin-top:7px; position:relative"><span style="position:absolute; left:0; width:62%; height:6px; border-radius:3px; background:var(--blue)"></span><span style="position:absolute; left:62%; top:-3px; width:12px;height:12px;border-radius:50%;background:var(--blue)"></span></div></div>
        <div><div class="muted" style="font-size:11px">Edge width</div><div style="height:6px; border-radius:3px; background:var(--line2); margin-top:7px; position:relative"><span style="position:absolute; left:0; width:78%; height:6px; border-radius:3px; background:var(--yellow)"></span><span style="position:absolute; left:78%; top:-3px; width:12px;height:12px;border-radius:50%;background:var(--yellow)"></span></div></div>
      </div>
      <div class="row" style="gap:22px; margin-top:14px">
        <div class="row" style="gap:8px"><span class="tog"><i></i></span><span class="muted">Show labels</span></div>
        <div class="row" style="gap:8px"><span class="tog on"><i></i></span><span class="muted">Hide weak edges</span></div>
        <div class="row" style="gap:8px"><span class="tog on"><i></i></span><span class="muted">Live sync</span></div>
      </div>
    </div>
    """
    acts = [btn('Sync to board','pri'), btn('Load FEN'), btn('Export')]
    return shell('Relations','Board / Relations','Tactical incidence · 12 edges across 4 channels · Current position', acts,
                 bc, rail)

# ================= DRAW =================
def draw():
    G='#22c55e'; O='#f59e0b'
    board = board_html('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR',
        arrows=[{'from':'c2','to':'c4','color':G,'w':12},{'from':'d2','to':'d4','color':G,'w':12},
                {'from':'g1','to':'f3','color':G,'w':12}],
        circles=[{'sq':'e4','color':O,'w':6}])
    foot = """<div class="boardfoot"><div><div class="who">Active tool: Arrow</div>
      <div class="det">green · width 9 · opacity 82% · snap to square</div></div>
      <div class="ctl"><button class="btn xs gho">Undo</button></div></div>"""
    bc = boardcard('Drawing canvas', 'Drag between squares for arrows; click a square for circles',
        '<span class="tagpill">3 objects</span>', board, foot)
    rail = f"""
    <div class="railhead">Drawing tools</div>
    <div class="card"><div class="ctitle">Tool</div><div class="csub">Choose annotation type</div>
      <div class="tools"><div class="tl on">↗ Arrow</div><div class="tl">○ Circle</div><div class="tl">□ Square</div><div class="tl">✎ Free</div><div class="tl">T Text</div></div>
      <div class="row" style="gap:8px; margin-top:11px"><button class="btn sm dng">Clear drawings</button><button class="btn sm gho">Undo</button><button class="btn sm gho">Redo</button></div>
    </div>
    <div class="grid2" style="align-items:start">
      <div class="card tight"><div class="ctitle">Style</div><div class="csub">Color, width, opacity, snapping</div>
        <div class="swatches"><span class="s on" style="background:#22c55e"></span><span class="s" style="background:#ef4444"></span><span class="s" style="background:#3b82f6"></span><span class="s" style="background:#f59e0b"></span><span class="s" style="background:#eab308"></span><span class="s" style="background:#a855f7"></span><button class="btn xs gho" style="margin-left:4px">Custom</button></div>
        <div class="frow"><div class="k">Width</div><div class="v"><span class="badge neutral">9 px</span></div></div>
        <div class="frow"><div class="k">Opacity</div><div class="v" style="flex:1; margin-left:10px"><div style="flex:1; height:6px; border-radius:3px; background:var(--line2); position:relative"><span style="position:absolute; left:0; width:82%; height:6px; border-radius:3px; background:var(--green)"></span><span style="position:absolute; left:82%; top:-3px; width:12px;height:12px;border-radius:50%;background:#fff"></span></div></div></div>
        <div class="frow"><div class="k">Snap to square</div><div class="v"><span class="tog on"><i></i></span></div></div>
        <div class="frow"><div class="k">Suggested arrows</div><div class="v"><span class="tog on"><i></i></span></div></div>
        <div class="note">Suggested arrows overlay the engine's recommended move as a faint hint.</div>
      </div>
      <div class="card tight"><div class="ctitle">Objects</div><div class="csub">Visible annotation layers</div>
        <div class="layer"><span class="sw" style="background:#22c55e"></span>Arrow · c2 → c4<span class="x">×</span></div>
        <div class="layer"><span class="sw" style="background:#22c55e"></span>Arrow · d2 → d4<span class="x">×</span></div>
        <div class="layer"><span class="sw" style="background:#22c55e"></span>Arrow · g1 → f3<span class="x">×</span></div>
        <div class="layer"><span class="sw" style="background:#f59e0b"></span>Circle · e4<span class="x">×</span></div>
        <div class="row" style="gap:8px; margin-top:11px"><button class="btn sm gho">Group</button><button class="btn sm gho">Delete</button></div>
      </div>
    </div>
    <div class="card"><div class="ctitle">Shortcuts</div><div class="csub">Fast annotation controls</div>
      <div class="grid2" style="margin-top:6px">
        <div><div class="sc"><span class="kbd">Drag</span>draw arrow</div><div class="sc"><span class="kbd">Click</span>draw circle</div><div class="sc"><span class="kbd">Shift+Drag</span>straight line</div></div>
        <div><div class="sc"><span class="kbd">Del</span>remove selected</div><div class="sc"><span class="kbd">⌘Z</span>undo</div><div class="sc"><span class="kbd">G</span>group selected</div></div>
      </div>
    </div>
    <div class="note ok">Export preview is up to date. Diagrams use the same board theme as Publish.</div>
    """
    acts = ['<button class="btn pri">Save PNG ▾</button>', btn('Copy image'), btn('Clear','dng')]
    return shell('Draw','Board / Draw','Annotation mode · 3 objects · Export ready · Current board', acts,
                 bc, rail)

# ================= ANALYZE (light old-rail, improved) =================
def analyze():
    board = evalbar(0.5,'0.0') + board_html('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR')
    et = ''.join(f'<div class="et {"on" if n=="Board" else ""}">{n}{" <span class=x>×</span>" if n=="Board" else ""}</div>'
                 for n in ['Dashboard','Board','Run','Datasets','Publish','Engine','Console','Logs'])
    modes = ''.join(f'<div class="m {"on" if m=="Analyze" else ""}">{m}</div>' for m in ['Analyze','Play','Solve','Relations','Draw'])
    html_doc = f"""<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head>
<body class="light">
<div class="menubar"><span class="dots"><i class="r"></i><i class="y"></i><i class="g"></i></span>
  <span>File</span><span>Edit</span><span>Selection</span><span>View</span><span>Go</span><span>Run</span><span>Terminal</span><span>Help</span>
  <span class="ttl">ChessRTK Workbench</span><span class="pal"><span>⌘K</span><span>Command Palette</span></span></div>
<div class="ed-tabs">{et}<span class="plus">+</span></div>
<div class="amain">
  <div class="aboard">
    <div class="col" style="gap:14px; flex:1">
      <div class="modewrap" style="padding:0"><div class="modebar">{modes}</div></div>
      <div class="boardwrap">{board}</div>
      <div class="boardfoot" style="max-width:610px"><div><div class="who">White to move</div><div class="det">normal · 20 legal moves · material even</div></div>
        <div class="ctl"><button class="btn xs gho">Flip</button><button class="btn xs gho">Reset</button></div></div>
    </div>
  </div>
  <div class="arail">
    <div class="asec"><div class="h">Position</div>
      <div class="fencard"><div class="f">rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1</div>
        <div class="meta"><span class="badge ok"><span class="d"></span>Valid</span> White to move · 20 legal</div>
        <div class="iconrow" style="margin-top:10px"><button class="btn xs gho">Copy</button><button class="btn xs gho">Edit</button><button class="btn xs gho">PGN database…</button></div>
      </div>
      <div class="iconrow" style="margin-top:10px">
        <div class="icbtn" title="First">⏮</div><div class="icbtn" title="Prev">‹</div><div class="icbtn" title="Next">›</div><div class="icbtn" title="Last">⏭</div><div class="icbtn" title="Reset">↻</div>
      </div>
    </div>
    <div class="asec"><div class="h">Tools</div>
      <div class="iconrow"><button class="btn xs gho">Opening tree</button><button class="btn xs gho">Review</button><button class="btn xs gho">Endgame</button><button class="btn xs gho">Study</button><button class="btn xs gho">Gauntlet</button></div>
    </div>
    <div class="asec"><div class="row"><div class="h" style="margin:0">Analysis</div><span class="sp"></span><span class="muted" style="font-size:11px; margin-right:7px">Live</span><span class="tog on"><i></i></span></div>
      <div class="frow" style="margin-top:10px"><div class="k">Depth</div><div class="v"><span class="badge neutral">4</span></div><div class="k" style="margin-left:12px">Time</div><div class="v"><span class="badge neutral">1s</span></div></div>
      <div class="row" style="gap:8px; margin-top:12px"><button class="btn sm pri" style="flex:1">Analyze</button><button class="btn sm gho">Search</button><button class="btn sm gho">Best</button></div>
    </div>
    <div class="asec"><div class="atabs"><div class="at on">Analysis</div><div class="at">Opening</div><div class="at">Data</div><div class="at">Editor</div></div>
      <div class="empty"><div class="t">No analysis yet</div><div>Run analysis to chart evaluation over time.</div><button class="btn sm pri" style="margin-top:4px">Analyze position</button></div>
    </div>
  </div>
</div>
</body></html>"""
    return html_doc

pages = {'play':play(),'solve':solve(),'relations':relations(),'draw':draw(),'analyze':analyze()}
for name, doc in pages.items():
    with open(os.path.join(OUT, f'{name}.html'),'w') as f:
        f.write(doc)
print("wrote", ", ".join(pages.keys()))

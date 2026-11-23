#!/usr/bin/env python3
"""Genera el tutorial narrado de SICA a partir de capturas JavaFX reales.
Requiere macOS (say), Pillow, imageio-ffmpeg y capturas en docs/tutorial/capturas.
"""
from pathlib import Path
import argparse, json, math, re, subprocess, textwrap, shutil
from PIL import Image, ImageDraw, ImageFont, ImageOps
import imageio_ffmpeg

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'entregables' / 'tutorial'
WORK = ROOT / 'target' / 'tutorial-video'
FF = imageio_ffmpeg.get_ffmpeg_exe()
FONT = '/System/Library/Fonts/Supplemental/Arial.ttf'
BOLD = '/System/Library/Fonts/Supplemental/Arial Bold.ttf'
BG, INK, MUTED, TEAL = '#F1F6F9', '#142E3D', '#577080', '#087F7A'
W,H = 1920,1080

def run(args, **kw):
    return subprocess.run(args, check=True, **kw)

def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size)

def wrap_pixels(draw, value, face, width):
    lines=[]
    for paragraph in value.split('\n'):
        line=''
        for word in paragraph.split():
            candidate=(line+' '+word).strip()
            if draw.textlength(candidate,font=face)>width and line:
                lines.append(line);line=word
            else:line=candidate
        lines.append(line)
    return lines

def multiline(draw, value, xy, size=30, width=450, color=INK, bold=False, spacing=1.3):
    face=font(size,bold);x,y=xy
    for line in wrap_pixels(draw,value,face,width):
        draw.text((x,y),line,font=face,fill=color)
        y+=round(size*spacing)
    return y

def card(draw, box, title, body, color=TEAL):
    x0,y0,x1,y1=box
    draw.rounded_rectangle(box, radius=24, fill='white', outline='#D9E4EA', width=2)
    draw.rounded_rectangle((x0,y0,x0+9,y1),radius=4,fill=color)
    y=multiline(draw,title,(x0+28,y0+25),size=32,width=x1-x0-50,bold=True,color=color)
    multiline(draw,body,(x0+28,y+14),size=25,width=x1-x0-52,color=MUTED)

def diagram(draw, kind):
    x=650;y=210;w=1190
    if kind=='roles':
        items=[('Superusuario','Usuarios, empresas y administración completa.'),('Supervisor','Personas, accesos, incidentes, reportes y auditoría.'),('Guarda','Invitados, solicitudes, entradas, salidas e incidentes.'),('Funcionario','Prerregistro, decisiones y reportes de su empresa.')]
        for i,(title,body) in enumerate(items):
            cx=x+(i%2)*595;cy=y+(i//2)*285
            card(draw,(cx,cy,cx+565,cy+250),title,body)
    elif kind=='forgotten':
        for i,(title,body) in enumerate([('1 · Visita anterior','DENTRO → SALIDA_OLVIDADA\nSe registra su salida.'),('2 · Nueva autorización','APROBADA → DENTRO\nDebe estar vigente y habilitada.'),('3 · Una transacción','Regularización + ingreso + auditoría.\nSolo queda una visita abierta.')]):
            cy=y+i*210;card(draw,(x,cy,x+w,cy+182),title,body)
    elif kind=='architecture':
        for i,(title,body) in enumerate([('Presentación · JavaFX','Pantallas, formularios, controles y mensajes.'),('Aplicación y dominio','Sesión, permisos, empresa, horarios y reglas de acceso.'),('Infraestructura · JDBC y MySQL','Persistencia, transacciones, notificaciones y auditoría.')]):
            cy=y+i*210;card(draw,(x,cy,x+w,cy+178),title,body)
            if i<2:draw.polygon([(1220,cy+183),(1250,cy+183),(1235,cy+201)],fill=TEAL)
    elif kind=='setup':
        for i,(title,body) in enumerate([('1 · Inicia la base de datos','docker compose up -d --wait'),('2 · Abre la aplicación','SICA.app   o   ./scripts/sica'),('3 · Comprueba tu cuenta','Usuario activo, rol asignado y conexión compartida.')]):
            cy=y+i*210;card(draw,(x,cy,x+w,cy+178),title,body)
    elif kind=='help':
        for i,(title,body) in enumerate([('Ingreso denegado','Aprobación, horario, persona y empresas habilitadas.'),('Permiso insuficiente','El rol y la empresa se validan en cada operación.'),('Sin conexión','Servicio MySQL, red y configuración local.')]):
            cy=y+i*210;card(draw,(x,cy,x+w,cy+178),title,body)
    elif kind=='summary':
        for i,(title,body) in enumerate([('Preparación','Empresa → usuarios → personas'),('Operación','Solicitud → decisión → entrada → salida'),('Seguimiento','Notificaciones → incidentes → auditoría → reportes')]):
            cy=y+i*210;card(draw,(x,cy,x+w,cy+178),title,body)

def frame(scene, index, total):
    im=Image.new('RGB',(W,H),BG);d=ImageDraw.Draw(im)
    d.rectangle((0,0,W,12),fill=TEAL)
    d.text((55,36),'SICA / VIDEOTUTORIAL',font=font(22,True),fill=TEAL)
    d.text((55,77),scene['title'],font=font(49,True),fill=INK)
    d.rounded_rectangle((55,181,545,225),radius=18,fill='#DCEFEB')
    d.text((76,191),scene['section'].upper(),font=font(19,True),fill=TEAL)
    y=276
    for i,step in enumerate(scene['steps'],1):
        d.ellipse((60,y,100,y+40),fill=TEAL)
        d.text((73,y+7),str(i),font=font(22,True),fill='white')
        y=multiline(d,step,(120,y),size=30,width=405,bold=True)+50
    d.text((60,860),f'{index+1:02d} / {total:02d}',font=font(31,True),fill=TEAL)
    d.text((60,910),'CAPTURAS REALES · DATOS DE PRUEBA',font=font(17),fill=MUTED)
    if 'image' in scene:
        screenshot=Image.open(ROOT/'docs/tutorial/capturas'/scene['image']).convert('RGB')
        # Los formularios se amplían para poder leer sus campos.
        limit=(1260,775)
        ratio=min(limit[0]/screenshot.width,limit[1]/screenshot.height)
        screenshot=screenshot.resize((round(screenshot.width*ratio),round(screenshot.height*ratio)),Image.Resampling.LANCZOS)
        x=600+(1280-screenshot.width)//2;y=151+(780-screenshot.height)//2
        d.rounded_rectangle((x-9,y-9,x+screenshot.width+9,y+screenshot.height+9),radius=15,fill='#D7E2E9')
        im.paste(screenshot,(x,y))
    else:diagram(d,scene['diagram'])
    d.rectangle((0,955,W,H),fill='#0D2534')
    return im

def duration(path):
    result=subprocess.run([FF,'-hide_banner','-i',str(path)],capture_output=True,text=True)
    match=re.search(r'Duration: (\d+):(\d+):(\d+\.\d+)',result.stderr)
    if not match:raise RuntimeError(result.stderr)
    h,m,s=map(float,match.groups());return h*3600+m*60+s

def stamp(t):
    ms=round(t*1000);h,ms=divmod(ms,3600000);m,ms=divmod(ms,60000);s,ms=divmod(ms,1000)
    return f'{h:02d}:{m:02d}:{s:02d},{ms:03d}'

def caption_chunks(speech):
    pieces=re.split(r'(?<=[.!?])\s+',speech)
    chunks=[]
    for piece in pieces:
        if len(piece)<=140:chunks.append(piece)
        else:
            current=''
            for word in piece.split():
                if len(current+' '+word)>138:
                    chunks.append(current);current=word
                else:current=(current+' '+word).strip()
            if current:chunks.append(current)
    return chunks

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--preview',action='store_true');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True);WORK.mkdir(parents=True,exist_ok=True)
    scenes=json.loads((ROOT/'docs/tutorial/guion.json').read_text())
    for i,scene in enumerate(scenes):frame(scene,i,len(scenes)).save(WORK/f'{i:02d}.png')
    if args.preview:
        frame(scenes[0],0,len(scenes)).save(OUT/'portada.png');print(WORK);return
    timeline=[];global_subs=[];clock=0
    for i,scene in enumerate(scenes):
        base=WORK/f'{i:02d}';text=base.with_suffix('.txt');audio=base.with_suffix('.aiff');movie=base.with_suffix('.mp4')
        text.write_text(scene['speech'])
        if not audio.exists():run(['say','-v','Paulina','-r','155','-f',str(text),'-o',str(audio)])
        spoken=duration(audio);length=math.ceil((spoken+.85)*12)/12
        chunks=caption_chunks(scene['speech']);weight=sum(len(c.split()) for c in chunks);cursor=.05;subs=[]
        for chunk in chunks:
            end=cursor+(spoken-.1)*len(chunk.split())/weight
            subs.append(f'{len(subs)+1}\n{stamp(cursor)} --> {stamp(end)}\n{chunk}\n')
            global_subs.append(f'{len(global_subs)+1}\n{stamp(clock+cursor)} --> {stamp(clock+end)}\n{chunk}\n')
            cursor=end
        local=base.with_suffix('.srt');local.write_text('\n'.join(subs))
        subtitle_path=str(local).replace('\\','\\\\').replace(':','\\:').replace("'","\\'")
        vf=f"subtitles=filename='{subtitle_path}':force_style='FontName=Arial,FontSize=10,PrimaryColour=&H00FFFFFF,Outline=0,Shadow=0,Alignment=2,MarginV=10,MarginL=40,MarginR=40',fade=t=in:st=0:d=0.25,fade=t=out:st={length-.25:.3f}:d=0.25"
        if not movie.exists():
            with base.with_suffix('.log').open('w') as log:
                run([FF,'-y','-hide_banner','-loglevel','warning','-loop','1','-framerate','12','-i',str(base.with_suffix('.png')),'-i',str(audio),'-t',f'{length:.6f}','-vf',vf,'-af','apad,loudnorm=I=-16:TP=-1.5:LRA=9','-c:v','libx264','-preset','veryfast','-threads','2','-crf','22','-tune','stillimage','-pix_fmt','yuv420p','-c:a','aac','-b:a','128k','-ar','48000','-movflags','+faststart',str(movie)],stdout=log,stderr=log)
        timeline.append({'start':round(clock,3),'end':round(clock+length,3),'title':scene['title'],'file':str(movie)})
        clock+=length
        print(f'{i+1}/{len(scenes)} · {scene["title"]} · {length:.1f}s',flush=True)
    srt=OUT/'SICA-videotutorial.srt';srt.write_text('\n'.join(global_subs))
    concat=WORK/'concat.txt';concat.write_text('\n'.join("file '"+x['file'].replace("'","'\\''")+"'" for x in timeline))
    metadata=WORK/'chapters.ffmeta'
    metadata.write_text(';FFMETADATA1\ntitle=Videotutorial completo de SICA\nartist=SICA · Zona Acme\ncomment=Capturas reales con narración sintética en español y datos de prueba.\n'+''.join('[CHAPTER]\nTIMEBASE=1/1000\nSTART='+str(round(x['start']*1000))+'\nEND='+str(round(x['end']*1000))+'\ntitle='+x['title']+'\n' for x in timeline))
    result=OUT/'SICA-videotutorial.mp4'
    with (WORK/'final.log').open('w') as log:
        run([FF,'-y','-hide_banner','-loglevel','warning','-f','concat','-safe','0','-i',str(concat),'-f','ffmetadata','-i',str(metadata),'-map','0:v','-map','0:a','-map_metadata','1','-map_chapters','1','-c','copy','-movflags','+faststart',str(result)],stdout=log,stderr=log)
    (OUT/'capitulos.json').write_text(json.dumps([{k:v for k,v in item.items() if k != 'file'} for item in timeline],ensure_ascii=False,indent=2))
    lines=['# Videotutorial de SICA','','Video narrado en español con capturas reales, subtítulos integrados y capítulos.','',f'Duración aproximada: {int(clock//60)} min {round(clock%60)} s.','','## Capítulos','']
    for item in timeline:
        seconds=round(item['start']);lines.append(f'- {seconds//60:02d}:{seconds%60:02d} · {item["title"]}')
    lines+=['','La demostración utiliza datos de prueba. La voz es sintética (Paulina, macOS).','Los subtítulos también están disponibles en SICA-videotutorial.srt.','']
    (OUT/'LEEME.md').write_text('\n'.join(lines))
    transcript=['# Guion del videotutorial de SICA','']
    for scene in scenes:transcript += ['## '+scene['title'],'',scene['speech'],'']
    (OUT/'Transcripcion.md').write_text('\n'.join(transcript))
    frame(scenes[0],0,len(scenes)).save(OUT/'portada.png')
    print(f'FINAL · {result} · {clock:.1f}s',flush=True)

if __name__=='__main__':main()

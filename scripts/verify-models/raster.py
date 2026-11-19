"""Render the Blockbench quads recorded by the client, using the round-two rasterizer."""
from functools import cache
from math import radians, sin, cos, tan

import numpy as np
from PIL import Image

from plan import ASSETS, read_json

width, height = 448, 288

sides = {
    'east': ((1,1,0),(1,1,1),(1,0,1),(1,0,0)),
    'west': ((0,1,1),(0,1,0),(0,0,0),(0,0,1)),
    'north': ((0,1,0),(1,1,0),(1,0,0),(0,0,0)),
    'south': ((1,1,1),(0,1,1),(0,0,1),(1,0,1)),
    'up': ((0,1,1),(1,1,1),(1,1,0),(0,1,0)),
    'down': ((0,0,0),(1,0,0),(1,0,1),(0,0,1)),
}

def box(low, high, color):
    bounds = np.array([low, high])
    for corners in sides.values():
        xyz = bounds[np.asarray(corners),np.arange(3)]
        yield xyz, np.zeros((4, 2)), 'CUTOUT_NO_CULL', np.array([[color]])

def mannequin():
    yield from box((-4.5,24,-4.5),(4.5,32.5,4.5),[184,156,93,255])
    for x in (-2.8, 1.2):
        yield from box((x,26.4,-4.62),(x+1.4,27.6,-4.51),[55,64,71,255])
    yield from box((-1,24.9,-4.60),(1,25.65,-4.51),[103,73,49,255])
    yield from box((-4.25,12,-2.25),(4.25,24,2.25),[54,32,58,255])
    for x in (-6.25,6.25):
        yield from box((x-2,12,-2.2),(x+2,24,2.2),[65,40,76,255])
    for x in (-2.02,2.02):
        yield from box((x-2,0,-2),(x+2,12,2),[49,38,51,255])

@cache
def texture(path, strip_width, strip_height):
    path = ASSETS / path.removeprefix("dynamic/blockbench/")
    with Image.open(path) as loaded:
        image = loaded.convert("RGBA")
    if strip_width:
        if image.size != (strip_width, strip_height):
            raise ValueError(f"Unexpected strip dimensions in {path}")
    elif path.with_suffix(path.suffix + ".mcmeta").is_file():
        animation = read_json(path.with_suffix(path.suffix + ".mcmeta"))["animation"]
        frame_width = animation.get("width", image.width)
        frame_height = animation.get("height", frame_width)
        frame = animation.get("frames", [0])[0]
        index = frame["index"] if isinstance(frame, dict) else frame
        columns = image.width // frame_width
        x, y = index % columns * frame_width, index // columns * frame_height
        image = image.crop((x, y, x + frame_width, y + frame_height))
    return np.asarray(image)


def read_emission(path):
    models = {}
    vertices = []
    for line in path.read_text(encoding="utf-8").splitlines():
        kind, *args = line.split()
        if kind == "M":
            model_id = args[0] if ":" in args[0] else "quarkos:" + args[0]
            batch = models.setdefault(model_id, [])
        elif kind == "T":
            mode, path, w, h = args
            image = texture(path, int(w), int(h))
        elif kind == "V":
            vertices.append([float(n) for n in args[:5]])
            if len(vertices) == 4:
                quad = np.asarray(vertices)
                batch.append((quad[:, :3], quad[:, 3:5], mode, image))
                vertices.clear()
    if vertices:
        raise ValueError("Incomplete quad in renderer export")
    return models


def camera_for(yaw, head):
    yaw = radians(yaw)
    pitch = radians(5 if head else 0)
    dist = 16 * (3.4 if head else 2.95)
    eye_height = 16 * (1.62 - (0.23 if head else 0.42) + (0.18 if head else 0))
    eye = np.array([sin(yaw) * dist, eye_height, -cos(yaw) * dist])
    forward = np.array([-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch)])
    right = np.array([cos(yaw), 0, sin(yaw)])
    return eye, np.array([right, np.cross(forward, right), forward]), bool(abs(yaw) > radians(90))


def render(quads, camera):
    """Rasterize the recorded quads with texture alpha and a depth buffer."""
    canvas = np.broadcast_to(np.array([142.,180.,239.]), (height, width, 3)).copy()
    depth = np.full((height, width), np.inf)
    eye, view, _ = camera
    focal = height/2/tan(radians(27))
    # Each triangle uses the depth buffer for opaque fragments and per-pixel alpha for transparent ones.
    def raster(data, translucent=False):
        xyz, uv, mode, tex = data
        cam = (xyz - eye) @ view.T
        points = np.column_stack([width/2 + focal*cam[:,0]/cam[:,2], height/2 - focal*cam[:,1]/cam[:,2]])
        normal = np.cross(xyz[1]-xyz[0], xyz[2]-xyz[0])
        norm = np.linalg.norm(normal)
        if norm < 1e-6:
            return
        normal /= norm
        if mode in ('CUTOUT', 'TRANSLUCENT_CULL') and np.dot(normal, xyz.mean(axis=0)-eye) > 0:
            return
        shade = 1.0 if mode in ('EMISSIVE','GLOW') else .72 + .28*abs(np.dot(normal, np.array([.35,.81,-.46])))
        for tri in ((0,1,2),(0,2,3)):
            tri = list(tri)
            (x0,y0),(x1,y1),(x2,y2) = points[tri]
            min_x, min_y = np.floor(points[tri].min(0)).astype(int)
            max_x, max_y = np.ceil(points[tri].max(0)).astype(int)
            min_x, min_y, max_x, max_y = max(0,min_x), max(0,min_y), min(width-1,max_x), min(height-1,max_y)
            if max_x < min_x or max_y < min_y:
                continue
            ys,xs = np.mgrid[min_y:max_y+1,min_x:max_x+1]
            x,y = xs+.5,ys+.5
            den = (y1-y2)*(x0-x2)+(x2-x1)*(y0-y2)
            if abs(den)<1e-8:
                continue
            a = ((y1-y2)*(x-x2)+(x2-x1)*(y-y2))/den
            b = ((y2-y0)*(x-x2)+(x0-x2)*(y-y2))/den
            c = 1-a-b
            inside = (a>=0)&(b>=0)&(c>=0)
            a0, b0, c0 = a/cam[tri[0],2], b/cam[tri[1],2], c/cam[tri[2],2]
            z = 1/(a0+b0+c0)
            inside &= z < depth[ys,xs] + 1e-5
            if not inside.any():
                continue
            uv_grid = (a0[inside,None]*uv[tri[0]] + b0[inside,None]*uv[tri[1]] + c0[inside,None]*uv[tri[2]]) * z[inside,None]
            xuv = np.floor((uv_grid[:,0]%1)*tex.shape[1]).astype(int)
            yuv = np.floor((uv_grid[:,1]%1)*tex.shape[0]).astype(int)
            texels = tex[yuv,xuv]
            mask = texels[:,3] >= (2 if translucent else 26)
            ys, xs = ys[inside][mask], xs[inside][mask]
            rgb = texels[mask,:3]*shade
            if translucent:
                alpha = texels[mask,3:4]/255.
                canvas[ys,xs] = rgb*alpha + canvas[ys,xs]*(1-alpha)
            else:
                canvas[ys,xs] = rgb
                depth[ys,xs] = z[inside][mask]
    transparent = []
    for quad in quads:
        if quad[2].startswith(('TRANSLUCENT','EMISSIVE','GLOW')):
            transparent.append(quad)
        else:
            raster(quad)
    transparent.sort(key=lambda q: np.dot(q[0].mean(0)-eye, view[2]), reverse=True)
    for quad in transparent:
        raster(quad, True)
    return canvas.astype(np.uint8)

import json, random
import sys
OUT = sys.argv[1]
from shapely.geometry import box, MultiPoint, mapping, Polygon
from shapely.ops import voronoi_diagram
from pyproj import Transformer
random.seed(3)
tr = Transformer.from_crs(4326, 32635, always_xy=True)
x0, y0 = tr.transform(30.20, 54.47)   # северо-западный угол
W, H = 1000, 1000
kvf, vdf = [], []
for kv in range(1, 201):
    r, c = divmod(kv - 1, 20)
    minx = x0 + c * W; maxy = y0 - r * H
    b = box(minx, maxy - H, minx + W, maxy)
    kvf.append({"type": "Feature", "properties": {"num_lch": 5, "num_kv": kv}, "geometry": mapping(b)})
    n = random.randint(14, 30) if kv not in (2, 44) else 40
    pts = MultiPoint([(random.uniform(minx, minx + W), random.uniform(maxy - H, maxy)) for _ in range(n)])
    cells = sorted([g.intersection(b) for g in voronoi_diagram(pts, envelope=b).geoms], key=lambda g: (-g.centroid.y, g.centroid.x))
    for i, g in enumerate(cells, 1):
        vdf.append({"type": "Feature", "properties": {"num_lch": 5, "num_kv": kv, "num_vd": i}, "geometry": mapping(g)})
crs = {"type": "name", "properties": {"name": "urn:ogc:def:crs:EPSG::32635"}}
json.dump({"type": "FeatureCollection", "crs": crs, "features": kvf}, open("" + OUT + "/map_kvartala.geojson", "w"))
json.dump({"type": "FeatureCollection", "crs": crs, "features": vdf}, open("" + OUT + "/map_vydela.geojson", "w"))
print(len(kvf), len(vdf))

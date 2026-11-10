#!/usr/bin/env python3
"""fetch.py로 받은 지자체별 CSV를 앱용 좌표 목록(app/src/main/assets/school_zones.csv)으로 줄인다.

    python3 -I tools/school-zones/build.py <받은 폴더> app/src/main/assets/school_zones.csv

- 위도·경도 열만 쓴다(시설 이름, 주소는 넣지 않는다).
- 한국 범위 밖 좌표는 버리고, 위도·경도가 뒤바뀐 줄은 바로잡는다.
- 지정여부가 N(지정 해제)인 줄은 버린다.
- 소수 넷째 자리(약 10m)까지 같은 좌표는 하나로 합친다(같은 건물의 학교·유치원 등).
"""
import csv
import datetime
import glob
import io
import os
import sys

LAT = (33.0, 39.0)
LON = (124.0, 132.0)


def read(path):
    raw = open(path, "rb").read()
    for enc in ("utf-8-sig", "cp949"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            pass
    return None


def column(header, *names):
    for i, h in enumerate(header):
        if h.strip().replace(" ", "") in names:
            return i
    return None


def inside(lat, lon):
    return LAT[0] <= lat <= LAT[1] and LON[0] <= lon <= LON[1]


def main():
    src, out = sys.argv[1], sys.argv[2]
    points = set()
    stats = {"files": 0, "rows": 0, "swapped": 0, "outside": 0, "unparsed": 0, "cancelled": 0, "bad_files": []}
    for path in sorted(glob.glob(os.path.join(src, "*.csv"))):
        text = read(path)
        if text is None:
            stats["bad_files"].append(os.path.basename(path))
            continue
        rows = list(csv.reader(io.StringIO(text)))
        if not rows:
            continue
        header = rows[0]
        lat_i = column(header, "위도")
        lon_i = column(header, "경도")
        designated_i = column(header, "어린이보호구역지정여부")
        if lat_i is None or lon_i is None:
            stats["bad_files"].append(os.path.basename(path))
            continue
        stats["files"] += 1
        for row in rows[1:]:
            if len(row) <= max(lat_i, lon_i):
                continue
            stats["rows"] += 1
            if designated_i is not None and len(row) > designated_i and row[designated_i].strip().upper() == "N":
                stats["cancelled"] += 1
                continue
            try:
                lat, lon = float(row[lat_i]), float(row[lon_i])
            except ValueError:
                stats["unparsed"] += 1
                continue
            if not inside(lat, lon) and inside(lon, lat):
                lat, lon = lon, lat
                stats["swapped"] += 1
            if not inside(lat, lon):
                stats["outside"] += 1
                continue
            points.add((round(lat, 4), round(lon, 4)))
    today = datetime.date.today().isoformat()
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("# 어린이보호구역 대상 시설 좌표. 출처: 공공데이터포털 전국어린이보호구역표준데이터(15012891), 지자체별 파일\n")
        f.write(f"# 받은 날: {today}, 파일 {stats['files']}개, {len(points)}곳. tools/school-zones/build.py로 만든다\n")
        f.write("lat,lon\n")
        for lat, lon in sorted(points):
            f.write(f"{lat:.4f},{lon:.4f}\n")
    stats["points"] = len(points)
    print(stats)


if __name__ == "__main__":
    main()

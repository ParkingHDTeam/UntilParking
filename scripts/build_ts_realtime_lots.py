#!/usr/bin/env python3
"""교통안전공단 주차정보(data.go.kr 15099883)에서 실시간을 주는 주차장의 이름·좌표 표를 만든다 (실시간 잔여 스펙 V1).

실시간 응답에는 ID와 면수만 있어 짝을 맞추려면 시설정보의 이름·좌표가 필요하다. 시설정보는 전국 170만 건이라
앱이 받을 수 없으므로, 실시간 ID에 해당하는 행만 여기서 뽑아 data:parking 리소스로 넣는다.
실시간 목록이 바뀌면 다시 돌린다. 사용: python3 scripts/build_ts_realtime_lots.py (저장소 루트, local.properties의 parking.serviceKey)
"""
import json
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data/parking/src/main/resources/ts-realtime-lots.json"
BASE = "https://apis.data.go.kr/B553881/Parking_v1"
# 시도 코드. 12 = 전남광주통합특별시 (2026-11-14 응답으로 확인)
SIDO_CODES = ["11", "12", "26", "27", "28", "29", "30", "31", "36", "41", "42", "43", "44", "45", "46", "47", "48", "50", "51", "52"]
PAGE = 10000
# 표준데이터 주소에 남아 있는 옛 시도 이름(2026-11-14 표준데이터 18,994건 대조). 시설 주소가 이것으로 시작해도 그 시도다
OLD_NAMES = {"12": ["광주광역시", "전라남도"], "51": ["강원도"], "52": ["전라북도"]}


def service_key() -> str:
    for line in (ROOT / "local.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("parking.serviceKey="):
            return line.split("=", 1)[1].strip()
    sys.exit("local.properties에 parking.serviceKey가 없다")


def get(operation: str, key: str, **params) -> dict:
    query = urllib.parse.urlencode({"serviceKey": key, "format": "2", **params})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(f"{BASE}/{operation}?{query}", timeout=120) as response:
                body = json.load(response)
            if body.get("resultCode") not in ("0", "00"):
                raise IOError(body.get("resultMsg"))
            return body
        except Exception as error:  # 네트워크·형식 오류는 다시 시도한다
            if attempt == 3:
                raise
            print(f"재시도 {operation} {params}: {error}", file=sys.stderr)
            time.sleep(3 * (attempt + 1))


def rows(body: dict, field: str) -> list:
    return body.get(field) or []


def main() -> None:
    key = service_key()
    realtime_by_sido = {}
    for code in SIDO_CODES:
        found = rows(get("PrkRealtimeInfo_v1", key, pageNo=1, numOfRows=PAGE, sidoCd=code), "PrkRealtimeInfo")
        if found:
            realtime_by_sido[code] = {row["prk_center_id"] for row in found}
    wanted = {i: code for code, ids in realtime_by_sido.items() for i in ids}
    print(f"실시간 ID {len(wanted)}곳, 시도 {sorted(realtime_by_sido)}", file=sys.stderr)

    pages = []
    for code in realtime_by_sido:
        total = int(get("PrkSttusInfo_v1", key, pageNo=1, numOfRows=1, sidoCd=code)["totalCount"])
        pages += [(code, page) for page in range(1, total // PAGE + 2)]

    def scan(task):
        code, page = task
        found = rows(get("PrkSttusInfo_v1", key, pageNo=page, numOfRows=PAGE, sidoCd=code), "PrkSttusInfo")
        return [row for row in found if wanted.get(row.get("prk_center_id")) == code]

    with ThreadPoolExecutor(max_workers=6) as pool:
        matched = [row for part in pool.map(scan, pages) for row in part]
    print(f"시설정보 {len(pages)}쪽에서 {len(matched)}곳을 찾음", file=sys.stderr)

    sidos = {}
    for row in sorted(matched, key=lambda r: r["prk_center_id"]):
        try:
            lat, lon = float(row["prk_plce_entrc_la"]), float(row["prk_plce_entrc_lo"])
        except (KeyError, TypeError, ValueError):
            continue
        code = wanted[row["prk_center_id"]]
        entry = sidos.setdefault(code, {"code": code, "names": set(), "lots": []})
        entry["names"].add(row.get("prk_plce_adres_sido") or "")
        entry["lots"].append([row["prk_center_id"], row.get("prk_plce_nm") or "", round(lat, 6), round(lon, 6)])

    result = {
        "generatedOn": date.today().isoformat(),
        "sidos": [{**entry, "names": sorted({n for n in entry["names"] if n} | set(OLD_NAMES.get(entry["code"], [])))} for entry in sorted(sidos.values(), key=lambda e: e["code"])],
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"{OUT.relative_to(ROOT)}: {sum(len(e['lots']) for e in result['sidos'])}곳", file=sys.stderr)


if __name__ == "__main__":
    main()

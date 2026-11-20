"""
Report performance test: runs every request of http/reports.http against a running app.

  python perf/bench.py                                  # http://localhost:8080, 5 timed runs each
  python perf/bench.py --base http://localhost:8082 --runs 10
  python perf/bench.py --no-total                       # without the COUNT(*) query for "total"
  python perf/bench.py --explain 500                    # EXPLAIN ANALYZE every report slower than 500 ms
  python perf/bench.py --concurrency 16 --duration 60   # load test: 16 parallel users, mixed reports
  python perf/bench.py --concurrency 16 --only 2,5,6,8   # load test with selected reports (numbers as printed)

Timings are end to end (HTTP + JSON + database), first page incl. "total", after one warm-up run.
EXPLAIN plans (perf/compose.yaml container) are written to target/perf-plans/.
"""
import argparse, base64, concurrent.futures, json, pathlib, re, statistics, subprocess, threading, time
import urllib.error, urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
NO_TOTAL = False


def parse_http_file(path):
    requests = []
    for block in re.split(r"^###", path.read_text(encoding="utf-8"), flags=re.M):
        lines = block.strip("\n").split("\n")
        request_line = next((l for l in lines[1:] if re.match(r"^(GET|POST|PUT|DELETE) ", l)), None)
        if not request_line:
            continue
        method, url = request_line.split(" ", 1)
        headers, body, in_body = {}, [], False
        for line in lines[lines.index(request_line) + 1:]:
            if in_body:
                body.append(line)
            elif not line.strip():
                in_body = True
            else:
                key, value = (part.strip() for part in line.split(":", 1))
                if key == "Authorization" and value.startswith("Basic ") and " " in value[6:]:
                    value = "Basic " + base64.b64encode(value[6:].replace(" ", ":", 1).encode()).decode()
                headers[key] = value
        title = lines[0].strip()
        expected = int(m.group(1)) if (m := re.search(r"Expect (\d{3})", title)) else 200
        requests.append(dict(title=title, method=method, url=url.strip(), headers=headers,
                             body="\n".join(body).strip() or None, expected=expected))
    return requests


def call(request, base, url=None, body=None, headers=None):
    target = (url or request["url"]).replace("http://localhost:8080", base)
    if NO_TOTAL and "/run" in target:
        target += ("&" if "?" in target else "?") + "includeTotal=false"
    data = body if body is not None else request["body"]
    req = urllib.request.Request(target, data=data.encode() if data else None,
                                 headers=headers or request["headers"], method=request["method"])
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            status, payload = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, payload = error.code, error.read()
    return status, payload, (time.perf_counter() - start) * 1000


def sql_literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def inline_parameters(sql, parameters):
    out, values, quoted = [], iter(parameters), False
    for char in sql:
        if char == "'":
            quoted = not quoted
        out.append(sql_literal(next(values)) if char == "?" and not quoted else char)
    return "".join(out)


def explain(request, base, number):
    admin = {"Content-Type": "application/json",
             "Authorization": "Basic " + base64.b64encode(b"admin:password").decode()}
    status, payload, _ = call(request, base, url="http://localhost:8080/api/reports/preview", headers=admin)
    if status != 200:
        return None
    preview = json.loads(payload)
    page_size = int(m.group(1)) if (m := re.search(r"size=(\d+)", request["url"])) else 200
    sql = inline_parameters(preview["sql"], preview["parameters"]) + f"\nLIMIT {page_size + 1} OFFSET 0"
    plan = subprocess.run(["docker", "exec", "-i", "reports-postgres", "psql", "-U", "reports", "-d", "reports",
                           "-c", "EXPLAIN (ANALYZE, BUFFERS) " + sql], capture_output=True, text=True).stdout
    directory = ROOT / "target" / "perf-plans"
    directory.mkdir(parents=True, exist_ok=True)
    (directory / f"{number:02d}.txt").write_text(f"-- {request['title']}\n{sql}\n\n{plan}", encoding="utf-8")
    execution = re.search(r"Execution Time: ([\d.]+) ms", plan)
    return float(execution.group(1)) if execution else None


def benchmark(requests, args):
    print(f"{'#':>3} {'status':6} {'median':>8} {'min':>7} {'max':>7} {'rows':>5} {'total':>9}  report")
    slow = []
    for number, request in enumerate(requests, 1):
        status, payload, _ = call(request, args.base)               # warm-up
        times = [call(request, args.base)[2] for _ in range(args.runs if status == 200 else 1)]
        rows = total = ""
        try:
            result = json.loads(payload)
            rows, total = len(result.get("rows", [])), result.get("total", "")
        except (ValueError, AttributeError):
            pass
        median = statistics.median(times)
        ok = "OK " if status == request["expected"] else "BAD"
        print(f"{number:>3} {ok}{status} {median:7.0f}ms {min(times):6.0f}ms {max(times):6.0f}ms {rows:>5} {total!s:>9}"
              f"  {request['title'][:70]}", flush=True)
        if args.explain is not None and status == 200 and median >= args.explain and "/run" in request["url"]:
            slow.append((number, request, median))
    for number, request, median in slow:
        database = explain(request, args.base, number)
        print(f"EXPLAIN #{number}: end-to-end {median:.0f} ms, database {database} ms -> target/perf-plans/{number:02d}.txt")


def load_test(requests, args):
    mix = [r for number, r in enumerate(requests, 1)
           if r["expected"] == 200 and "/run" in r["url"] and (not args.only or number in args.only)]
    stop_at = time.monotonic() + args.duration
    latencies, errors, lock = [], [0], threading.Lock()

    def user(offset):
        i = offset
        while time.monotonic() < stop_at:
            status, _, ms = call(mix[i % len(mix)], args.base)
            with lock:
                latencies.append(ms)
                errors[0] += status != 200
            i += 1

    with concurrent.futures.ThreadPoolExecutor(args.concurrency) as pool:
        list(pool.map(user, range(args.concurrency)))
    latencies.sort()
    pct = lambda p: latencies[min(len(latencies) - 1, int(len(latencies) * p))]
    print(f"{args.concurrency} users, {args.duration}s, {len(mix)} different reports: {len(latencies)} requests,"
          f" {len(latencies) / args.duration:.1f} req/s, errors {errors[0]}")
    print(f"latency p50 {pct(0.50):.0f} ms, p95 {pct(0.95):.0f} ms, p99 {pct(0.99):.0f} ms, max {latencies[-1]:.0f} ms")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--file", default=str(ROOT / "http" / "reports.http"))
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--explain", type=float, help="EXPLAIN ANALYZE reports slower than this many ms")
    parser.add_argument("--concurrency", type=int, help="load test with this many parallel users")
    parser.add_argument("--duration", type=int, default=60)
    parser.add_argument("--only", type=lambda text: {int(n) for n in text.split(",")},
                        help="load test only these reports, e.g. 2,5,6")
    parser.add_argument("--no-total", action="store_true", help="run pages with includeTotal=false")
    arguments = parser.parse_args()
    NO_TOTAL = arguments.no_total
    all_requests = parse_http_file(pathlib.Path(arguments.file))
    (load_test if arguments.concurrency else benchmark)(all_requests, arguments)

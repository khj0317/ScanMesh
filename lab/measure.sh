#!/usr/bin/env bash
# 점검 엔진 하나를 실행하면서 실행 시간과 최대 메모리를 잽니다.
# 사용법: ./measure.sh zap | ./measure.sh nuclei
set -u
svc="$1"
name="secuscan-lab-$svc-measure"
docker rm -f "$name" >/dev/null 2>&1
start=$(date +%s)
docker compose --profile scan run -d --name "$name" "$svc" >/dev/null
peak=0
while [ "$(docker inspect -f '{{.State.Running}}' "$name" 2>/dev/null)" = "true" ]; do
  mem=$(docker stats --no-stream --format '{{.MemUsage}}' "$name" 2>/dev/null | awk '{print $1}')
  mib=$(echo "$mem" | awk '/GiB/{sub("GiB","");printf "%d",$1*1024;next} /MiB/{sub("MiB","");printf "%d",$1;next} {print 0}')
  [ "${mib:-0}" -gt "$peak" ] && peak=$mib
  sleep 1
done
end=$(date +%s)
code=$(docker inspect -f '{{.State.ExitCode}}' "$name")
docker logs "$name" > "results/$svc.log" 2>&1
docker rm "$name" >/dev/null
echo "$svc: ${peak}MiB peak, $((end - start))s, exit $code"

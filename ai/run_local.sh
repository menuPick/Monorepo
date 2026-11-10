#!/usr/bin/env bash
# MenuPick AI 서버 로컬 실행. 기본은 그래프 엔진(O(N+E)), AI_ENGINE=llm이면 로컬 LLM(Ollama).
set -euo pipefail
cd "$(dirname "$0")"

AI_PORT=${AI_PORT:-18090}
PY=python3; command -v "$PY" >/dev/null || PY=python

if [ ! -d .venv ]; then
  "$PY" -m venv .venv
  if [ -x .venv/bin/python ]; then VPY=.venv/bin/python; else VPY=.venv/Scripts/python.exe; fi
  "$VPY" -m pip install -q -r requirements.txt
fi
if [ -x .venv/bin/python ]; then VPY=.venv/bin/python; else VPY=.venv/Scripts/python.exe; fi

echo "[INFO] MenuPick AI starting: http://localhost:$AI_PORT"
exec "$VPY" -m uvicorn app.main:app --host 127.0.0.1 --port "$AI_PORT"

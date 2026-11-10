# MenuPick AI 서버 로컬 실행 (PowerShell)
#   .\run_local.ps1                 → http://localhost:18090  (문서: /docs)
#   기본 엔진: 메뉴 그래프 BFS + Aho-Corasick (O(N+E), 즉시 응답)
#   $env:AI_ENGINE='llm'  → 로컬 LLM(Ollama qwen2.5:7b) 사용, 실패 시 그래프 엔진 (10~15초)
#   $env:AI_PORT=9100; .\run_local.ps1
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $env:AI_PORT) { $env:AI_PORT = '18090' }

if (-not (Test-Path '.venv\Scripts\python.exe')) {
    Write-Host '[INFO] 가상환경 생성 (.venv)'
    $python = (Get-Command python -ErrorAction SilentlyContinue).Source
    if (-not $python) { $python = (Get-Command py -ErrorAction SilentlyContinue).Source }
    if (-not $python) {
        $python = Get-ChildItem "$env:LOCALAPPDATA\Programs\Python\Python3*\python.exe" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $python) { throw 'Python 3.10 이상을 설치하세요: https://www.python.org/downloads/' }
    & $python -m venv .venv
    & .\.venv\Scripts\python.exe -m pip install -q -r requirements.txt
}

Write-Host "[INFO] MenuPick AI starting: http://localhost:$($env:AI_PORT)  engine=$(if ($env:AI_ENGINE) { $env:AI_ENGINE } else { 'graph' })"
& .\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port $env:AI_PORT

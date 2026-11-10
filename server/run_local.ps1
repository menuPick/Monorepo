# Windows용 로컬 실행 스크립트 (PowerShell)
#   .\run_local.ps1            → http://localhost:18080
#   $env:PORT=9000; .\run_local.ps1
# 확인 페이지: http://localhost:<PORT>/   (Swagger UI: /swagger-ui.html)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $env:PORT) { $env:PORT = '18080' }
if (-not $env:DATA_DIR) { $env:DATA_DIR = (Join-Path (Resolve-Path '..') 'data') }
# 로컬 개발: 월 1회 추천 제한 끔, 10분 요청 제한 완화, AI 서버(ai/run_local.ps1) 자동 연결.
if (-not $env:MONTHLY_RECOMMENDATION_LIMIT) { $env:MONTHLY_RECOMMENDATION_LIMIT = 'false' }
if (-not $env:RECOMMENDATION_RATE_LIMIT) { $env:RECOMMENDATION_RATE_LIMIT = '100' }
if (-not $env:AI_BASE_URL) { $env:AI_BASE_URL = 'http://127.0.0.1:18090' }
# 로컬 관리자 로그인 ID: test (ADMIN_ID_HASH가 있으면 그쪽이 우선)
if (-not $env:ADMIN_ID) { $env:ADMIN_ID = 'test' }

# JAVA_HOME이 없으면 Android Studio에 포함된 JDK를 사용합니다.
if (-not $env:JAVA_HOME) {
    $jbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path "$jbr\bin\java.exe") { $env:JAVA_HOME = $jbr }
}

Write-Host "[INFO] MenuPick server starting: http://localhost:$($env:PORT)  storage=$($env:DATA_DIR)  monthlyLimit=$($env:MONTHLY_RECOMMENDATION_LIMIT)  ai=$($env:AI_BASE_URL)"
& .\mvnw.cmd -q spring-boot:run

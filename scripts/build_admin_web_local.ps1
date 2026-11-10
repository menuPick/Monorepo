# 관리자 Flutter Web을 빌드해 API 서버가 /admin/ 에서 바로 보여주게 합니다(로컬 개발용).
#   .\scripts\build_admin_web_local.ps1   → build\admin_web  (서버: http://localhost:18080/admin/)
# 사용자 앱의 "관리자 계정 이메일"을 누르면 이 주소가 열립니다.
# web\index.html, web\manifest.json은 빌드 동안만 관리자용으로 바꾸고 원래대로 되돌립니다.
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $root

$flutter = (Get-Command flutter -ErrorAction SilentlyContinue).Source
if (-not $flutter) { $flutter = 'C:\Users\USER\Downloads\flutter\bin\flutter.bat' }

$web = Join-Path $root 'web'
$backupIndex = Get-Content -Raw -Encoding UTF8 (Join-Path $web 'index.html')
$backupManifest = Get-Content -Raw -Encoding UTF8 (Join-Path $web 'manifest.json')
try {
    Copy-Item (Join-Path $web 'index.admin.html') (Join-Path $web 'index.html') -Force
    Copy-Item (Join-Path $web 'manifest.admin.json') (Join-Path $web 'manifest.json') -Force
    # API_BASE_URL을 주지 않으면 관리자 웹은 자신을 내려준 서버(같은 주소)를 API로 씁니다.
    # 성능 옵션:
    #   --wasm                    WebAssembly(skwasm) 렌더러. 지원 안 되는 브라우저는 자동으로 JS로 대체.
    #   --no-web-resources-cdn    CanvasKit 등을 인터넷(gstatic)이 아니라 이 서버에서 받음 → 에뮬레이터 첫 로딩 빠름.
    #   -O4                       최대 최적화.
    & $flutter build web -t lib/admin_main.dart --release --wasm --no-web-resources-cdn -O4 --base-href /admin/ -o (Join-Path $root 'build\admin_web')
    if ($LASTEXITCODE -ne 0) { throw "flutter build web failed ($LASTEXITCODE)" }
} finally {
    [IO.File]::WriteAllText((Join-Path $web 'index.html'), $backupIndex, (New-Object Text.UTF8Encoding($false)))
    [IO.File]::WriteAllText((Join-Path $web 'manifest.json'), $backupManifest, (New-Object Text.UTF8Encoding($false)))
}
Write-Host "[OK] build\admin_web 생성. 서버 실행 후 http://localhost:18080/admin/ (에뮬레이터: http://10.0.2.2:18080/admin/)"

# ============================================================
# 拾光 (Shiguang) 一键启动脚本
# 用法:
#   powershell -File .devtools\start-all.ps1          # 启动全部
#   powershell -File .devtools\start-all.ps1 -Stop    # 停止前后端
# ============================================================
param(
    [switch]$Stop
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$project = Split-Path -Parent $root
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$backendLog  = Join-Path $logDir 'backend.log'
$backendErr  = Join-Path $logDir 'backend.err.log'
$frontendLog = Join-Path $logDir 'frontend.log'
$frontendErr = Join-Path $logDir 'frontend.err.log'
$backendPid  = Join-Path $logDir 'backend.pid'
$frontendPid = Join-Path $logDir 'frontend.pid'

function Write-Step { param([string]$Msg) Write-Host "[拾光] $Msg" -ForegroundColor Cyan }
function Write-Info { param([string]$Msg) Write-Host "[拾光] $Msg" -ForegroundColor Green }
function Write-Warn { param([string]$Msg) Write-Host "[拾光] $Msg" -ForegroundColor Yellow }

# ---------------- 停止模式 ----------------
if ($Stop) {
    # ---------------- 前后端：优先按 pid 文件杀进程树 ----------------
    foreach ($pair in @(@('后端', $backendPid), @('前端', $frontendPid))) {
        $name = $pair[0]
        $pidFile = $pair[1]
        if (Test-Path $pidFile) {
            $procId = (Get-Content $pidFile | Select-Object -First 1).Trim()
            if ($procId) {
                & taskkill.exe /T /F /PID $procId 2>$null | Out-Null
                Write-Step "$name 已停止 (PID $procId)"
            }
            Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
        }
    }

    # 兜底：没记录 pid 时按端口停止
    foreach ($pair in @(@('后端', 8080), @('前端', 5173))) {
        $name = $pair[0]
        $port = $pair[1]
        $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($conn) {
            $conn | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
            Write-Step "$name 已停止 (端口 $port)"
        }
    }

    # ---------------- Redis ----------------
    $redisCli = Join-Path $root 'redis\redis-cli.exe'
    if (Test-Path $redisCli) { & $redisCli shutdown 2>$null | Out-Null }
    Start-Sleep -Milliseconds 800
    if (Get-NetTCPConnection -LocalPort 6379 -State Listen -ErrorAction SilentlyContinue) {
        Stop-Process -Name redis-server -Force -ErrorAction SilentlyContinue
    }
    if (Get-NetTCPConnection -LocalPort 6379 -State Listen -ErrorAction SilentlyContinue) {
        Write-Warn 'Redis 未能停止，请手动关闭 redis-server.exe'
    } else {
        Write-Info 'Redis 已停止'
    }

    # ---------------- MinIO ----------------
    if (Get-Process -Name minio -ErrorAction SilentlyContinue) {
        Stop-Process -Name minio -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 800
    }
    if (Get-NetTCPConnection -LocalPort 9000 -State Listen -ErrorAction SilentlyContinue) {
        Write-Warn 'MinIO 未能停止，请手动关闭 minio.exe'
    } else {
        Write-Info 'MinIO 已停止'
    }

    # ---------------- RabbitMQ (Windows 服务，需管理员) ----------------
    $rmqSvc = Get-Service -Name RabbitMQ -ErrorAction SilentlyContinue
    if ($rmqSvc -and $rmqSvc.Status -eq 'Running') {
        try {
            Stop-Service -Name RabbitMQ -Force -ErrorAction Stop
            Write-Info 'RabbitMQ 服务已停止'
        } catch {
            Write-Warn '停止 RabbitMQ 需要管理员权限：请用管理员 PowerShell 执行 Stop-Service RabbitMQ'
        }
    } else {
        Write-Info 'RabbitMQ 未在运行'
    }
    Write-Step '全部服务已停止。'
    exit
}

Write-Step '========== 拾光一键启动 =========='

# ---------------- 1. 中间件 ----------------
Write-Step '第 1 步: 检查中间件 (Redis / MinIO / RabbitMQ) ...'
& (Join-Path $root 'start-dev.ps1')

$mysql = Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue
if (-not $mysql) {
    Write-Warn '警告: 3306 端口没有 MySQL 在监听，请先启动 MySQL（Docker 或本机服务）。'
}

# ---------------- 2. 前端依赖 ----------------
$frontendDir = Join-Path $project 'frontend'
if (-not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
    Write-Step '第 2 步: 前端依赖未安装，执行 npm install ...'
    Push-Location $frontendDir
    npm install
    Pop-Location
} else {
    Write-Step '第 2 步: 前端依赖已就绪。'
}

# ---------------- 3. 后端 ----------------
Write-Step '第 3 步: 启动后端 (8080) ...'

# 生产参数（OSS / 真实短信）写在 .devtools/oss.env，每行 KEY=VALUE，该目录已 gitignore，不会进仓库
$extraEnv = Join-Path $root 'oss.env'
if (Test-Path $extraEnv) {
    Get-Content $extraEnv | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*\s*=' -and $_ -notmatch '^\s*#' } | ForEach-Object {
        $idx = $_.IndexOf('=')
        $key = $_.Substring(0, $idx).Trim()
        $value = $_.Substring($idx + 1).Trim().Trim('"')
        if ($value) { Set-Item -Path "Env:$key" -Value $value }
    }
    Write-Step "已加载 $extraEnv（对象存储 / 短信等参数）"
}

$ffmpeg = Join-Path $root 'ffmpeg\ffmpeg-9.0.1-essentials_build\bin\ffmpeg.exe'
if (Test-Path $ffmpeg) { $env:FFMPEG_PATH = $ffmpeg } else { $env:FFMPEG_PATH = 'ffmpeg' }

$backendUp = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($backendUp) {
    Write-Info '后端已在运行 (8080)，跳过启动。'
    $backendProc = $null
} else {
    Write-Step "使用 FFMPEG_PATH=$env:FFMPEG_PATH"
    $backendProc = Start-Process -FilePath 'cmd.exe' `
        -ArgumentList @('/c', 'mvnw.cmd -s .devtools\maven-settings-public.xml spring-boot:run') `
        -WorkingDirectory $project -WindowStyle Hidden `
        -RedirectStandardOutput $backendLog -RedirectStandardError $backendErr -PassThru
    $backendProc.Id | Set-Content $backendPid
    Write-Step "后端进程已启动 (PID $($backendProc.Id))，日志: $backendLog"
}

# ---------------- 4. 前端 ----------------
Write-Step '第 4 步: 启动前端 (5173) ...'
$frontendUp = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
if ($frontendUp) {
    Write-Info '前端已在运行 (5173)，跳过启动。'
    $frontendProc = $null
} else {
    $frontendProc = Start-Process -FilePath 'cmd.exe' `
        -ArgumentList @('/c', 'npm run dev') `
        -WorkingDirectory $frontendDir -WindowStyle Hidden `
        -RedirectStandardOutput $frontendLog -RedirectStandardError $frontendErr -PassThru
    $frontendProc.Id | Set-Content $frontendPid
    Write-Step "前端进程已启动 (PID $($frontendProc.Id))，日志: $frontendLog"
}

# ---------------- 5. 等待就绪 ----------------
Write-Step '第 5 步: 等待服务就绪 (最多 180 秒) ...'
$deadline = (Get-Date).AddSeconds(180)
$be = $false
$fe = $false
while ((Get-Date) -lt $deadline) {
    if (-not $be -and (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) {
        $be = $true
        Write-Info '后端就绪: http://localhost:8080/swagger-ui.html'
    }
    if (-not $fe -and (Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue)) {
        $fe = $true
        Write-Info '前端就绪: http://localhost:5173'
    }
    if ($be -and $fe) { break }
    if ($backendProc -and $backendProc.HasExited -and -not $be) {
        Write-Warn "后端进程提前退出，请查看日志: $backendLog"
        break
    }
    if ($frontendProc -and $frontendProc.HasExited -and -not $fe) {
        Write-Warn "前端进程提前退出，请查看日志: $frontendLog"
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $be) { Write-Warn "后端 180 秒内未就绪，日志: $backendLog" }
if (-not $fe) { Write-Warn "前端 180 秒内未就绪，日志: $frontendLog" }

# ---------------- 6. 打开浏览器 ----------------
if ($fe) {
    Write-Step '打开浏览器...'
    Start-Process 'http://localhost:5173'
}

Write-Step '完成！'
Write-Step "停止服务: powershell -File `"$PSCommandPath`" -Stop"

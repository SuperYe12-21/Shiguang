# Shiguang local dev services (Redis / MinIO / RabbitMQ)
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Start-DevService {
    param([string]$Name, [string]$File, [string[]]$Arguments, [string]$CheckPort, [hashtable]$EnvVars = @{})
    if ($CheckPort -and (Get-NetTCPConnection -LocalPort $CheckPort -State Listen -ErrorAction SilentlyContinue)) {
        Write-Host "[$Name] already running (port $CheckPort)" -ForegroundColor Green
        return
    }
    # 兼容 Windows PowerShell 5.1（无 Start-Process -Environment）：子进程会继承当前进程环境变量
    foreach ($key in $EnvVars.Keys) {
        Set-Item -Path "Env:$key" -Value $EnvVars[$key]
    }
    Start-Process -FilePath $File -ArgumentList $Arguments -WindowStyle Hidden
    Write-Host "[$Name] starting..." -ForegroundColor Yellow
}

# Redis 6379
Start-DevService -Name "Redis" -File "$root\redis\redis-server.exe" -Arguments @("$root\redis\redis.windows.conf") -CheckPort 6379

# MinIO 已下线（2026-10-08 起对象存储全部走阿里云 OSS，媒体不落本机）
# 需要临时跑回本地存储时才恢复下面这行，并在 .devtools\oss.env 里把 STORAGE_TYPE 改成 minio：
# Start-DevService -Name "MinIO" -File "$root\minio\minio.exe" -Arguments @("server", "$root\minio-data", "--address", "0.0.0.0:9000", "--console-address", "0.0.0.0:9001") -CheckPort 9000 -EnvVars @{ MINIO_API_REQUESTS_MAX = '10000' }

# RabbitMQ 5672 (start frontend node when Windows service is unavailable)
$rmq = Get-NetTCPConnection -LocalPort 5672 -State Listen -ErrorAction SilentlyContinue
if ($rmq) {
    Write-Host "[RabbitMQ] already running (port 5672)" -ForegroundColor Green
} else {
    $env:ERLANG_HOME = "C:\Program Files\Erlang OTP"
    Start-Process -FilePath "C:\Windows\System32\cmd.exe" -ArgumentList @("/c", "cd /d `"C:\Program Files\RabbitMQ Server\rabbitmq_server-4.3.5\sbin`" && rabbitmq-server.bat") -WindowStyle Hidden
    Write-Host "[RabbitMQ] starting..." -ForegroundColor Yellow
}
Write-Host "done. Tip: set FFMPEG_PATH=$root\ffmpeg\ffmpeg-9.0.1-essentials_build\bin\ffmpeg.exe when starting the backend"

# ---------------------------------------------------------------
#  改完 GitHub 用户名后，把本仓库推到新的地址
#
#  用法（在项目根目录执行）：
#     .\重新上传.ps1 -User 你的新用户名
#     .\重新上传.ps1 -User 新用户名 -Repo SJ-Lens
#
#  前提：已经在 GitHub 上创建了同名空仓库（不要勾选 README / .gitignore）
# ---------------------------------------------------------------
param(
    [string]$User = "Acpicu",
    [string]$Repo = "SJ-Lens",
    [string]$Branch = "main"
)

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

if (-not (Test-Path "gradlew.bat")) {
    Write-Host "[ERROR] 请在项目根目录执行（找不到 gradlew.bat）" -ForegroundColor Red
    exit 1
}

$url = "https://github.com/$User/$Repo.git"
Write-Host "目标仓库: $url" -ForegroundColor Cyan

# 1) 改 remote
$existing = git remote get-url origin 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "原 remote: $existing"
    git remote set-url origin $url
} else {
    git remote add origin $url
}
Write-Host "已切换 remote -> $(git remote get-url origin)" -ForegroundColor Green

# 2) 顺手把提交身份也改成新账号（否则提交在 GitHub 上不会算到你名下）
$noreply = "$User@users.noreply.github.com"
git config user.name $User
git config user.email $noreply
Write-Host "提交身份: $User <$noreply>" -ForegroundColor Green

# 3) 推上去（空仓库首次推送用 -u；历史重传用 --force 也无妨）
Write-Host "正在推送 $Branch ..." -ForegroundColor Cyan
git push -u origin $Branch --force
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] 推送失败：先确认仓库已创建、且本机已登录正确的 GitHub 账号" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "完成: $url" -ForegroundColor Green
Write-Host "提示：Release 附件请手动上传 SJ-Lens-v*.apk" -ForegroundColor Yellow

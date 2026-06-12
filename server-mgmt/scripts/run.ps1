# 一键检查环境并执行（在 WSL 或已安装 Ansible 的 PowerShell 中运行）
param(
    [ValidateSet("ping", "users", "check")]
    [string]$Action = "users",
    [string]$Limit = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

function Test-Ansible {
    if (-not (Get-Command ansible -ErrorAction SilentlyContinue)) {
        Write-Host @"

未检测到 ansible 命令。请先安装：

  方式一（推荐 WSL Ubuntu）:
    sudo apt update && sudo apt install -y ansible
    cd "$(WSL path '$Root' 2>$null; if (-not $?) { $Root })"
    ansible-galaxy collection install -r requirements.yml

  方式二（pip）:
    pip install ansible
    ansible-galaxy collection install -r requirements.yml

"@ -ForegroundColor Red
        exit 1
    }
}

Test-Ansible

if (-not (Test-Path "collections\ansible_collections\ansible\posix")) {
    Write-Host "正在安装 Ansible 集合 ansible.posix ..." -ForegroundColor Cyan
    ansible-galaxy collection install -r requirements.yml
}

$limitArg = if ($Limit) { @("-l", $Limit) } else { @() }

switch ($Action) {
    "ping" {
        ansible-playbook playbook-ping.yml @limitArg
    }
    "check" {
        ansible-playbook playbook-users.yml --check --diff @limitArg
    }
    "users" {
        ansible-playbook playbook-users.yml @limitArg
    }
}

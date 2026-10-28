# 极简 RCON 客户端：登录 + 发一条命令 + 打印响应。
# 只用于验证 /ctd 命令，不追求健壮。
param(
    [string]$Host_ = '127.0.0.1',
    [int]$Port = 25575,
    [string]$Password = 'ctdtest',
    [string]$Command = '/ctd host status'
)

$ErrorActionPreference = 'Stop'

function Send-Packet {
    param([System.IO.Stream]$Stream, [int]$Id, [int]$Type, [string]$Body)
    $bodyBytes = [Text.Encoding]::UTF8.GetBytes($Body)
    $len = 4 + 4 + $bodyBytes.Length + 2
    $buf = New-Object byte[] ($len + 4)
    [BitConverter]::GetBytes($len).CopyTo($buf, 0)
    [BitConverter]::GetBytes($Id).CopyTo($buf, 4)
    [BitConverter]::GetBytes($Type).CopyTo($buf, 8)
    $bodyBytes.CopyTo($buf, 12)
    $Stream.Write($buf, 0, $buf.Length)
    $Stream.Flush()
}

function Read-Packet {
    param([System.IO.Stream]$Stream)
    $lenBuf = New-Object byte[] 4
    $read = 0
    while ($read -lt 4) {
        $n = $Stream.Read($lenBuf, $read, 4 - $read)
        if ($n -le 0) { return $null }
        $read += $n
    }
    $len = [BitConverter]::ToInt32($lenBuf, 0)
    $buf = New-Object byte[] $len
    $read = 0
    while ($read -lt $len) {
        $n = $Stream.Read($buf, $read, $len - $read)
        if ($n -le 0) { return $null }
        $read += $n
    }
    $id = [BitConverter]::ToInt32($buf, 0)
    $type = [BitConverter]::ToInt32($buf, 4)
    $text = [Text.Encoding]::UTF8.GetString($buf, 8, $len - 8 - 2)
    return [pscustomobject]@{ Id = $id; Type = $type; Body = $text }
}

$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($Host_, $Port)
$stream = $client.GetStream()
$stream.ReadTimeout = 5000

Send-Packet -Stream $stream -Id 1 -Type 3 -Body $Password
$auth = Read-Packet -Stream $stream
if ($auth -eq $null -or $auth.Id -eq -1) { throw "RCON 认证失败" }
Write-Host "[rcon] authenticated"

Send-Packet -Stream $stream -Id 2 -Type 2 -Body $Command
$response = Read-Packet -Stream $stream
Write-Host "[rcon] $Command"
Write-Host "--- response ---"
if ($response) { Write-Host $response.Body } else { Write-Host "(无响应)" }

$client.Close()

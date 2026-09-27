$ErrorActionPreference = 'Stop'
$config = Get-Content 'C:\Server-Game\tsm_dodo\launcher_config.json' -Raw | ConvertFrom-Json
$env:MYSQL_PWD = $config.mysql_password
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'
$dump = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe'
$backupDir = 'C:\Server-Game\tsm_dodo\backups'
New-Item -ItemType Directory -Force $backupDir | Out-Null
$backup = Join-Path $backupDir ('character4-mission-flags-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
& $dump -h $config.mysql_host -P $config.mysql_port -u $config.mysql_user $config.mysql_database character_mission_flags --where='char_id=4' | Set-Content -LiteralPath $backup -Encoding utf8
& $mysql -h $config.mysql_host -P $config.mysql_port -u $config.mysql_user -D $config.mysql_database -e "INSERT INTO character_mission_flags (char_id, bit_index, bit_value) VALUES (4,936,1) ON DUPLICATE KEY UPDATE bit_value=1; SELECT char_id,bit_index,bit_value FROM character_mission_flags WHERE char_id=4 AND bit_index=936;"
Remove-Item Env:MYSQL_PWD
Write-Output "Backup: $backup"


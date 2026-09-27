$root='C:\Server-Game\tsm_dodo'
$c=Get-Content "$root\launcher_config.json" -Raw | ConvertFrom-Json
$env:MYSQL_PWD=[string]$c.mysql_password
& 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe' -h $c.mysql_host -P $c.mysql_port -u $c.mysql_user -D $c.mysql_database --batch --skip-column-names -e 'SELECT mission_id,step FROM character_missions WHERE char_id=7 ORDER BY mission_id;'


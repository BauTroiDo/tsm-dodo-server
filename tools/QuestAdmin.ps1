# QuestAdmin.ps1 - TSOnline quest repair/admin tool
# Run on the game server: powershell -ExecutionPolicy Bypass -File .\tools\QuestAdmin.ps1

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$configPath = Join-Path $root 'launcher_config.json'
$config = Get-Content $configPath -Raw | ConvertFrom-Json
$mysqlExe = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'
if (!(Test-Path $mysqlExe)) { throw "Không tìm thấy mysql.exe: $mysqlExe" }

function Invoke-MySql([string]$Sql) {
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $mysqlExe
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $psi.Environment['MYSQL_PWD'] = [string]$config.mysql_password
    foreach ($part in @('-h', $config.mysql_host, '-P', $config.mysql_port, '-u', $config.mysql_user, '-D', $config.mysql_database, '--batch', '--skip-column-names', '-e', $Sql)) {
        [void]$psi.ArgumentList.Add([string]$part)
    }
    $proc = [System.Diagnostics.Process]::Start($psi)
    $out = $proc.StandardOutput.ReadToEnd()
    $err = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit()
    if ($proc.ExitCode -ne 0) { throw $err.Trim() }
    return $out
}

function Read-U16([System.IO.BinaryReader]$Reader) { return $Reader.ReadUInt16() }
function Read-UnicodeString([System.IO.BinaryReader]$Reader) {
    $length = Read-U16 $Reader
    if ($length -eq 0) { return '' }
    return [Text.Encoding]::Unicode.GetString($Reader.ReadBytes($length))
}

function Get-MarkDefinitions {
    $path = Join-Path $root 'cdn\mirror\tsr\2.4.76\ResourcesPacked\Data\Mark_C.dat'
    $stream = [IO.File]::OpenRead($path)
    $reader = [IO.BinaryReader]::new($stream)
    try {
        $defs = @{}
        $count = $reader.ReadInt32()
        for ($i = 0; $i -lt $count; $i++) {
            $name = Read-UnicodeString $reader
            $kind = $reader.ReadByte()
            $id = $reader.ReadUInt16()
            $bitId = $reader.ReadUInt16()
            $gainWay = $reader.ReadByte()
            $description = Read-UnicodeString $reader
            if ($id -ne 0) { $defs[$id] = [pscustomobject]@{ Name=$name; Description=$description; BitId=$bitId; Kind=$kind } }
        }
        return $defs
    } finally { $reader.Dispose(); $stream.Dispose() }
}

$marks = Get-MarkDefinitions
$form = [Windows.Forms.Form]::new()
$form.Text = 'TSOnline - Quản trị nhiệm vụ'
$form.Size = [Drawing.Size]::new(1120, 690)
$form.StartPosition = 'CenterScreen'
$form.Font = [Drawing.Font]::new('Segoe UI', 10)

$characterLabel = [Windows.Forms.Label]::new()
$characterLabel.Text = 'Nhân vật:'
$characterLabel.Location = [Drawing.Point]::new(18, 20)
$characterLabel.AutoSize = $true
$form.Controls.Add($characterLabel)

$characters = [Windows.Forms.ComboBox]::new()
$characters.Location = [Drawing.Point]::new(95, 16)
$characters.Size = [Drawing.Size]::new(360, 30)
$characters.DropDownStyle = 'DropDownList'
$form.Controls.Add($characters)

$refresh = [Windows.Forms.Button]::new()
$refresh.Text = 'Tải lại'
$refresh.Location = [Drawing.Point]::new(470, 15)
$refresh.Size = [Drawing.Size]::new(100, 32)
$form.Controls.Add($refresh)

$backup = [Windows.Forms.Button]::new()
$backup.Text = 'Sao lưu nhiệm vụ'
$backup.Location = [Drawing.Point]::new(584, 15)
$backup.Size = [Drawing.Size]::new(150, 32)
$form.Controls.Add($backup)

$grid = [Windows.Forms.DataGridView]::new()
$grid.Location = [Drawing.Point]::new(18, 62)
$grid.Size = [Drawing.Size]::new(1065, 440)
$grid.ReadOnly = $true
$grid.AllowUserToAddRows = $false
$grid.AllowUserToDeleteRows = $false
$grid.SelectionMode = 'FullRowSelect'
$grid.AutoSizeColumnsMode = 'Fill'
foreach ($column in @('ID', 'Bước', 'Tên nhiệm vụ', 'Mô tả')) { [void]$grid.Columns.Add($column, $column) }
$grid.Columns[0].FillWeight = 10; $grid.Columns[1].FillWeight = 9; $grid.Columns[2].FillWeight = 28; $grid.Columns[3].FillWeight = 53
$form.Controls.Add($grid)

$idLabel = [Windows.Forms.Label]::new(); $idLabel.Text = 'ID nhiệm vụ:'; $idLabel.Location = [Drawing.Point]::new(18, 525); $idLabel.AutoSize = $true; $form.Controls.Add($idLabel)
$missionId = [Windows.Forms.NumericUpDown]::new(); $missionId.Location = [Drawing.Point]::new(112, 520); $missionId.Maximum = 65535; $missionId.Size = [Drawing.Size]::new(115, 30); $form.Controls.Add($missionId)
$stepLabel = [Windows.Forms.Label]::new(); $stepLabel.Text = 'Bước:'; $stepLabel.Location = [Drawing.Point]::new(245, 525); $stepLabel.AutoSize = $true; $form.Controls.Add($stepLabel)
$step = [Windows.Forms.NumericUpDown]::new(); $step.Location = [Drawing.Point]::new(300, 520); $step.Maximum = 999; $step.Minimum = 1; $step.Value = 1; $step.Size = [Drawing.Size]::new(90, 30); $form.Controls.Add($step)

$save = [Windows.Forms.Button]::new(); $save.Text = 'Thêm / cập nhật'; $save.Location = [Drawing.Point]::new(412, 518); $save.Size = [Drawing.Size]::new(145, 34); $form.Controls.Add($save)
$remove = [Windows.Forms.Button]::new(); $remove.Text = 'Xóa nhiệm vụ'; $remove.Location = [Drawing.Point]::new(570, 518); $remove.Size = [Drawing.Size]::new(130, 34); $form.Controls.Add($remove)
$hint = [Windows.Forms.Label]::new(); $hint.Location = [Drawing.Point]::new(18, 568); $hint.Size = [Drawing.Size]::new(1060, 60); $hint.ForeColor = [Drawing.Color]::FromArgb(50, 80, 120); $hint.Text = 'Chỉ thêm/chỉnh các ID có trong Mark_C.dat. Công cụ sao lưu trước mỗi lần ghi. Chọn một dòng để nạp ID và bước vào biểu mẫu.'; $form.Controls.Add($hint)
$status = [Windows.Forms.Label]::new(); $status.Location = [Drawing.Point]::new(18, 635); $status.Size = [Drawing.Size]::new(1060, 25); $form.Controls.Add($status)

function Get-SelectedCharacterId {
    if ($null -eq $characters.SelectedItem) { throw 'Hãy chọn nhân vật.' }
    return [int]$characters.SelectedItem.Id
}
function Backup-Character([int]$CharId) {
    $dir = Join-Path $root 'backups\quest-admin'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $rows = Invoke-MySql "SELECT mission_id,step FROM character_missions WHERE char_id=$CharId ORDER BY mission_id;"
    $path = Join-Path $dir ("character_{0}_{1:yyyyMMdd_HHmmss}.sql" -f $CharId, (Get-Date))
    $content = @("-- QuestAdmin backup for character $CharId", "DELETE FROM character_missions WHERE char_id=$CharId;")
    foreach ($row in $rows -split "`r?`n") {
        if ($row -match '^(\d+)\t(\d+)$') { $content += "INSERT INTO character_missions (char_id, mission_id, step) VALUES ($CharId, $($Matches[1]), $($Matches[2]));" }
    }
    Set-Content -Path $path -Value $content -Encoding utf8
    return $path
}
function Load-Missions {
    $grid.Rows.Clear()
    $id = Get-SelectedCharacterId
    $rows = Invoke-MySql "SELECT mission_id,step FROM character_missions WHERE char_id=$id ORDER BY mission_id;"
    foreach ($row in $rows -split "`r?`n") {
        if ($row -match '^(\d+)\t(\d+)$') {
            $questId = [int]$Matches[1]; $questStep = [int]$Matches[2]; $def = $marks[$questId]
            $name = if ($null -ne $def) { $def.Name } else { '(ID chưa có trong dữ liệu client)' }
            $desc = if ($null -ne $def) { $def.Description } else { '' }
            [void]$grid.Rows.Add($questId, $questStep, $name, $desc)
        }
    }
    $status.Text = "Đã tải nhiệm vụ của nhân vật ID $id."
}

try {
    $raw = Invoke-MySql 'SELECT id,name,level,map_id FROM characters ORDER BY id;'
    foreach ($line in $raw -split "`r?`n") {
        if ($line -match '^(\d+)\t(.*?)\t(\d+)\t(\d+)$') { [void]$characters.Items.Add([pscustomobject]@{ Id=[int]$Matches[1]; Display="[$($Matches[1])] $($Matches[2]) - Lv.$($Matches[3]) - map $($Matches[4])" }) }
    }
    $characters.DisplayMember = 'Display'
    if ($characters.Items.Count -gt 0) { $characters.SelectedIndex = 0; Load-Missions }
} catch { [Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Không thể kết nối dữ liệu'); exit }

$refresh.Add_Click({ try { Load-Missions } catch { [Windows.Forms.MessageBox]::Show($_.Exception.Message) } })
$characters.Add_SelectedIndexChanged({ try { Load-Missions } catch {} })
$grid.Add_SelectionChanged({ if ($grid.SelectedRows.Count -gt 0) { $missionId.Value = [decimal]$grid.SelectedRows[0].Cells[0].Value; $step.Value = [decimal]$grid.SelectedRows[0].Cells[1].Value } })
$backup.Add_Click({ try { $path = Backup-Character (Get-SelectedCharacterId); $status.Text = "Đã sao lưu: $path" } catch { [Windows.Forms.MessageBox]::Show($_.Exception.Message) } })
$save.Add_Click({
    try {
        $id = [int]$missionId.Value; $value = [int]$step.Value; $charId = Get-SelectedCharacterId
        if (!$marks.ContainsKey($id)) { throw "ID $id không tồn tại trong dữ liệu nhiệm vụ của client." }
        [void](Backup-Character $charId)
        Invoke-MySql "INSERT INTO character_missions (char_id,mission_id,step) VALUES ($charId,$id,$value) ON DUPLICATE KEY UPDATE step=VALUES(step);" | Out-Null
        Load-Missions; $status.Text = "Đã lưu $id ($($marks[$id].Name)) ở bước $value."
    } catch { [Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Không thể lưu') }
})
$remove.Add_Click({
    try {
        $id = [int]$missionId.Value; $charId = Get-SelectedCharacterId
        if ([Windows.Forms.MessageBox]::Show("Xóa nhiệm vụ ID $id khỏi nhân vật đang chọn?", 'Xác nhận', 'YesNo', 'Warning') -ne 'Yes') { return }
        [void](Backup-Character $charId)
        Invoke-MySql "DELETE FROM character_missions WHERE char_id=$charId AND mission_id=$id;" | Out-Null
        Load-Missions; $status.Text = "Đã xóa nhiệm vụ $id."
    } catch { [Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Không thể xóa') }
})

[void]$form.ShowDialog()

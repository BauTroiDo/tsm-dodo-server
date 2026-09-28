BattleRoyaleHotkeyData= {}
BattleRoyaleHotkeyData.__index = BattleRoyaleHotkeyData;
local this = BattleRoyaleHotkeyData;

function BattleRoyaleHotkeyData.New(reader)
  local self = {};
  setmetatable(self, BattleRoyaleHotkeyData);
  local id = reader:ReadUInt16();
  local type = reader:ReadByte();
  local uiIndex = reader:ReadByte();
  if type == 1 and uiIndex == 1 then
    self.hotkeyType = EHotkeyType.SceneBattleMain;
  elseif type == 1 and uiIndex == 2 then
    self.hotkeyType = EHotkeyType.SceneBattleSub;
  elseif type == 2 and uiIndex == 1 then
    self.hotkeyType = EHotkeyType.PlayerMain;
  elseif type == 2 and uiIndex == 2 then
    self.hotkeyType = EHotkeyType.PlayerSub;
  elseif type == 3 and uiIndex == 1 then
    self.hotkeyType = EHotkeyType.Equip;
  end
  self.useId = reader:ReadUInt16();
  return self.hotkeyType, self.useId
end
BattleRoyaleSealData= {}
BattleRoyaleSealData.__index = BattleRoyaleSealData;
local this = BattleRoyaleSealData;

function BattleRoyaleSealData.New(reader)
  local self = {};
  setmetatable(self, BattleRoyaleSealData);

  self.id = reader:ReadUInt16();
  self.name = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));
  self.quality = reader:ReadByte();
  self.description = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));

  return self.id, self;
end
PlayerBonusData = {}
PlayerBonusData.__index = PlayerBonusData;

function PlayerBonusData.New(reader)
  local self = {};
  setmetatable(self, PlayerBonusData);

  self.serialNumber = reader:ReadUInt16();
  self.bonusSource = reader:ReadUInt16();
  self.idInSource = reader:ReadUInt32();
  self.conditionType = reader:ReadByte();
  self.conditionId = reader:ReadInt32();
  self.operator = reader:ReadByte();
  self.conditionValue = reader:ReadUInt32();
  self.bonusType = reader:ReadByte();
  self.bonusAmount = reader:ReadUInt16();

  return self.bonusSource, self.idInSource, self;
end

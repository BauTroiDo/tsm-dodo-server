TreasureGroupLVData = {}
TreasureGroupLVData.__index = TreasureGroupLVData;

function TreasureGroupLVData.New(reader)
  local self = {};
  setmetatable(self, TreasureGroupLVData);

  self.id = reader:ReadUInt16();
  self.level = reader:ReadByte();
  self.environmentId = reader:ReadUInt16();
  self.characterId = reader:ReadUInt16();
  self.att = reader:ReadUInt16();
  self.value = reader:ReadInt32();

  return self;
end
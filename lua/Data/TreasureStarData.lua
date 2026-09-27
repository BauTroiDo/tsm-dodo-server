TreasureStarData = {}
TreasureStarData.__index = TreasureStarData;

function TreasureStarData.New(reader)
  local self = {};
  setmetatable(self, TreasureStarData);

  self.id = reader:ReadUInt16();
  self.star = reader:ReadByte();
  self.environmentId = reader:ReadUInt16();
  self.characterId = reader:ReadUInt16();
  self.att = reader:ReadUInt16();
  self.value = reader:ReadInt32();
  self.itemCount = reader:ReadInt32();
  self.moneyCount = reader:ReadInt32();

  return self;
end
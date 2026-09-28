TreasureLvData = {}
TreasureLvData.__index = TreasureLvData;

function TreasureLvData.New(reader)
  local self = {};
  setmetatable(self, TreasureLvData);

  self.id = reader:ReadUInt16();
  self.level = reader:ReadByte();
  self.environmentId = reader:ReadUInt16();
  self.characterId = reader:ReadUInt16();
  self.att = reader:ReadUInt16();
  self.value = reader:ReadInt32();
  self.count = reader:ReadByte();
  self.itemCount = reader:ReadInt32();
  self.moneyCount = reader:ReadInt32();

  return self;
end
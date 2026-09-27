FurnitureCollectLevelData = {}
FurnitureCollectLevelData.__index = FurnitureCollectLevelData;

function FurnitureCollectLevelData.New(reader)
  local self = {};
  setmetatable(self, FurnitureCollectLevelData);

  self.level = reader:ReadByte();
  self.score = reader:ReadUInt16();
  self.kind = reader:ReadByte();
  self.value = reader:ReadUInt16();

  return  self.level, self;
end

function FurnitureCollectLevelData:GetString()
  return string.Concat(ItemData.GetAttrText(self.kind), "+", self.value);
end
TreasureActivityCriteriaData = {}
TreasureActivityCriteriaData.__index = TreasureActivityCriteriaData;

function TreasureActivityCriteriaData.New(reader)
  local self = {};
  setmetatable(self, TreasureActivityCriteriaData);

  self.id = reader:ReadUInt16();
  self.string = reader:ReadUInt32();
  self.type = reader:ReadByte();
  self.number = reader:ReadUInt16();

  return self;
end
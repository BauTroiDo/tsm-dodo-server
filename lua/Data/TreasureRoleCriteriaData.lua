TreasureRoleCriteriaData = {}
TreasureRoleCriteriaData.__index = TreasureRoleCriteriaData;

function TreasureRoleCriteriaData.New(reader)
  local self = {};
  setmetatable(self, TreasureRoleCriteriaData);

  self.id = reader:ReadUInt16();
  self.role = reader:ReadByte();
  self.element = reader:ReadByte();
  self.turn = reader:ReadByte();
  self.country = reader:ReadByte();
  self.collectBookId = reader:ReadUInt16();

  return self;
end
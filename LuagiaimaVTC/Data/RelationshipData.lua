RelationshipData = {}
RelationshipData.__index = RelationshipData;

function RelationshipData.New(reader)
  local self = {};
  setmetatable(self, RelationshipData);

  self.id = reader:ReadUInt16();
  self.sortNumber = reader:ReadUInt16();
  self.mainWarriorId = reader:ReadUInt16();

  self.relatedWarriorIds = {}
  for i = 1, 9 do
    local warriorId = reader:ReadUInt16();
    if warriorId ~= 0 then
      self.relatedWarriorIds[i] = warriorId;
    end
  end

  self.fiftyPercentPrizeFlag = reader:ReadUInt16();
  self.fiftyPercentPrizeId = reader:ReadUInt16();
  self.fiftyPercentPrizeAmount = reader:ReadInt32();

  self.hundredPercentPrizeFlag = reader:ReadUInt16();
  self.hundredPercentPrizeId = reader:ReadUInt16();
  self.hundredPercentPrizeAmount = reader:ReadInt32();

  return self.id, self;
end
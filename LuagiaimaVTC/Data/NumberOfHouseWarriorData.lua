NumberOfHouseWarriorData = {}
NumberOfHouseWarriorData.__index = NumberOfHouseWarriorData;

function NumberOfHouseWarriorData.New(reader)
  local self = {};
  setmetatable(self, NumberOfHouseWarriorData);

  self.number = reader:ReadByte();
  self.score = reader:ReadUInt16();

  return  self.number, self;
end

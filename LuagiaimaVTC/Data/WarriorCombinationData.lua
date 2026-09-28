WarriorCombinationData = {}
WarriorCombinationData.__index = WarriorCombinationData;

function WarriorCombinationData.New(reader)
  local self = {};

  self.index = reader:ReadByte();

  self.warriors = {};
  for i = 1, 4 do
    self.warriors[i] = reader:ReadUInt16();
  end

  self.values = {};

  self.values.kind = reader:ReadByte();
  self.values.value = reader:ReadUInt16();

  return  self;
end
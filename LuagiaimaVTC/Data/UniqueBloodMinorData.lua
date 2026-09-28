UniqueBloodMinorData = {}
UniqueBloodMinorData.__index = UniqueBloodMinorData;

function UniqueBloodMinorData.New(reader)
  local self = {};
  setmetatable(self, UniqueBloodMinorData);
  self.id = reader:ReadUInt16();
  self.group = reader:ReadByte();
  self.probability = reader:ReadUInt16();
  self.element = reader:ReadByte();
  self.value = reader:ReadUInt16();

  return self.id, self;
end
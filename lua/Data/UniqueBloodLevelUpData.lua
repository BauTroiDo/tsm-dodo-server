UniqueBloodLevelUpData = {}
UniqueBloodLevelUpData.__index = UniqueBloodLevelUpData;

function UniqueBloodLevelUpData.New(reader)
  local self = {};
  setmetatable(self, UniqueBloodLevelUpData);
  self.group = reader:ReadByte();
  self.lv = reader:ReadByte();
  self.piece = reader:ReadUInt32();
  self.element = reader:ReadByte();
  self.value = reader:ReadUInt16();

  return self;
end
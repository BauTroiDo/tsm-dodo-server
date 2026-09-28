UniqueBloodUnlimitedData = {}
UniqueBloodUnlimitedData.__index = UniqueBloodUnlimitedData;

function UniqueBloodUnlimitedData.New(reader)
  local self = {};
  setmetatable(self, UniqueBloodUnlimitedData);
  self.time = reader:ReadByte();
  self.lv = reader:ReadByte();

  return self.time, self;
end
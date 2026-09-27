VIPRewardData = {};
VIPRewardData.__index = VIPRewardData;

function VIPRewardData.New(reader)
  local self = {};
  setmetatable(self, VIPRewardData);
  
  self.lv = reader:ReadByte();
  self.info = reader:ReadUInt32();
  self.itemId = reader:ReadUInt16();
  self.count = reader:ReadByte();
  self.flag = reader:ReadUInt16();

  return self.lv, self;
end
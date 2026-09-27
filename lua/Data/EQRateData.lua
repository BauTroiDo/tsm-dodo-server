EQRateData = {};
EQRateData.__index = EQRateData;

function EQRateData.New(reader)
  local self = {};
  setmetatable(self, EQRateData);

  local level = reader:ReadByte(); --升階等級
  self.rate = reader:ReadUInt16(); --升階成功機率
  self.isNeedEquip = reader:ReadByte(); --是否掉階(0：不掉階 1：掉階)
  self.itemId = reader:ReadUInt16(); --物品編號
  self.count = reader:ReadUInt16(); --物品數量
  self.required = reader:ReadUInt16(); --所需金幣

  return level, self;
end
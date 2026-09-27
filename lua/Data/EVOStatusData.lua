EVOStatusData = {}
EVOStatusData.__index = EVOStatusData;

function EVOStatusData.New(reader)
  local self = {};
  setmetatable(self, EVOStatusData);
  
  local id = reader:ReadByte();

  self.kind = reader:ReadByte(); --狀態種類
  self.value = reader:ReadByte(); --增加數值
  self.itemId = reader:ReadUInt16(); --圖示ID
  self.description = reader:ReadString(254, false); --說明

  return id, self;
end

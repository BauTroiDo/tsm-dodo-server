StrongholdWarData = {}
StrongholdWarData.__index = StrongholdWarData;

function StrongholdWarData.New(reader)
  local self = {};
  setmetatable(self, StrongholdWarData);

  self.id = reader:ReadByte(); --編號
  self.name = reader:ReadUInt16(); --名稱
  self.description = reader:ReadUInt16(); --說明
  self.drumCount = reader:ReadByte(); --數量
  self.time = System.TimeSpan.FromMinutes(reader:ReadUInt16()); --時間
  self.orderType = reader:ReadByte(); --特殊效果
  self.sellId = reader:ReadUInt16(); --介消
  self.changeSellId = reader:ReadUInt16(); --介消

  return self.id, self;
end



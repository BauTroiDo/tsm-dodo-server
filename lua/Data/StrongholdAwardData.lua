StrongholdAwardData = {}
StrongholdAwardData.__index = StrongholdAwardData;

function StrongholdAwardData.New(reader)
  local self = {};
  setmetatable(self, StrongholdAwardData);

  self.condition = reader:ReadByte(); --條件
  self.value = reader:ReadUInt16(); --條件數值
  self.award = reader:ReadUInt16(); --獎勵
  self.quant = reader:ReadUInt16(); --數量

  return self;
end

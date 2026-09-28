StoreValueData = {};
StoreValueData.__index = StoreValueData;

function StoreValueData.New(reader)
  local self = {};
  setmetatable(self, StoreValueData);
  
  local kind = reader:ReadByte(); -- 支付種類
  self.index = reader:ReadByte(); --IAPData索引
  self.cashIndex = reader:ReadUInt16(); --給cash的索引
  self.price = reader:ReadUInt32(); -- 儲值金額
  self.totalGold = reader:ReadUInt16(); --元寶 + 額外元寶
  self.gold = reader:ReadUInt16(); --元寶
  self.extraGold = reader:ReadUInt16(); --額外元寶
  self.flag = reader:ReadUInt16(); --首儲標記
  self.textureId = reader:ReadByte(); --圖片編號
  self.showText = reader:ReadUInt32(); -- 顯示字串編號
  self.showPrice = reader:ReadUInt32(); -- 顯示儲值金額
  self.bonusTarget = reader:ReadByte();  --分紅對象

  return  kind, self;
end
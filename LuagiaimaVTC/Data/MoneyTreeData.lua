MoneyTreeData = {};
MoneyTreeData.__index = MoneyTreeData;

function MoneyTreeData.New(reader)
  local self = {};
  setmetatable(self, MoneyTreeData);

  local lv = reader:ReadByte();
  self.gold = reader:ReadUInt16();     --需求元寶
  self.money = reader:ReadUInt32();    --加速消耗金幣價
  self.cdTime = reader:ReadUInt16();   --CD時間(分)

  return lv, self;
end
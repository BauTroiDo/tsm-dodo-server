BreakthroughData = {}
BreakthroughData.__index = BreakthroughData;

local maxBreakthroughEXP = 0;

function BreakthroughData.New(reader)
  local self = {};
  setmetatable(self, BreakthroughData);
  self.itemID = reader:ReadUInt16(); --物品ID
  self.itemCount = reader:ReadByte(); --物品數量
  self.exp = reader:ReadInt32() --所需經驗
  maxBreakthroughEXP = maxBreakthroughEXP + self.exp;
  return self;
end

function BreakthroughData:IsEmpty()
  if self.itemID == 0 or self.itemCount == 0 or self.exp == 0 then
    return true
  else
    return false
  end
end

function BreakthroughData.GetMaxEXP()
  return maxBreakthroughEXP;
end
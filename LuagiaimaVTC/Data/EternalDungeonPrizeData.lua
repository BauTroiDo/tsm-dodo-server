EternalDungeonPrizeData = {}
EternalDungeonPrizeData.__index = EternalDungeonPrizeData;

local this = EternalDungeonPrizeData;
local eternalDungeonPrizeGroup = {};
local lastIdx = 1;

function EternalDungeonPrizeData.New(reader)
  local self = {};
  setmetatable(self, EternalDungeonPrizeData);
  
  self.id = reader:ReadInt32(); 
  
  self.prize = {};
  for i = 1, 5 do
    self.prize[i] = {}
    self.prize[i].itemId = reader:ReadUInt16();--物品Id
    self.prize[i].count = reader:ReadByte(); --物品數量
  end

  for i = lastIdx, self.id do
    eternalDungeonPrizeGroup[i] = lastIdx;
  end
  lastIdx = self.id;
  eternalDungeonPrizeGroup[lastIdx] = lastIdx;

  return self.id, self;
end 

function EternalDungeonPrizeData.GetIdx(idx)
  if eternalDungeonPrizeGroup[idx] ~= nil then
    return eternalDungeonPrizeGroup[idx];
  else
    return eternalDungeonPrizeGroup[lastIdx];
  end;
end
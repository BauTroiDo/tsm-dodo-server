
ArmyPokeData = {}
ArmyPokeData.__index = ArmyPokeData;

function ArmyPokeData.New(reader)
  local self = {};
  setmetatable(self, ArmyPokeData);
  
  self.index = reader:ReadByte();     --流水號
  self.awardKind = reader:ReadByte(); --獎項分類
  self.kind = reader:ReadByte();      --種類
  self.itemId = reader:ReadUInt16();      --編號
  self.count = reader:ReadUInt16();   --數量
  
  return self.index, self;
end

function ArmyPokeData.GetData()
  local temp = {};
  local temp1 = {};

  for _, v in pairs(armyPokeDatas) do
    if v.awardKind == 1 then
      table.insert(temp, v);
    elseif v.awardKind == 2 then
      table.insert(temp1, v);
    end
  end

  for _, v in pairs(temp) do
    table.insert(temp1, v);
  end

  return temp1;
end
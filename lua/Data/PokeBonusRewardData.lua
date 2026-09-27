PokeBonusRewardData = {}
PokeBonusRewardData.__index = PokeBonusRewardData;

function PokeBonusRewardData.New(reader)
  local self = {};
  setmetatable(self, PokeBonusRewardData);
  
  self.index = reader:ReadByte();     --盤面編號
  self.id = reader:ReadByte(); --格子ID
  self.quantPlus = reader:ReadByte();      --加倍種類

  return self;
end

function PokeBonusRewardData.GetData()
  local temp = {};

  for _, v in pairs(pokeBonusRewardDatas) do
    if temp[v.index] == nil then
      temp[v.index] = {};
      temp[v.index].id = {};
      temp[v.index].quantPlus = {};
    end

    table.insert(temp[v.index].id, v.id);
    temp[v.index].quantPlus[v.id] = v.quantPlus;
  end

  return temp;
end

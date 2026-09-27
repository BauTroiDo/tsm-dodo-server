EQValueData = {};
EQValueData.__index = EQValueData;

function EQValueData.New(reader)
  local self = {};
  setmetatable(self, EQValueData);

  self.index = reader:ReadByte(); --索引編號
  self.attribute = reader:ReadUInt16(); --強化屬性 (207:提昇總HP, 208:提昇總SP, 210:普通攻擊力, 211: 普通防禦力, 212: 知力,  214敏捷力 , 218:體質 ,  219:能量)

  self.level = {};
  for i = 1, 15 do
    self.level[i] = reader:ReadUInt16(); --洗鍊階級1(0不加入洗鍊值)
  end

  return self;
end
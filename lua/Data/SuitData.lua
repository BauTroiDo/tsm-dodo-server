SuitData = {}
SuitData.__index = SuitData;

function SuitData.New(reader)
  local self = {};
  setmetatable(self, SuitData);

  self.id = reader:ReadUInt16(); --套裝ID
  self.name = reader:ReadUInt32(); --套裝名稱
  self.maxSum = reader:ReadByte(); --套裝最大數
  self.attributes = {};
  for i = 1, Item.MaxSuitAttr do
    self.attributes[i] = {};
    self.attributes[i].suitSum = reader:ReadByte(); --套裝數
    self.attributes[i].attribute = reader:ReadByte(); --屬性
    self.attributes[i].value = reader:ReadUInt16(); --值
  end

  if Define.TextCount then
    DataManager.textCount = DataManager.textCount + string.Count(self.name);
  end
  
  return self.id, self;
end
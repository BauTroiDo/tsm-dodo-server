LoginAwardData = {};
LoginAwardData.__index = LoginAwardData;

function LoginAwardData.New(reader)
  local self = {};
  setmetatable(self, LoginAwardData);

  self.group = reader:ReadByte(); --群組
  self.changeKind = reader:ReadByte(); --兌換方式
  self.day = reader:ReadUInt32(); --天數

  self.itemId = {};
  self.quant = {};

  for i = 1, 5 do
    self.itemId[i] = reader:ReadUInt16();
    self.quant[i] = reader:ReadInt32();
  end

  self.changeItemId = reader:ReadUInt16();
  self.changeQuant = reader:ReadInt32();
  self.roleCount = reader:ReadUInt16();
  self.flag = reader:ReadUInt16();

  return self;
end

function LoginAwardData.Add(group, day, items, quants, changeItemId, changeQuant, roleCount, flag, actRule)
  local self = {};
  setmetatable(self, LoginAwardData);

  self.group = group; --群組
  self.day = day; --天數
  self.itemId = items;
  self.quant = quants;
  self.changeItemId = changeItemId;
  self.changeQuant = changeQuant;
  self.roleCount = roleCount;
  self.flag = flag;
  self.actRule = actRule;

  return self;
end
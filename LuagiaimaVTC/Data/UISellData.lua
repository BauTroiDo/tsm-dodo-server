UISellData = {};
UISellData.__index = UISellData;

function UISellData.New(reader)
  local self = {};
  setmetatable(self, UISellData);

  self.id = reader:ReadUInt16();
  self.rolecountId = reader:ReadUInt16();
  self.moneyString = reader:ReadUInt32();
  self.itemString = reader:ReadUInt32();
  self.customString = reader:ReadUInt32();

  return self;
end
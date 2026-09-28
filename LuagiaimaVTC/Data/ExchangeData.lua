ExchangeData = {};
ExchangeData.__index = ExchangeData;

function ExchangeData.New(reader)
  local self = {};
  setmetatable(self, ExchangeData);

  local id = reader:ReadUInt16();
  self.itemId = reader:ReadUInt16();
  self.count = reader:ReadUInt16();

  return id, self;
end
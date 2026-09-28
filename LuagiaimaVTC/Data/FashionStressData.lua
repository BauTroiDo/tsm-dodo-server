FashionStressData = {}
FashionStressData.__index = FashionStressData;

function FashionStressData.New(reader)
  local self = {};
  setmetatable(self, FashionStressData);

  local id = reader:ReadUInt16();
  self.maxLv = reader:ReadByte();

  self.attributes = {};
  for i = 1, 2 do
    self.attributes[i] = {};
    self.attributes[i].attr = reader:ReadUInt16();
    self.attributes[i].value = reader:ReadByte();
    self.attributes[i].bonus = reader:ReadByte();
  end

  return id, self;
end
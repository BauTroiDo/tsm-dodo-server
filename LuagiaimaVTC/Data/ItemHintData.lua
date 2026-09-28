ItemHintData = {};
ItemHintData.__index = ItemHintData;

function ItemHintData.New(reader)
  local self = {};
  setmetatable(self, ItemHintData);

  self.id = reader:ReadByte();
  self.string = reader:ReadUInt32();

  return self;
end
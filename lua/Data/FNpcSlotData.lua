FNpcSlotData = {};
FNpcSlotData.__index = FNpcSlotData;

function FNpcSlotData.New(reader)
  local self = {};
  setmetatable(self, FNpcSlotData);

  local index = reader:ReadUInt16();
  self.exp = reader:ReadUInt32();

  self.icons = {};
  for i = 1, 3 do
    self.icons[i] = {};
    self.icons[i] = reader:ReadByte();
  end

  return index, self;
end
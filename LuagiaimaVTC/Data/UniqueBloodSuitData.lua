UniqueBloodSuitData = {}
UniqueBloodSuitData.__index = UniqueBloodSuitData;

function UniqueBloodSuitData.New(reader)
  local self = {};
  setmetatable(self, UniqueBloodSuitData);
  self.id = reader:ReadUInt16();
  self.stringId = reader:ReadUInt32();
  self.pieces = {};
  self.att = {};
  self.value = {};
  for i = 1, 3 do
    self.pieces[i] = reader:ReadByte();
    self.att[i] = reader:ReadUInt16();
    self.value[i] =reader:ReadUInt16();
  end

  self.icon = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));
  self.red = reader:ReadByte();
  self.green = reader:ReadByte();
  self.blue = reader:ReadByte();

  return self.id, self;
end

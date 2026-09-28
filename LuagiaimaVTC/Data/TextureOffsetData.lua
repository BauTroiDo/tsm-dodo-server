TextureOffsetData ={}
TextureOffsetData.__index = TextureOffsetData

function TextureOffsetData.New(reader)
  local self = {};
  setmetatable(self, TextureOffsetData);

  local length = reader:ReadByte();
  reader:Seek(reader.position - 1);
  self.textureName = reader:ReadString(length, false);
  self.offsetNum = reader:ReadInt32();
  self.offsets = {};
  for i = 1, self.offsetNum do
    self.offsets[i] = {};
    self.offsets[i].serial = reader:ReadInt32();
    self.offsets[i].x = reader:ReadInt32();
    self.offsets[i].y = reader:ReadInt32();
  end
  
  return self.textureName, self;
end

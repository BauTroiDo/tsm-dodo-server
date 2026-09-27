TextureSizeData ={}
TextureSizeData.__index = TextureSizeData

function TextureSizeData.New(reader)
  local self = {};
  setmetatable(self, TextureSizeData);

  local length = reader:ReadByte();
  reader:Seek(reader.position - 1);
  self.textureName = reader:ReadString(length, false);
  self.width = reader:ReadInt32();
  self.height = reader:ReadInt32();
  
  return self.textureName, self;
end
